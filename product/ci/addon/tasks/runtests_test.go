// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"bytes"
	"context"

	"encoding/json"
	"errors"
	"fmt"
	"os"
	"testing"
	"time"

	"github.com/harness/harness-core/product/ci/addon/testintelligence/mocks"

	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/exec"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestCreateJavaAgentArg(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	annotations := "a1, a2, a3"
	packages := "p1, p2, p3"

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		tmpFilePath:          tmpFilePath,
		annotations:          annotations,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3
testAnnotations: a1, a2, a3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil)

	runner := mocks.NewMockTestRunner(ctrl)
	arg, err := r.createJavaAgentConfigFile(runner)
	assert.Nil(t, err)
	assert.Equal(t, arg, "/test/tmp/config.ini")
}

func TestCreateJavaAgentArg_WithWriteFailure(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, errors.New("could not write data"))
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil)

	runner := mocks.NewMockTestRunner(ctrl)
	_, err := r.createJavaAgentConfigFile(runner)
	assert.NotNil(t, err)
}

func TestGetCmd_WithNoFilesChanged(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls1", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{})

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: false,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}

	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	want := `set -xe
export TMPDIR=/test/tmp
export HARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini
echo x
mvn -am -DharnessArgLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini -DargLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini clean test
echo y`
	got, err := r.getCmd(ctx, "/tmp/addon/agent", outputFile)
	assert.Nil(t, err)
	assert.Equal(t, r.runOnlySelectedTests, false) // If no errors, we should run only selected tests
	assert.Equal(t, got, want)
}

func TestGetCmd_SelectAll(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: true,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}

	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	want := `set -xe
export TMPDIR=/test/tmp
export HARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini
echo x
mvn -am -DharnessArgLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini -DargLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini clean test
echo y`
	got, err := r.getCmd(ctx, "/tmp/addon/agent", outputFile)
	assert.Nil(t, err)
	assert.Equal(t, r.runOnlySelectedTests, false) // Since selection returns all the tests
	assert.Equal(t, got, want)
}

func TestGetCmd_RunAll(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{}, errors.New("error in selection")
	}

	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	want := `set -xe
export TMPDIR=/test/tmp
export HARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini
echo x
mvn -am -DharnessArgLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini -DargLine=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini clean test
echo y`
	got, err := r.getCmd(ctx, "/tmp/addon/agent", outputFile)
	assert.Nil(t, err)
	assert.Equal(t, r.runOnlySelectedTests, false) // Since there was an error in execution
	assert.Equal(t, got, want)
}

func TestGetCmd_ManualExecution(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{}, nil
	}

	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return true
	}

	want := `set -xe
export TMPDIR=/test/tmp
export HARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini
echo x
mvn clean test
echo y`
	got, err := r.getCmd(ctx, "/tmp/addon/agent", outputFile)
	assert.Nil(t, err)
	assert.Equal(t, r.runOnlySelectedTests, false) // Since it's a manual execution
	assert.Equal(t, got, want)
}

func TestGetCmd_ErrorIncorrectBuildTool(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "random",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		packages:             packages,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{}, nil
	}

	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	_, err := r.getCmd(ctx, "/tmp/addon/agent", outputFile)
	assert.NotNil(t, err)
}

