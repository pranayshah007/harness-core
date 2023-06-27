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
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/exec"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/addon/testintelligence/mocks"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/ti-client/types"
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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

func TestComputeSelected(t *testing.T) {
	rts := make([]types.RunnableTest, 0)
	for i := 1; i <= 12; i++ {
		rt := types.RunnableTest{
			Pkg:   fmt.Sprintf("p%d", i),
			Class: fmt.Sprintf("c%d", i),
		}
		rts = append(rts, rt)
	}
	emptyTestGlobsString := ""
	emptyTestGlobs := strings.Split(emptyTestGlobsString, ",")
	testGlobsString := "path1/to/test*/*.cs,path2/to/test*/*.cs"
	testGlobs := strings.Split(testGlobsString, ",")

	tests := []struct {
		name string
		// Input
		runOnlySelectedTestsBool  bool
		parallelizeTestsBool      bool
		isParallelismEnabledBool  bool
		isStepParallelismEnabled  bool
		isStageParallelismEnabled bool
		stepStrategyIteration     string
		stepStrategyIterations    string
		stageStrategyIteration    string
		stageStrategyIterations   string
		runnableTests             []types.RunnableTest
		runnerAutodetectExpect    bool
		runnerAutodetectTestsVal  []types.RunnableTest
		runnerAutodetectTestsErr  error
		testGlobsString           string
		// Verify
		runOnlySelectedTests     bool
		selectTestsResponseTests []types.RunnableTest
		testGlobs                []string
	}{
		{
			name: "SkipParallelization_Manual",
			// Input
			runOnlySelectedTestsBool: true,
			parallelizeTestsBool:     false,
			// Expect
			runOnlySelectedTests: true,
		},
		{
			name: "SkipParallelization_TiSelection",
			// Input
			runOnlySelectedTestsBool: true,
			parallelizeTestsBool:     false,
			runnableTests:            rts[:1],
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: rts[:1],
		},
		{
			name: "SkipTestSplitting_TiSelectionZeroTests",
			// Input
			runOnlySelectedTestsBool: true,
			parallelizeTestsBool:     true,
			runnableTests:            []types.RunnableTest{}, // TI returned 0 tests to run
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{},
		},
		{
			name: "ManualAutodetectPass",
			// Input
			runOnlySelectedTestsBool:  false,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: false,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "",
			stageStrategyIterations:   "",
			runnableTests:             []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:    true,
			runnerAutodetectTestsVal:  []types.RunnableTest{rts[0], rts[1]},
			runnerAutodetectTestsErr:  nil,
			testGlobsString:           emptyTestGlobsString,
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]},
			testGlobs:                emptyTestGlobs,
		},
		{
			name: "ManualAutodetectPass_TestGlobsProvided",
			// Input
			runOnlySelectedTestsBool:  false,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: false,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "",
			stageStrategyIterations:   "",
			runnableTests:             []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:    true,
			runnerAutodetectTestsVal:  []types.RunnableTest{rts[0], rts[1]},
			runnerAutodetectTestsErr:  nil,
			testGlobsString:           testGlobsString,
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]},
			testGlobs:                testGlobs,
		},
		{
			name: "ManualAutodetectFailStepZero",
			// Input
			runOnlySelectedTestsBool:  false,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: false,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "",
			stageStrategyIterations:   "",
			runnableTests:             []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:    true,
			runnerAutodetectTestsVal:  []types.RunnableTest{},
			runnerAutodetectTestsErr:  fmt.Errorf("error in autodetection"),
			testGlobsString:           emptyTestGlobsString,
			// Expect
			runOnlySelectedTests:     false,
			selectTestsResponseTests: []types.RunnableTest{},
			testGlobs:                emptyTestGlobs,
		},
		{
			name: "ManualAutodetectFailStepNonZero",
			// Input
			runOnlySelectedTestsBool:  false,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: false,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "",
			stageStrategyIterations:   "",
			runnableTests:             []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:    true,
			runnerAutodetectTestsVal:  []types.RunnableTest{},
			runnerAutodetectTestsErr:  fmt.Errorf("error in autodetection"),
			testGlobsString:           emptyTestGlobsString,
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: make([]types.RunnableTest, 0),
			testGlobs:                emptyTestGlobs,
		},
		{
			name: "TestParallelism_StageParallelismOnly",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: false,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "",
			stageStrategyIterations:   "",
			runnableTests:             []types.RunnableTest{rts[0], rts[1]}, // t1, t2
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage 0, Step) - t1
		},
		{
			name: "TestParallelism_StepParallelismOnly",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  false,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "",
			stepStrategyIterations:    "",
			stageStrategyIteration:    "0",
			stageStrategyIterations:   "2",
			runnableTests:             []types.RunnableTest{rts[0], rts[1]}, // t1, t2
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage, Step 1) - t2
		},
		{
			name: "TestParallelism_StageStepParallelism_v1",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "0",
			stageStrategyIterations:   "2",
			runnableTests:             []types.RunnableTest{rts[0], rts[1], rts[2], rts[3]}, // t1, t2, t3, t4
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[1]}, // (Stage 0, Step 1) - t2
		},
		{
			name: "TestParallelism_StageStepParallelism_v2",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "1",
			stageStrategyIterations:   "2",
			runnableTests:             []types.RunnableTest{rts[0], rts[1], rts[2], rts[3]}, // t1, t2, t3, t4
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[3]}, // (Stage 1, Step 1) - t4
		},
		{
			name: "TestParallelism_StageStepParallelism_v30",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "0",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage 0, Step 0) - t1
		},
		{
			name: "TestParallelism_StageStepParallelism_v31",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "0",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[1]}, // (Stage 0, Step 1) - t2
		},
		{
			name: "TestParallelism_StageStepParallelism_v32",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "1",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[2]}, // (Stage 1, Step 0) - t3
		},
		{
			name: "TestParallelism_StageStepParallelism_v33",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "1",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[3]}, // (Stage 1, Step 1) - t4
		},
		{
			name: "TestParallelism_StageStepParallelism_v34",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "0",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "2",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[4]}, // (Stage 2, Step 0) - t5
		},
		{
			name: "TestParallelism_StageStepParallelism_v35",
			// Input
			runOnlySelectedTestsBool:  true,
			parallelizeTestsBool:      true,
			isParallelismEnabledBool:  true,
			isStepParallelismEnabled:  true,
			isStageParallelismEnabled: true,
			stepStrategyIteration:     "1",
			stepStrategyIterations:    "2",
			stageStrategyIteration:    "2",
			stageStrategyIterations:   "3",
			runnableTests:             rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[5]}, // (Stage 2, Step 1) - t5
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctrl, ctx := gomock.WithContext(context.Background(), t)
			defer ctrl.Finish()

			log, _ := logs.GetObservedLogger(zap.InfoLevel)
			runner := mocks.NewMockTestRunner(ctrl)
			if tt.runnerAutodetectExpect {
				runner.EXPECT().AutoDetectTests(ctx, tt.testGlobs).Return(tt.runnerAutodetectTestsVal, tt.runnerAutodetectTestsErr)
			}

			envMap := map[string]string{
				"HARNESS_STEP_INDEX":  tt.stepStrategyIteration,
				"HARNESS_STEP_TOTAL":  tt.stepStrategyIterations,
				"HARNESS_STAGE_INDEX": tt.stageStrategyIteration,
				"HARNESS_STAGE_TOTAL": tt.stageStrategyIterations,
			}

			r := runTestsTask{
				id:                   "id",
				runOnlySelectedTests: tt.runOnlySelectedTestsBool,
				preCommand:           "echo x",
				args:                 "test",
				postCommand:          "echo y",
				buildTool:            "maven",
				language:             "java",
				log:                  log.Sugar(),
				addonLogger:          log.Sugar(),
				testSplitStrategy:    countTestSplitStrategy,
				parallelizeTests:     tt.parallelizeTestsBool,
				testGlobs:            tt.testGlobsString,
				environment:          envMap,
			}
			selectTestsResponse := types.SelectTestsResp{}
			selectTestsResponse.Tests = tt.runnableTests

			r.computeSelectedTests(ctx, runner, &selectTestsResponse)
			assert.Equal(t, r.runOnlySelectedTests, tt.runOnlySelectedTests)
			assert.Equal(t, selectTestsResponse.Tests, tt.selectTestsResponseTests)
		})
	}
}

