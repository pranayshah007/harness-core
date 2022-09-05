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
	stutils "github.com/harness/harness-core/product/ci/split_tests/utils"
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
	selectTestsFn             = selectTests
	collectCgFn               = collectCg
	collectTestReportsFn      = collectTestReports
	runCmdFn                  = runCmd
	isManualFn                = external.IsManualExecution
	installAgentFn            = installAgents
	getWorkspace              = external.GetWrkspcPath
	isParallelismEnabled      = external.IsParallelismEnabled
	getStepStrategyIteration  = external.GetStepStrategyIteration
	getStepStrategyIterations = external.GetStepStrategyIterations
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
	splitStrategy        string
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
		splitStrategy:        r.GetSplitStrategy(),
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
			errCg = collectCgFn(ctx, r.id, cgDir, time.Since(testSt).Milliseconds(), r.log)
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
			r.log.Infow(fmt.Sprintf("successfully uploaded partial callgraph in %s time", cgTime))
			return o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect callgraph and reports, ignore any errors during collection steps itself
		errCg = collectCgFn(ctx, r.id, cgDir, time.Since(testSt).Milliseconds(), r.log)
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
		// Select all tests in case of manual execution
		log.Infow("detected manual execution - for intelligence to be configured, a PR must be raised. Running all the tests")
		r.runOnlySelectedTests = false
	} else if len(files) == 0 {
		// Select all tests if unable to find changed files list
		log.Infow("unable to get changed files list")
		r.runOnlySelectedTests = false
	} else {
		// Call TI svc only when there is a chance of running selected tests
		resp, err := selectTestsFn(ctx, files, r.runOnlySelectedTests, r.id, r.log, r.fs)
		if err != nil {
			fmt.Println("GOT ERROR", err)
			log.Errorw("there was some issue in trying to intelligently figure out tests to run. Running all the tests", "error", zap.Error(err))
			r.runOnlySelectedTests = false
		} else if !valid(resp.Tests) { // This shouldn't happen
			log.Errorw("test intelligence did not return suitable tests")
			r.runOnlySelectedTests = false
		} else if resp.SelectAll == true {
			log.Infow("intelligently determined to run all the tests")
			r.runOnlySelectedTests = false
			fmt.Println("RunSelected3", r.runOnlySelectedTests)
		} else {
			r.log.Infow(fmt.Sprintf("intelligently running tests: %s", resp.Tests))
		}
	}
	fmt.Println("RunSelected2", r.runOnlySelectedTests)
	return resp
}

func (r *runTestsTask) getSplitTests(ctx context.Context, tests []types.RunnableTest, splitStrategy string) ([]types.RunnableTest, error) {
	if len(tests) == 0 {
		return tests, nil
	}

	res := make([]types.RunnableTest, 0)
	idx, _ := getStepStrategyIteration()
	total, _ := getStepStrategyIterations()

	currentFileMap := make(map[string][]types.RunnableTest)
	currentFileSet := make(map[string]bool)
	var testId string
	for _, t := range tests {
		switch splitStrategy {
		case "class_timing":
			testId = t.Pkg + t.Class
		case "file_size":
			testId = t.Autodetect.Path
		default:
			testId = t.Pkg + t.Class
		}
		currentFileSet[testId] = true
		currentFileMap[testId] = append(currentFileMap[testId], t)
	}

	fileTimes := map[string]float64{}
	var err error
	// Estimate by strategy - line count
	switch splitStrategy {
	case "file_size":
		stutils.EstimateFileTimesByLineCount(r.log, currentFileSet, fileTimes)
	case "class_timing":
		// Call TI to get the test times
		fmt.Println("Not implemented")
		fileTimes, err = getTestTime(ctx, r.log, splitStrategy)
		if err != nil {
			fmt.Println("Error while calling TI", err)
			return tests, err
		}
		fmt.Println("File times", fileTimes)
	case "split_equal":
		// Send empty fileTimesMap while processing to assign equal weights
	default:
		// Send empty fileTimesMap while processing to assign equal weights
	}

	fmt.Println("Printing weights", fileTimes)
	stutils.ProcessFiles(fileTimes, currentFileSet, float64(1), false)

	// Split tests into buckets and return tests from the current node's bucket
	buckets, _ := stutils.SplitFiles(fileTimes, total)
	for _, testId := range buckets[idx] {
		if _, ok := currentFileMap[testId]; !ok {
			continue
		}
		res = append(res, currentFileMap[testId]...)
	}
	return res, nil
}

