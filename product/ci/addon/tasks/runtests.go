// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/addon/testintelligence"
	"github.com/harness/harness-core/product/ci/addon/testintelligence/csharp"
	"github.com/harness/harness-core/product/ci/addon/testintelligence/java"
	"github.com/harness/harness-core/product/ci/common/external"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
)

const (
	defaultRunTestsTimeout int64 = 14400 // 4 hour
	defaultRunTestsRetries int32 = 1
	outDir                       = "ti/callgraph/"    // path passed as outDir in the config.ini file
	cgDir                        = "ti/callgraph/cg/" // path where callgraph files will be generated
	javaAgentArg                 = "-javaagent:/addon/bin/java-agent.jar=%s"
	tiConfigPath                 = ".ticonfig.yaml"
)

var (
	selectTestsFn        = selectTests
	collectCgFn          = collectCg
	collectTestReportsFn = collectTestReports
	runCmdFn             = runCmd
	isManualFn           = external.IsManualExecution
	installAgentFn       = installAgents
	getWorkspace         = external.GetWrkspcPath
)

// RunTestsTask represents an interface to run tests intelligently
type RunTestsTask interface {
	Run(ctx context.Context) (int32, error)
}

type runTestsTask struct {
	id          string
	fs          filesystem.FileSystem
	displayName string
	reports     []*pb.Report
	// List of files which have been modified in the PR. This is marshalled form of types.File{}
	// This is done to avoid redefining the structs in code as well as proto.
	// Calls via lite engine use json encoded structs and can be decoded
	// on the TI service.
	diffFiles            string // JSON encoded string of a types.File{} object
	timeoutSecs          int64
	numRetries           int32
	tmpFilePath          string
	preCommand           string // command to run before the actual tests
	postCommand          string // command to run after the test execution
	args                 string // custom flags to run the tests
	language             string // language of codebase
	buildTool            string // buildTool used for codebase
	packages             string // Packages ti will generate callgraph for
	namespaces           string // Namespaces TI will generate callgraph for, similar to package
	annotations          string // Annotations to identify tests for instrumentation
	buildEnvironment     string // Dotnet build environment
	frameworkVersion     string // Dotnet framework version
	runOnlySelectedTests bool   // Flag to be used for disabling testIntelligence and running all tests
	envVarOutputs        []string
	environment          map[string]string
	logMetrics           bool
	log                  *zap.SugaredLogger
	addonLogger          *zap.SugaredLogger
	procWriter           io.Writer
	cmdContextFactory    exec.CmdContextFactory
}

func NewRunTestsTask(step *pb.UnitStep, tmpFilePath string, log *zap.SugaredLogger,
	w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) *runTestsTask {
	r := step.GetRunTests()
	fs := filesystem.NewOSFileSystem(log)
	timeoutSecs := r.GetContext().GetExecutionTimeoutSecs()
	if timeoutSecs == 0 {
		timeoutSecs = defaultRunTestsTimeout
	}

	numRetries := r.GetContext().GetNumRetries()
	if numRetries == 0 {
		numRetries = defaultRunTestsRetries
	}
	return &runTestsTask{
		id:                   step.GetId(),
		fs:                   fs,
		displayName:          step.GetDisplayName(),
		timeoutSecs:          timeoutSecs,
		diffFiles:            r.GetDiffFiles(),
		tmpFilePath:          tmpFilePath,
		numRetries:           numRetries,
		reports:              r.GetReports(),
		preCommand:           r.GetPreTestCommand(),
		postCommand:          r.GetPostTestCommand(),
		args:                 r.GetArgs(),
		language:             r.GetLanguage(),
		buildTool:            r.GetBuildTool(),
		packages:             r.GetPackages(),
		namespaces:           r.GetNamespaces(),
		annotations:          r.GetTestAnnotations(),
		runOnlySelectedTests: r.GetRunOnlySelectedTests(),
		envVarOutputs:        r.GetEnvVarOutputs(),
		environment:          r.GetEnvironment(),
		buildEnvironment:     r.GetBuildEnvironment(),
		frameworkVersion:     r.GetFrameworkVersion(),
		cmdContextFactory:    exec.OsCommandContextGracefulWithLog(log),
		logMetrics:           logMetrics,
		log:                  log,
		procWriter:           w,
		addonLogger:          addonLogger,
	}
}

