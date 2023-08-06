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
	"github.com/harness/harness-core/product/ci/addon/testintelligence/python"
	"github.com/harness/harness-core/product/ci/common/external"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	stutils "github.com/harness/harness-core/product/ci/split_tests/utils"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

const (
	defaultRunTestsTimeout       int64 = 14400 // 4 hour
	defaultRunTestsRetries       int32 = 1
	outDir                             = "ti/callgraph/"    // path passed as outDir in the config.ini file
	cgDir                              = "ti/callgraph/cg/" // path where callgraph files will be generated
	javaAgentArg                       = "-javaagent:/addon/bin/java-agent.jar=%s"
	tiConfigPath                       = ".ticonfig.yaml"
	classTimingTestSplitStrategy       = stutils.SplitByClassTimeStr
	countTestSplitStrategy             = stutils.SplitByTestCount
	defaultTestSplitStrategy           = classTimingTestSplitStrategy
)

var (
	selectTestsFn                = selectTests
	getCommitInfoFn              = getCommitInfo
	getChangedFilesPushTriggerFn = getChangedFilesPushTrigger
	collectCgFn                  = collectCg
	collectTestReportsFn         = collectTestReports
	runCmdFn                     = runCmd
	isManualFn                   = external.IsManualExecution
	isPushTriggerFn              = external.IsPushTriggerExecution
	installAgentFn               = installAgents
	getWorkspace                 = external.GetWrkspcPath
	isParallelismEnabled         = external.IsParallelismEnabled
	getStepStrategyIteration     = external.GetStepStrategyIteration
	getStepStrategyIterations    = external.GetStepStrategyIterations
	getStageStrategyIteration    = external.GetStageStrategyIteration
	getStageStrategyIterations   = external.GetStageStrategyIterations
	isStepParallelismEnabled     = external.IsStepParallelismEnabled
	isStageParallelismEnabled    = external.IsStageParallelismEnabled
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
	testSplitStrategy    string
	parallelizeTests     bool
	testGlobs            string
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
	testSplitStrategy := r.GetTestSplitStrategy()
	if testSplitStrategy == "" {
		testSplitStrategy = defaultTestSplitStrategy
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
		testSplitStrategy:    testSplitStrategy,
		parallelizeTests:     r.GetParallelizeTests(),
		testGlobs:            r.GetTestGlobs(),
	}
}

// Run executes commands with timeout
func (r *runTestsTask) Run(ctx context.Context) (map[string]string, int32, error) {
	cgDirPath := filepath.Join(r.tmpFilePath, cgDir)
	testSt := time.Now()

	stepOutput, err := r.execute(ctx)
	collectionErr := r.collectRunTestData(ctx, cgDirPath, testSt)
	if err == nil {
		// Fail the step if run was successful but error during collection
		err = collectionErr
	}
	return stepOutput, r.numRetries, err
}