func (r *runTestsTask) invokeParallelism(ctx context.Context, runner testintelligence.TestRunner, selection *types.SelectTestsResp, ignoreInstr, skip bool) bool {
	if skip {
		return ignoreInstr
	}
	// TI returned zero test cases to run. Skip parallelism as
	// there are no tests to run
	if r.runOnlySelectedTests && (len(selection.Tests) == 0) {
		return ignoreInstr
	}

	tests := make([]types.RunnableTest, 0)
	if !r.runOnlySelectedTests {
		// For full runs, detect all the tests in the repo and split them
		// If autodetect fails or detects no tests, we run all tests
		tests, err := runner.AutoDetectTestFiles(ctx)
		if err != nil || len(tests) == 0 {
			// Error in auto-detecting test files, run all tests
			// Run all tests if no tests are detected
			r.runOnlySelectedTests = false
			fmt.Println("Error in auto-detecting test files, run all tests")
		} else {
			r.log.Infow(fmt.Sprintf("Autodetected test packages: %s", tests))
			// Auto-detected tests, split them
			splitTests, err := r.getSplitTests(ctx, tests, "class_timing")
			fmt.Println("Comparing lengths", len(splitTests), len(tests))
			if err != nil {
				// Error while splitting by input strategy, splitting tests equally
				r.log.Infow("Error occurred while splitting the tests. Splitting detected tests equally")
				splitTests, _ = r.getSplitTests(ctx, tests, "split_equal")
				selection.Tests = tests
			} else {
				r.log.Infow(fmt.Sprintf("Test split for this run: %s", splitTests))
				// Send the split slice to the runner instead of all tests
				selection.Tests = splitTests
				r.runOnlySelectedTests = true
				ignoreInstr = false
			}
		}
	} else if len(selection.Tests) > 0 {
		// In case of intelligent runs, split the tests from TI SelectTests API response
		tests = selection.Tests
		splitTests, err := r.getSplitTests(ctx, tests, "class_timing")
		if err != nil {
			// Error while splitting by input strategy, splitting tests equally
			r.log.Infow("Error occurred while splitting the tests. Splitting selected tests equally")
			splitTests, _ = r.getSplitTests(ctx, tests, "split_equal")
			selection.Tests = tests
		} else {
			r.log.Infow(fmt.Sprintf("Test split for this run: %s", splitTests))
			// Send the split slice to the runner instead of all tests
			selection.Tests = splitTests
			r.runOnlySelectedTests = true
			ignoreInstr = false
		}
	}
	fmt.Println("Test length", len(selection.Tests))
	return ignoreInstr
}

func (r *runTestsTask) getCmd(ctx context.Context, agentPath, outputVarFile string) (string, error) {
	// Get the tests that need to be run if we are running selected tests
	var selection types.SelectTestsResp
	var files []types.File
	err := json.Unmarshal([]byte(r.diffFiles), &files)
	if err != nil {
		return "", err
	}

	// Test selection
	isManual := isManualFn()
	ignoreInstr := isManual
	selection = r.getTestSelection(ctx, files, true)

	// Runner selection
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

	// Environment variables
	outputVarCmd := ""
	for _, o := range r.envVarOutputs {
		outputVarCmd += fmt.Sprintf("\necho %s $%s >> %s", o, o, outputVarFile)
	}

	// Config file
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

	fmt.Println("Parallel", isParallelismEnabled())
	fmt.Println("RunSelected", r.runOnlySelectedTests)
	fmt.Println("Previous tests", len(selection.Tests), selection.Tests)
	fmt.Println("RunNoTests", r.runOnlySelectedTests && (len(selection.Tests) == 0))

	// Test splitting: only when parallelism is enabled
	doNotSplit := false
	if isParallelismEnabled() {
		ignoreInstr = r.invokeParallelism(ctx, runner, &selection, ignoreInstr, doNotSplit)
	}

	//fmt.Println("New tests", len(selection.Tests), selection.Tests, r.runOnlySelectedTests)

	// Test command
	testCmd, err := runner.GetCmd(ctx, selection.Tests, r.args, iniFilePath, ignoreInstr, !r.runOnlySelectedTests)
	fmt.Println("Command for bazel test", testCmd)
	time.Sleep(100 * time.Second)
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