func TestNewRunTestsTask(t *testing.T) {
	diff := "diff"
	preCommand := "pre"
	postCommand := "post"
	lang := "java"
	buildTool := "maven"
	args := "args"
	packages := "packages"
	annotations := "annotations"
	runOnlySelectedTests := false

	runTests := &pb.UnitStep_RunTests{RunTests: &pb.RunTestsStep{
		Args:                 args,
		Language:             lang,
		BuildTool:            buildTool,
		RunOnlySelectedTests: runOnlySelectedTests,
		PreTestCommand:       preCommand,
		PostTestCommand:      postCommand,
		DiffFiles:            diff,
		Packages:             packages,
		TestAnnotations:      annotations,
	}}
	step := &pb.UnitStep{
		Id: "id", Step: runTests}

	task := NewRunTestsTask(step, "/tmp", nil, nil, false, nil)
	assert.Equal(t, task.args, args)
	assert.Equal(t, task.language, lang)
	assert.Equal(t, task.buildTool, buildTool)
	assert.Equal(t, task.runOnlySelectedTests, runOnlySelectedTests)
	assert.Equal(t, task.preCommand, preCommand)
	assert.Equal(t, task.postCommand, postCommand)
	assert.Equal(t, task.diffFiles, diff)
	assert.Equal(t, task.packages, packages)
	assert.Equal(t, task.annotations, annotations)
}

func TestRun_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)

	var buf bytes.Buffer

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil).AnyTimes()

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, "sh", gomock.Any(), gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)

	oldRunCmd := runCmdFn
	defer func() {
		runCmdFn = oldRunCmd
	}()
	runCmdFn = func(ctx context.Context, cmd exec.Command, stepID string, commands []string, retryCount int32, startTime time.Time,
		logMetrics bool, addonLogger *zap.SugaredLogger) error {
		return nil
	}

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		logMetrics:           false,
		packages:             packages,
		procWriter:           &buf,
		numRetries:           1,
		cmdContextFactory:    cmdFactory,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	// Mock test selection
	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: false,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}
	called := 0

	// Mock collectCg
	oldCollectCg := collectCgFn
	defer func() {
		collectCgFn = oldCollectCg
	}()
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger) error {
		called += 1
		return nil
	}

	// Mock test reports
	oldReports := collectTestReportsFn
	defer func() {
		collectTestReportsFn = oldReports
	}()
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger) error {
		called += 1
		return nil
	}

	oldInstallAgent := installAgentFn
	defer func() {
		installAgentFn = oldInstallAgent
	}()
	installAgentFn = func(ctx context.Context, path, language, framework, frameworkVersion, buildEnvironment string, log *zap.SugaredLogger, fs filesystem.FileSystem) (string, error) {
		return "", nil
	}

	_, _, err := r.Run(ctx)
	assert.Nil(t, err)
	assert.Equal(t, called, 2) // Make sure both CG collection and report collection are called
}

func TestRun_Execution_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)

	var buf bytes.Buffer

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil)

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, "sh", gomock.Any(), gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)
	expErr := errors.New("could not run command")
	oldRunCmd := runCmdFn
	defer func() {
		runCmdFn = oldRunCmd
	}()
	runCmdFn = func(ctx context.Context, cmd exec.Command, stepID string, commands []string, retryCount int32, startTime time.Time,
		logMetrics bool, addonLogger *zap.SugaredLogger) error {
		return expErr
	}

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		logMetrics:           false,
		packages:             packages,
		procWriter:           &buf,
		numRetries:           1,
		cmdContextFactory:    cmdFactory,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	called := 0

	// Mock test selection
	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: false,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}

	// Mock collectCg
	oldCollectCg := collectCgFn
	defer func() {
		collectCgFn = oldCollectCg
	}()
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger) error {
		called += 1
		return nil
	}

	// Set isManual to false
	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	// Mock test reports
	oldReports := collectTestReportsFn
	defer func() {
		collectTestReportsFn = oldReports
	}()
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger) error {
		called += 1
		return nil
	}

	oldInstallAgent := installAgentFn
	defer func() {
		installAgentFn = oldInstallAgent
	}()
	installAgentFn = func(ctx context.Context, path, language, framework, frameworkVersion, buildEnvironment string, log *zap.SugaredLogger, fs filesystem.FileSystem) (string, error) {
		return "", nil
	}

	_, _, err := r.Run(ctx)
	assert.Equal(t, err, expErr)
	assert.Equal(t, called, 2) // makes ure both functions are called even on failure
}