func TestGetCmd_ErrorIncorrectBuildTool(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	outputFile := "test.out"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	tmpFilePath := "/test/tmp"
	packages := "p1, p2, p3"

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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

func Test_GetSplitTests(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: false,
		preCommand:           "echo x",
		args:                 "test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
		testSplitStrategy:    countTestSplitStrategy,
		parallelizeTests:     false,
	}
	testsToSplit := []types.RunnableTest{
		{Pkg: "pkg1", Class: "cls1"},
		{Pkg: "pkg1", Class: "cls2"},
		{Pkg: "pkg2", Class: "cls1"},
		{Pkg: "pkg2", Class: "cls2"},
		{Pkg: "pkg3", Class: "cls1"},
	}
	splitStrategy := countTestSplitStrategy
	splitTotal := 3
	tests, _ := r.getSplitTests(ctx, testsToSplit, splitStrategy, 0, splitTotal)
	assert.Equal(t, len(tests), 2)
	tests, _ = r.getSplitTests(ctx, testsToSplit, splitStrategy, 1, splitTotal)
	assert.Equal(t, len(tests), 2)
	tests, _ = r.getSplitTests(ctx, testsToSplit, splitStrategy, 2, splitTotal)
	assert.Equal(t, len(tests), 1)
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
	parallelizeTests := true
	testSplitStrategy := defaultTestSplitStrategy
	testGlobsString := "path1/to/test*/*.cs,path2/to/test*/*.cs"

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
		ParallelizeTests:     true,
		TestGlobs:            testGlobsString,
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
	assert.Equal(t, task.parallelizeTests, parallelizeTests)
	assert.Equal(t, task.testSplitStrategy, testSplitStrategy)
	assert.Equal(t, task.testGlobs, testGlobsString)
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil).AnyTimes()
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil).AnyTimes()
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, start time.Time) error {
		called += 1
		return nil
	}

	// Mock test reports
	oldReports := collectTestReportsFn
	defer func() {
		collectTestReportsFn = oldReports
	}()
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, start time.Time) error {
		called += 1
		return errors.New("could not collect CG")
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
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
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
	assert.Equal(t, err, expErr) // make sure error returned is of RunCmd and not collectRunTestData
	assert.Equal(t, called, 2)   // make sure both functions are called even on failure
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
	expData := `outDir: /test/tmp/ti/callgraph/
logLevel: 0
logConsole: false
writeTo: COVERAGE_JSON
instrPackages: p1, p2, p3`
	fs.EXPECT().MkdirAll(expDir, os.ModePerm).Return(nil)
	mf := filesystem.NewMockFile(ctrl)
	mf.EXPECT().Write([]byte(expData)).Return(0, nil)
	fs.EXPECT().ReadFile(gomock.Any(), gomock.Any()).Return(nil)
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, start time.Time) error {
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
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
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

	expDir := filepath.Join(tmpFilePath, outDir) + "/"
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
	collectCgFn = func(ctx context.Context, stepID, collectcgDir string, timeTakenMs int64, log *zap.SugaredLogger, start time.Time) error {
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
	collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
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

func Test_FormatTests(t *testing.T) {
	tests := []types.RunnableTest{
		{
			Pkg:   "package",
			Class: "class",
		},
		{
			Pkg:   "package",
			Class: "class",
		},
		{
			Class: "dotnetClass",
		},
	}
	tests[0].Autodetect.Rule = "//bazel-rule:package.class"

	expectedTests := "package.class //bazel-rule:package.class, package.class, dotnetClass"
	formattedTest := formatTests(tests)
	assert.Equal(t, expectedTests, formattedTest)
}

func Test_CollectRunTestData(t *testing.T) {
	ctx := context.Background()
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	r := runTestsTask{
		id:                   "id",
		runOnlySelectedTests: true,
		preCommand:           "echo x",
		args:                 "clean test",
		postCommand:          "echo y",
		buildTool:            "maven",
		language:             "java",
		logMetrics:           false,
		numRetries:           1,
		log:                  log.Sugar(),
		addonLogger:          log.Sugar(),
	}
	cgDirPath := "cg/dir"

	tests := []struct {
		name          string
		cgErr         error
		crErr         error
		collectionErr error
	}{
		{
			name:          "NoError",
			cgErr:         nil,
			crErr:         nil,
			collectionErr: nil,
		},
		{
			name:          "CallgraphUploadError",
			cgErr:         fmt.Errorf("callgraph upload error"),
			crErr:         nil,
			collectionErr: fmt.Errorf("callgraph upload error"),
		},
		{
			name:          "TestReportsUploadError",
			cgErr:         nil,
			crErr:         fmt.Errorf("test reports upload error"),
			collectionErr: fmt.Errorf("test reports upload error"),
		},
	}

	oldCollectCgFn := collectCgFn
	defer func() { collectCgFn = oldCollectCgFn }()
	oldCollectTestReportsFn := collectTestReportsFn
	defer func() { collectTestReportsFn = oldCollectTestReportsFn }()

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			collectCgFn = func(ctx context.Context, stepID, cgDir string, timeMs int64, log *zap.SugaredLogger, start time.Time) error {
				return tc.cgErr
			}
			collectTestReportsFn = func(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
				return tc.crErr
			}
			err := r.collectRunTestData(ctx, cgDirPath, time.Now())
			assert.Equal(t, tc.collectionErr, err)
		})
	}
}