// Execute commands with timeout and retry handling
func (r *runTestsTask) Run(ctx context.Context) (map[string]string, int32, error) {
	var err, errCg error
	var o map[string]string
	cgDir := filepath.Join(r.tmpFilePath, cgDir)
	testSt := time.Now()
	for i := int32(1); i <= r.numRetries; i++ {
		if o, err = r.execute(ctx, i); err == nil {
			cgSt := time.Now()
			// even if the collectCg fails, try to collect reports. Both are parallel features and one should
			// work even if the other one fails
			errCg = collectCgFn(ctx, r.id, cgDir, time.Since(testSt).Milliseconds(), r.log, cgSt)
			cgTime := time.Since(cgSt)
			repoSt := time.Now()
			err = collectTestReportsFn(ctx, r.reports, r.id, r.log)
			repoTime := time.Since(repoSt)
			if errCg != nil {
				// If there's an error in collecting callgraph, we won't retry but
				// the step will be marked as an error
				r.log.Errorw(fmt.Sprintf("unable to collect callgraph. Time taken: %s", cgTime), zap.Error(errCg))
				if err != nil {
					r.log.Errorw(fmt.Sprintf("unable to collect tests reports. Time taken: %s", repoTime), zap.Error(err))
				}
				return nil, r.numRetries, errCg
			}
			if err != nil {
				// If there's an error in collecting reports, we won't retry but
				// the step will be marked as an error
				r.log.Errorw(fmt.Sprintf("unable to collect test reports. Time taken: %s", repoTime), zap.Error(err))
				return nil, r.numRetries, err
			}
			if len(r.reports) > 0 {
				r.log.Infow(fmt.Sprintf("successfully collected test reports in %s time", repoTime))
			}
			return o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect callgraph and reports, ignore any errors during collection steps itself
		errCg = collectCgFn(ctx, r.id, cgDir, time.Since(testSt).Milliseconds(), r.log, time.Now())
		errc := collectTestReportsFn(ctx, r.reports, r.id, r.log)
		if errc != nil {
			r.log.Errorw("error while collecting test reports", zap.Error(errc))
		}
		if errCg != nil {
			r.log.Errorw("error while collecting callgraph", zap.Error(errCg))
		}
		return nil, r.numRetries, err
	}
	return nil, r.numRetries, err
}

// createJavaAgentArg creates the ini file which is required as input to the java agent
// and returns back the path to the file.
func (r *runTestsTask) createJavaAgentConfigFile(runner testintelligence.TestRunner) (string, error) {
	// Create config file
	dir := filepath.Join(r.tmpFilePath, outDir) + "/"
	err := r.fs.MkdirAll(dir, os.ModePerm)
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not create nested directory %s", dir), zap.Error(err))
		return "", err
	}
	if r.packages == "" {
		pkgs, err := runner.AutoDetectPackages()
		if err != nil {
			r.log.Errorw(fmt.Sprintf("could not auto detect packages: %s", err))
		}
		r.packages = strings.Join(pkgs, ",")
	}
	data := fmt.Sprintf(`outDir: %s
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: %s`, dir, r.packages)
	// Add test annotations if they were provided
	if r.annotations != "" {
		data = data + "\n" + fmt.Sprintf("testAnnotations: %s", r.annotations)
	}
	iniFile := filepath.Join(r.tmpFilePath, "config.ini")
	r.log.Infow(fmt.Sprintf("attempting to write %s to %s", data, iniFile))
	f, err := r.fs.Create(iniFile)
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not create file %s", iniFile), zap.Error(err))
		return "", err
	}
	_, err = f.Write([]byte(data))
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not write %s to file %s", data, iniFile), zap.Error(err))
		return "", err
	}
	// Return path to the java agent file
	return iniFile, nil
}