func TestRun_Execution_Cg_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)

	var buf bytes.Buffer

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil)

	diffFiles, _ := json.Marshal([]types.File{{Name: "abc.java", Status: types.FileModified}})

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, "sh", gomock.Any(), gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)

	oldRunCmd := runCmdFn
	defer func() {
		runCmdFn = oldRunCmd
	}()
	runCmdFn = func(ctx context.Context, cmd exec.Command, stepID string, commands []string, retryCount int32, startTime time.Time,
		logMetrics bool, addonLogger *zap.SugaredLogger) error {
		return nil
	}

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		logMetrics:           false,
		packages:             packages,
		procWriter:           &buf,
		numRetries:           1,
		cmdContextFactory:    cmdFactory,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	// Mock test selection
	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: false,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}

	// Mock collectCg
	oldCollectCg := collectCgFn
	defer func() {
		collectCgFn = oldCollectCg
	}()
	errCg := errors.New("could not collect CG")
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger) error {
		return errCg
	}

	// Set isManual to false
	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	// Mock test reports
	oldReports := collectTestReportsFn
	defer func() {
		collectTestReportsFn = oldReports
	}()
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger) error {
		return nil
	}

	oldInstallAgent := installAgentFn
	defer func() {
		installAgentFn = oldInstallAgent
	}()
	installAgentFn = func(ctx context.Context, path, language, framework, frameworkVersion, buildEnvironment string, log *zap.SugaredLogger, fs filesystem.FileSystem) (string, error) {
		return "", nil
	}
	_, _, err := r.Run(ctx)
	assert.Equal(t, err, errCg)
}

func TestRun_Execution_Reports_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)

	var buf bytes.Buffer

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := fmt.Sprintf(outDir, tmpFilePath)
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().Create("/test/tmp/config.ini").Return(mf, nil)

	diffFiles, _ := json.Marshal([]types.File{})

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, "sh", gomock.Any(), gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)

	oldRunCmd := runCmdFn
	defer func() {
		runCmdFn = oldRunCmd
	}()
	runCmdFn = func(ctx context.Context, cmd exec.Command, stepID string, commands []string, retryCount int32, startTime time.Time,
		logMetrics bool, addonLogger *zap.SugaredLogger) error {
		return nil
	}

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		fs:                   fs,
		preCommand:           "echo x",
		diffFiles:            string(diffFiles),
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		tmpFilePath:          tmpFilePath,
		logMetrics:           false,
		packages:             packages,
		procWriter:           &buf,
		numRetries:           1,
		cmdContextFactory:    cmdFactory,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}

	// Mock test selection
	oldSelect := selectTestsFn
	defer func() {
		selectTestsFn = oldSelect
	}()
	selectTestsFn = func(ctx context.Context, f []types.File, runSelected bool, id string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
		return types.SelectTestsResp{
			SelectAll: false,
			Tests:     []types.RunnableTest{t1, t2}}, nil
	}

	// Mock collectCg
	oldCollectCg := collectCgFn
	defer func() {
		collectCgFn = oldCollectCg
	}()
	collectCgFn = func(ctx context.Context, stepID, collectcgDir string, timeTakenMs int64, log *zap.SugaredLogger) error {
		return nil
	}

	// Set isManual to false
	oldIsManual := isManualFn
	defer func() {
		isManualFn = oldIsManual
	}()
	isManualFn = func() bool {
		return false
	}

	// Mock test reports
	errReport := errors.New("could not collect reports")
	oldReports := collectTestReportsFn
	defer func() {
		collectTestReportsFn = oldReports
	}()
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger) error {
		return errReport
	}

	oldInstallAgent := installAgentFn
	defer func() {
		installAgentFn = oldInstallAgent
	}()
	installAgentFn = func(ctx context.Context, path, language, framework, frameworkVersion, buildEnvironment string, log *zap.SugaredLogger, fs filesystem.FileSystem) (string, error) {
		return "", nil
	}

	_, _, err := r.Run(ctx)
	assert.Equal(t, err, errReport)
}