func (r *runTestsTask) collectRunTestData(ctx context.Context, cgDirPath string, testSt time.Time) error {
	cgStart := time.Now()
	errCg := collectCgFn(ctx, r.id, cgDirPath, time.Since(testSt).Milliseconds(), r.log, cgStart)
	if errCg != nil {
		r.log.Errorw(fmt.Sprintf("Unable to collect callgraph. Time taken: %s", time.Since(cgStart)), zap.Error(errCg))
	}

	crStart := time.Now()
	errCr := collectTestReportsFn(ctx, r.reports, r.id, r.log, crStart)
	if errCr != nil {
		r.log.Errorw(fmt.Sprintf("Unable to collect tests reports. Time taken: %s", time.Since(crStart)), zap.Error(errCr))
	}

	if errCg != nil {
		return errCg
	}
	return errCr
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

/*
Creates config.ini file for Python agent to consume and returns the path to config.ini file on successful creation.
Args:

	None

Returns:

	configPath (string): Path to the config.ini file. Empty string on errors.
	err (error): Error if there's one, nil otherwise.
*/
func (r *runTestsTask) createPythonConfigFile() (string, error) {
	// Create config file
	dir := filepath.Join(r.tmpFilePath, outDir)
	cgdir := filepath.Join(r.tmpFilePath, cgDir)
	err := r.fs.MkdirAll(dir, os.ModePerm)
	if err != nil {
		r.log.Errorw(fmt.Sprintf("could not create nested directory %s", dir), zap.Error(err))
		return "", err
	}
	var data string
	var outputFile string

	outputFile = filepath.Join(r.tmpFilePath, "config.ini")
	namespaceArray := strings.Split(r.namespaces, ",")
	for idx, s := range namespaceArray {
		namespaceArray[idx] = fmt.Sprintf("'%s'", s)
	}
	data = fmt.Sprintf(`outDir: '%s'
logLevel: 0`, cgdir)

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
	// Return path to the config.ini file
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

func (r *runTestsTask) getTestSelection(ctx context.Context, runner testintelligence.TestRunner, files []types.File, isManual bool) types.SelectTestsResp {
	resp := types.SelectTestsResp{}
	log := r.log

	if isManual {
		// Manual execution: Select all tests in case of manual execution
		log.Infow("Detected manual execution - for test intelligence to be configured the execution should be via a PR or Push trigger. Running all the tests")
		r.runOnlySelectedTests = false
		return resp
	}
	// PR/Push execution
	var errChangedFiles error
	if isPushTriggerFn() {
		// Get changes files from the previous successful commit
		lastSuccessfulCommitID, err := getCommitInfoFn(ctx, r.id, r.log)
		if err != nil || lastSuccessfulCommitID == "" {
			// Run all tests when there's no CG present
			log.Infow("Test Intelligence determined to run all tests to bootstrap", "error", zap.Error(err))
			r.runOnlySelectedTests = false // TI selected all the tests to be run
			return resp
		}
		log.Infof("Using reference commit: %s", lastSuccessfulCommitID)
		files, errChangedFiles = getChangedFilesPushTriggerFn(ctx, r.id, lastSuccessfulCommitID, r.log)
	}
	if errChangedFiles != nil || len(files) == 0 {
		// Select all tests if unable to find changed files list
		log.Infow("Unable to get changed files list")
		r.runOnlySelectedTests = false
		return resp
	}
	// Call TI svc to get test selection based on the files changed
	files = runner.ReadPackages(files)
	var err error
	resp, err = selectTestsFn(ctx, files, r.runOnlySelectedTests, r.id, r.log, r.fs)
	if err != nil {
		log.Errorw("There was some issue in trying to intelligently figure out tests to run, running all the tests", "error", zap.Error(err))
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
	return resp
}

// getSplitTests takes a list of tests as input and returns the slice of tests to run depending on
// the test split strategy and index
func (r *runTestsTask) getSplitTests(ctx context.Context, testsToSplit []types.RunnableTest, splitStrategy string, splitIdx, splitTotal int) ([]types.RunnableTest, error) {
	if len(testsToSplit) == 0 {
		return testsToSplit, nil
	}

	currentTestMap := make(map[string][]types.RunnableTest)
	currentTestSet := make(map[string]bool)
	var testID string
	for _, t := range testsToSplit {
		switch splitStrategy {
		case classTimingTestSplitStrategy, countTestSplitStrategy:
			testID = t.Pkg + "." + t.Class
		default:
			testID = t.Pkg + "." + t.Class
		}
		currentTestSet[testID] = true
		currentTestMap[testID] = append(currentTestMap[testID], t)
	}

	fileTimes := map[string]float64{}
	var err error

	// Get weights for each test depending on the strategy
	switch splitStrategy {
	case classTimingTestSplitStrategy:
		// Call TI svc to get the test timing data
		fileTimes, err = getTestTime(ctx, r.log, splitStrategy)
		if err != nil {
			return testsToSplit, err
		}
		r.log.Infow("Successfully retrieved timing data for splitting")
	case countTestSplitStrategy:
		// Send empty fileTimesMap while processing to assign equal weights
		r.log.Infow("Assigning all tests equal weight for splitting")
	default:
		// Send empty fileTimesMap while processing to assign equal weights
		r.log.Infow("Assigning all tests equal weight for splitting as default strategy")
	}

	// Assign weights to the current test set if present, else average. If there are no
	// weights for taking average, set the weight as 1 to all the tests
	stutils.ProcessFiles(fileTimes, currentTestSet, float64(1), false)

	// Split tests into buckets and return tests from the current node's bucket
	testsToRun := make([]types.RunnableTest, 0)
	buckets, _ := stutils.SplitFiles(fileTimes, splitTotal)
	for _, id := range buckets[splitIdx] {
		if _, ok := currentTestMap[id]; !ok {
			// This should not happen
			r.log.Warnw(fmt.Sprintf("Test %s from the split not present in the original set of tests, skipping", id))
			continue
		}
		testsToRun = append(testsToRun, currentTestMap[id]...)
	}
	return testsToRun, nil
}

func formatTests(tests []types.RunnableTest) string {
	testStrings := make([]string, 0)
	for _, t := range tests {
		tString := t.Class
		if t.Pkg != "" {
			tString = fmt.Sprintf("%s.", t.Pkg) + tString
		}
		if t.Autodetect.Rule != "" {
			tString += fmt.Sprintf(" %s", t.Autodetect.Rule)
		}
		testStrings = append(testStrings, tString)
	}
	return strings.Join(testStrings, ", ")
}

func (r *runTestsTask) computeSelectedTests(ctx context.Context, runner testintelligence.TestRunner, selection *types.SelectTestsResp) {
	if !r.parallelizeTests {
		r.log.Info("Skipping test splitting as requested")
		return
	}

	if r.runOnlySelectedTests && len(selection.Tests) == 0 {
		// TI returned zero test cases to run. Skip parallelism as
		// there are no tests to run
		return
	}

	r.log.Info("Splitting the tests as parallelism is enabled")

	stepIdx, _ := getStepStrategyIteration(r.environment)
	stepTotal, _ := getStepStrategyIterations(r.environment)
	if !isStepParallelismEnabled(r.environment) {
		stepIdx = 0
		stepTotal = 1
	}
	stageIdx, _ := getStageStrategyIteration(r.environment)
	stageTotal, _ := getStageStrategyIterations(r.environment)
	if !isStageParallelismEnabled(r.environment) {
		stageIdx = 0
		stageTotal = 1
	}
	splitIdx := stepTotal*stageIdx + stepIdx
	splitTotal := stepTotal * stageTotal

	tests := make([]types.RunnableTest, 0)
	if !r.runOnlySelectedTests {
		// For full runs, detect all the tests in the repo and split them
		// If autodetect fails or detects no tests, we run all tests in step 0
		var err error
		testGlobs := strings.Split(r.testGlobs, ",")
		tests, err = runner.AutoDetectTests(ctx, testGlobs)
		if err != nil || len(tests) == 0 {
			// AutoDetectTests output should be same across all the parallel steps. If one of the step
			// receives error / no tests to run, all the other steps should have the same output
			if splitIdx == 0 {
				// Error while auto-detecting, run all tests for parallel step 0
				r.runOnlySelectedTests = false
				r.log.Errorw("Error in auto-detecting tests for splitting, running all tests")
			} else {
				// Error while auto-detecting, no tests for other parallel steps
				selection.Tests = []types.RunnableTest{}
				r.runOnlySelectedTests = true
				r.log.Errorw("Error in auto-detecting tests for splitting, running all tests in parallel step 0")
			}
			return
		}
		// Auto-detected tests successfully
		r.log.Infow(fmt.Sprintf("Autodetected tests: %s", formatTests(tests)))
	} else if len(selection.Tests) > 0 {
		// In case of intelligent runs, split the tests from TI SelectTests API response
		tests = selection.Tests
	}

	// Split the tests and send the split slice to the runner
	splitTests, err := r.getSplitTests(ctx, tests, r.testSplitStrategy, splitIdx, splitTotal)
	if err != nil {
		// Error while splitting by input strategy, splitting tests equally
		r.log.Errorw("Error occurred while splitting the tests by input strategy. Splitting tests equally")
		splitTests, _ = r.getSplitTests(ctx, tests, countTestSplitStrategy, splitIdx, splitTotal)
	}
	r.log.Infow(fmt.Sprintf("Test split for this run: %s", formatTests(splitTests)))

	// Modify runner input to run selected tests
	selection.Tests = splitTests
	r.runOnlySelectedTests = true
}

func (r *runTestsTask) getCmd(ctx context.Context, agentPath, outputVarFile string) (string, error) {
	// Get the tests that need to be run if we are running selected tests
	var selection types.SelectTestsResp
	var files []types.File
	err := json.Unmarshal([]byte(r.diffFiles), &files)
	if err != nil {
		return "", err
	}
	// Ignore instrumentation when it's a manual run or user has unchecked RunOnlySelectedTests option
	isManual := isManualFn()
	ignoreInstr := isManual || !r.runOnlySelectedTests

	// Runner selection
	runner, err := r.getTiRunner(agentPath)
	if err != nil {
		return "", err
	}

	// Test selection
	selection = r.getTestSelection(ctx, runner, files, isManual)

	// Environment variables
	outputVarCmd := ""
	for _, o := range r.envVarOutputs {
		outputVarCmd += fmt.Sprintf("\necho %s $%s >> %s", o, o, outputVarFile)
	}

	// Config file
	var iniFilePath, agentArg string
	switch r.language {
	case "java", "scala", "kotlin":
		// Create the java agent config file
		iniFilePath, err = r.createJavaAgentConfigFile(runner)
		if err != nil {
			return "", err
		}
		agentArg = fmt.Sprintf(javaAgentArg, iniFilePath)
	case "csharp":
		{
			iniFilePath, err = r.createDotNetConfigFile()
			if err != nil {
				return "", err
			}
		}
	case "python":
		{
			iniFilePath, err = r.createPythonConfigFile()
			if err != nil {
				return "", err
			}
		}
	}

	// Test splitting: only when parallelism is enabled
	if isParallelismEnabled(r.environment) {
		r.computeSelectedTests(ctx, runner, &selection)
	}

	// Test command
	testCmd, err := runner.GetCmd(ctx, selection.Tests, r.args, iniFilePath, ignoreInstr, !r.runOnlySelectedTests)
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

	if ignoreInstr {
		r.log.Infow("Ignoring instrumentation and not attaching agent")
	}
	return resolvedCmd, nil
}

func (r *runTestsTask) execute(ctx context.Context) (map[string]string, error) {
	start := time.Now()
	retryCount := int32(1)
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(r.timeoutSecs))
	defer cancel()

	// Install agent artifacts if not present
	var agentPath = ""
	if r.language == "csharp" || r.language == "python" {
		zipAgentPath, err := installAgentFn(ctx, r.tmpFilePath, r.language, r.buildTool, r.frameworkVersion, r.buildEnvironment, r.log, r.fs)
		if err != nil {
			return nil, err
		}
		r.log.Infow("agent downloaded to: " + zipAgentPath)
		// Unzip everything at agentInstallDir/dotnet-agent.zip
		switch r.language {
		case "csharp":
			err = unzipSource(filepath.Join(zipAgentPath, "dotnet-agent.zip"), zipAgentPath, r.log, r.fs)
		default:
			err = unzipSource(filepath.Join(zipAgentPath, fmt.Sprintf("%s-agent.zip", r.language)), zipAgentPath, r.log, r.fs)
		}

		if err != nil {
			r.log.Errorw(fmt.Sprintf("could not unarchive the %s agent", r.language), zap.Error(err))
			return nil, err
		}
		agentPath = zipAgentPath
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
			logCommandExecErr(r.log, "error encountered while fetching output of runtest step from .env File", r.id, cmdToExecute, retryCount, start, err)
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

func (r *runTestsTask) getTiRunner(agentPath string) (runner testintelligence.TestRunner, err error) {
	switch r.language {
	case "scala", "java", "kotlin":
		switch r.buildTool {
		case "maven":
			runner = java.NewMavenRunner(r.log, r.fs, r.cmdContextFactory)
		case "gradle":
			runner = java.NewGradleRunner(r.log, r.fs, r.cmdContextFactory)
		case "bazel":
			runner = java.NewBazelRunner(r.log, r.fs, r.cmdContextFactory)
		case "sbt":
			{
				if r.language != "scala" {
					return runner, fmt.Errorf("build tool: SBT is not supported for non-Scala languages")
				}
				runner = java.NewSBTRunner(r.log, r.fs, r.cmdContextFactory)
			}
		default:
			return runner, fmt.Errorf("build tool: %s is not supported for Java", r.buildTool)
		}
	case "csharp":
		{
			switch r.buildTool {
			case "dotnet":
				runner = csharp.NewDotnetRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			case "nunitconsole":
				runner = csharp.NewNunitConsoleRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			default:
				return runner, fmt.Errorf("build tool: %s is not supported for csharp", r.buildTool)
			}
		}
	case "python":
		{
			switch r.buildTool {
			case "pytest":
				runner = python.NewPytestRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			case "unittest":
				runner = python.NewUnittestRunner(r.log, r.fs, r.cmdContextFactory, agentPath)
			default:
				return runner, fmt.Errorf("build tool: %s is not supported for python", r.buildTool)
			}
		}
	default:
		return runner, fmt.Errorf("language %s is not suported", r.language)
	}
	return runner, nil
}