/*
Creates config.yaml file for .NET agent to consume and returns the path to config.yaml file on successful creation.
Args:
  None
Returns:
  configPath (string): Path to the config.yaml file. Empty string on errors.
  err (error): Error if there's one, nil otherwise.
*/
func (r *runTestsTask) createDotNetConfigFile() (string, error) {
	// Create config file
	dir := filepath.Join(r.tmpFilePath, outDir)
	cgdir := filepath.Join(r.tmpFilePath, cgDir)
	err := r.fs.MkdirAll(dir, os.ModePerm)
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not create nested directory %s", dir), zap.Error(err))
		return "", err
	}

	if r.namespaces == "" {
		r.log.Errorw("Dotnet does not support auto detect namespaces", zap.Error(err))
	}
	var data string
	var outputFile string

	outputFile = filepath.Join(r.tmpFilePath, "config.yaml")
	namespaceArray := strings.Split(r.namespaces, ",")
	for idx, s := range namespaceArray {
		namespaceArray[idx] = fmt.Sprintf("'%s'", s)
	}
	data = fmt.Sprintf(`outDir: '%s'
logLevel: 0
writeTo: [COVERAGE_JSON]
instrPackages: [%s]`, cgdir, strings.Join(namespaceArray, ","))

	r.log.Infow(fmt.Sprintf("attempting to write %s to %s", data, outputFile))
	f, err := r.fs.Create(outputFile)
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not create file %s", outputFile), zap.Error(err))
		return "", err
	}
	_, err = f.Write([]byte(data))
	defer f.Close()
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not write %s to file %s", data, outputFile), zap.Error(err))
		return "", err
	}
	// Return path to the config.yaml file
	return outputFile, nil
}

func valid(tests []types.RunnableTest) bool {
	for _, t := range tests {
		if t.Class == "" {
			return false
		}
	}
	return true
}

func (r *runTestsTask) getTestSelection(ctx context.Context, files []types.File, isManual bool) types.SelectTestsResp {
	resp := types.SelectTestsResp{}
	log := r.log

	if isManual {
		// Manual execution: Select all tests in case of manual execution
		log.Infow("Detected manual execution - for test intelligence to be configured, a PR must be raised. Running all the tests")
		r.runOnlySelectedTests = false
	} else if len(files) == 0 {
		// PR execution: Select all tests if unable to find changed files list
		log.Infow("Unable to get changed files list")
		r.runOnlySelectedTests = false
	} else {
		// PR execution: Call TI svc only when there is a chance of running selected tests
		var err error
		resp, err = selectTestsFn(ctx, files, r.runOnlySelectedTests, r.id, r.log, r.fs)
		if err != nil {
			log.Errorw("There was some issue in trying to intelligently figure out tests to run. Running all the tests", "error", zap.Error(err))
			r.runOnlySelectedTests = false
		} else if !valid(resp.Tests) { // This shouldn't happen
			log.Errorw("Test Intelligence did not return suitable tests")
			r.runOnlySelectedTests = false
		} else if resp.SelectAll == true {
			log.Infow("Test Intelligence determined to run all the tests")
			r.runOnlySelectedTests = false
		} else {
			r.log.Infow(fmt.Sprintf("Running tests selected by Test Intelligence: %s", resp.Tests))
		}
	}
	return resp
}

func (r *runTestsTask) getCmd(ctx context.Context, agentPath, outputVarFile string) (string, error) {
	// Get the tests that need to be run if we are running selected tests
	var selection types.SelectTestsResp
	var files []types.File
	err := json.Unmarshal([]byte(r.diffFiles), &files)
	if err != nil {
		return "", err
	}
	isManual := isManualFn()
	selection = r.getTestSelection(ctx, files, isManual)

	var runner testintelligence.TestRunner
	switch r.language {
	case "java":
		switch r.buildTool {
		case "maven":
			runner = java.NewMavenRunner(r.log, r.fs, r.cmdContextFactory)
		case "gradle":
			runner = java.NewGradleRunner(r.log, r.fs, r.cmdContextFactory)
		case "bazel":
			runner = java.NewBazelRunner(r.log, r.fs, r.cmdContextFactory)
		default:
			return "", fmt.Errorf("build tool: %s is not supported for Java", r.buildTool)
		}
	case "csharp":
		{
			switch r.buildTool {
			case "dotnet":
				runner = csharp.NewDotnetRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			case "nunitconsole":
				runner = csharp.NewNunitConsoleRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			default:
				return "", fmt.Errorf("build tool: %s is not supported for csharp", r.buildTool)
			}
		}
	default:
		return "", fmt.Errorf("language %s is not suported", r.language)
	}

	outputVarCmd := ""
	for _, o := range r.envVarOutputs {
		outputVarCmd += fmt.Sprintf("\necho %s $%s >> %s", o, o, outputVarFile)
	}

	var iniFilePath, agentArg string

	switch r.language {
	case "java":
		{
			// Create the java agent config file
			iniFilePath, err = r.createJavaAgentConfigFile(runner)
			if err != nil {
				return "", err
			}
			agentArg = fmt.Sprintf(javaAgentArg, iniFilePath)
		}
	case "csharp":
		{
			iniFilePath, err = r.createDotNetConfigFile()
			if err != nil {
				return "", err
			}
		}
	}

	testCmd, err := runner.GetCmd(ctx, selection.Tests, r.args, iniFilePath, isManual, !r.runOnlySelectedTests)
	if err != nil {
		return "", err
	}

	// TMPDIR needs to be set for some build tools like bazel
	// TODO: (Vistaar) These commands need to be handled for Windows as well. We should move this out to the tool
	// implementations and check for OS there.
	command := fmt.Sprintf("set -xe\nexport TMPDIR=%s\nexport HARNESS_JAVA_AGENT=%s\n%s\n%s\n%s%s", r.tmpFilePath, agentArg, r.preCommand, testCmd, r.postCommand, outputVarCmd)
	resolvedCmd, err := resolveExprInCmd(command)
	if err != nil {
		return "", err
	}

	return resolvedCmd, nil
}

func (r *runTestsTask) execute(ctx context.Context, retryCount int32) (map[string]string, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(r.timeoutSecs))
	defer cancel()

	// Install agent artifacts if not present
	var agentPath = ""
	if r.language == "csharp" {
		csharpAgentPath, err := installAgentFn(ctx, r.tmpFilePath, r.language, r.buildTool, r.frameworkVersion, r.buildEnvironment, r.log, r.fs)
		if err != nil {
			return nil, err
		}
		r.log.Infow("agent downloaded to: " + csharpAgentPath)
		// Unzip everything at agentInstallDir/dotnet-agent.zip
		err = unzipSource(filepath.Join(csharpAgentPath, "dotnet-agent.zip"), csharpAgentPath, r.log, r.fs)
		if err != nil {
			r.log.Errorw("could not unarchive the dotnet agent", zap.Error(err))
			return nil, err
		}
		agentPath = csharpAgentPath
	}

	outputFile := filepath.Join(r.tmpFilePath, fmt.Sprintf("%s%s", r.id, outputEnvSuffix))
	cmdToExecute, err := r.getCmd(ctx, agentPath, outputFile)
	if err != nil {
		r.log.Errorw("could not create run command", zap.Error(err))
		return nil, err
	}

	envVars, err := resolveExprInEnv(r.environment)
	if err != nil {
		return nil, err
	}

	cmdArgs := []string{"-c", cmdToExecute}

	cmd := r.cmdContextFactory.CmdContextWithSleep(ctx, cmdExitWaitTime, "sh", cmdArgs...).
		WithStdout(r.procWriter).WithStderr(r.procWriter).WithEnvVarsMap(envVars)
	err = runCmdFn(ctx, cmd, r.id, cmdArgs, retryCount, start, r.logMetrics, r.addonLogger)
	if err != nil {
		return nil, err
	}

	stepOutput := make(map[string]string)
	if len(r.envVarOutputs) != 0 {
		var err error
		outputVars, err := fetchOutputVariables(outputFile, r.fs, r.log)
		if err != nil {
			logCommandExecErr(r.log, "error encountered while fetching output of runtest step", r.id, cmdToExecute, retryCount, start, err)
			return nil, err
		}

		stepOutput = outputVars
	}

	r.addonLogger.Infow(
		"successfully executed run tests step",
		"arguments", cmdToExecute,
		"output", stepOutput,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return stepOutput, nil
}
