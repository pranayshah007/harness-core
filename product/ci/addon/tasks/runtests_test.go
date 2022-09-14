// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"bytes"
	"context"
	"fmt"
	"path/filepath"

	"encoding/json"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/exec"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/addon/testintelligence/mocks"
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

func TestComputeSelected_RunSelectedTests(t *testing.T) {
	rts := make([]types.RunnableTest, 0)
	for i := 1; i <= 12; i++ {
		rt := types.RunnableTest{
			Pkg:   fmt.Sprintf("p%d", i),
			Class: fmt.Sprintf("c%d", i),
		}
		rts = append(rts, rt)
	}
	tests := []struct {
		name string
		// Input
		runOnlySelectedTestsBool      bool
		IgnoreInstrBool               bool
		isParallelismEnabledBool      bool
		isStepParallelismEnabled      bool
		isStageParallelismEnabled     bool
		getStepStrategyIterationInt   int
		getStepStrategyIterationErr   error
		getStepStrategyIterationsInt  int
		getStepStrategyIterationsErr  error
		getStageStrategyIterationInt  int
		getStageStrategyIterationErr  error
		getStageStrategyIterationsInt int
		getStageStrategyIterationsErr error
		runnableTests                 []types.RunnableTest
		runnerAutodetectExpect        bool
		runnerAutodetectTestsVal      []types.RunnableTest
		runnerAutodetectTestsErr      error
		// Verify
		runOnlySelectedTests     bool
		selectTestsResponseTests []types.RunnableTest
		ignoreInstrResp          bool
	}{
		{
			name: "ManualAutodetectPass",
			// Input
			runOnlySelectedTestsBool:      false,
			IgnoreInstrBool:               true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     false,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  -1,
			getStageStrategyIterationErr:  fmt.Errorf("no stage parallelism"),
			getStageStrategyIterationsInt: -1,
			getStageStrategyIterationsErr: fmt.Errorf("no stage parallelism"),
			runnableTests:                 []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:        true,
			runnerAutodetectTestsVal:      []types.RunnableTest{rts[0], rts[1]},
			runnerAutodetectTestsErr:      nil,
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]},
			ignoreInstrResp:          false,
		},
		{
			name: "ManualAutodetectFailStepZero",
			// Input
			runOnlySelectedTestsBool:      false,
			IgnoreInstrBool:               true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     false,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  -1,
			getStageStrategyIterationErr:  fmt.Errorf("no stage parallelism"),
			getStageStrategyIterationsInt: -1,
			getStageStrategyIterationsErr: fmt.Errorf("no stage parallelism"),
			runnableTests:                 []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:        true,
			runnerAutodetectTestsVal:      []types.RunnableTest{},
			runnerAutodetectTestsErr:      fmt.Errorf("error in autodetection"),
			// Expect
			runOnlySelectedTests:     false,
			selectTestsResponseTests: []types.RunnableTest{},
			ignoreInstrResp:          true,
		},
		{
			name: "ManualAutodetectFailStepNonZero",
			// Input
			runOnlySelectedTestsBool:      false,
			IgnoreInstrBool:               true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     false,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  -1,
			getStageStrategyIterationErr:  fmt.Errorf("no stage parallelism"),
			getStageStrategyIterationsInt: -1,
			getStageStrategyIterationsErr: fmt.Errorf("no stage parallelism"),
			runnableTests:                 []types.RunnableTest{}, // Manual run - No TI test selection
			runnerAutodetectExpect:        true,
			runnerAutodetectTestsVal:      []types.RunnableTest{},
			runnerAutodetectTestsErr:      fmt.Errorf("error in autodetection"),
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: make([]types.RunnableTest, 0),
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageParallelismOnly",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     false,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  -1,
			getStageStrategyIterationErr:  fmt.Errorf("no stage parallelism"),
			getStageStrategyIterationsInt: -1,
			getStageStrategyIterationsErr: fmt.Errorf("no stage parallelism"),
			runnableTests:                 []types.RunnableTest{rts[0], rts[1]}, // t1, t2
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage 0, Step) - t1
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStepParallelismOnly",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      false,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   -1,
			getStepStrategyIterationErr:   fmt.Errorf("no step parallelism"),
			getStepStrategyIterationsInt:  -1,
			getStepStrategyIterationsErr:  fmt.Errorf("no step parallelism"),
			getStageStrategyIterationInt:  0,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 2,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 []types.RunnableTest{rts[0], rts[1]}, // t1, t2
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage, Step 1) - t2
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v1",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  0,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 2,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 []types.RunnableTest{rts[0], rts[1], rts[2], rts[3]}, // t1, t2, t3, t4
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[1]}, // (Stage 0, Step 1) - t2
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v2",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  1,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 2,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 []types.RunnableTest{rts[0], rts[1], rts[2], rts[3]}, // t1, t2, t3, t4
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[3]}, // (Stage 1, Step 1) - t4
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v30",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  0,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[0]}, // (Stage 0, Step 0) - t1
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v31",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  0,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[1]}, // (Stage 0, Step 1) - t2
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v32",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  1,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[2]}, // (Stage 1, Step 0) - t3
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v33",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  1,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[3]}, // (Stage 1, Step 1) - t4
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v34",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   0,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  2,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[4]}, // (Stage 2, Step 0) - t5
			ignoreInstrResp:          false,
		},
		{
			name: "TestStageParallelismStageStepParallelism_v35",
			// Input
			runOnlySelectedTestsBool:      true,
			isParallelismEnabledBool:      true,
			isStepParallelismEnabled:      true,
			isStageParallelismEnabled:     true,
			getStepStrategyIterationInt:   1,
			getStepStrategyIterationErr:   nil,
			getStepStrategyIterationsInt:  2,
			getStepStrategyIterationsErr:  nil,
			getStageStrategyIterationInt:  2,
			getStageStrategyIterationErr:  nil,
			getStageStrategyIterationsInt: 3,
			getStageStrategyIterationsErr: nil,
			runnableTests:                 rts[:6], // t1, t2, t3, t4, t5, t6
			// Expect
			runOnlySelectedTests:     true,
			selectTestsResponseTests: []types.RunnableTest{rts[5]}, // (Stage 2, Step 1) - t5
			ignoreInstrResp:          false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctrl, ctx := gomock.WithContext(context.Background(), t)
			defer ctrl.Finish()

			log, _ := logs.GetObservedLogger(zap.InfoLevel)
			runner := mocks.NewMockTestRunner(ctrl)
			if tt.runnerAutodetectExpect {
				runner.EXPECT().AutoDetectTests(ctx).Return(tt.runnerAutodetectTestsVal, tt.runnerAutodetectTestsErr)
			}

			oldGetStepStrategyIteration := getStepStrategyIteration
			oldGetStepStrategyIterations := getStepStrategyIterations
			oldGetStageStrategyIteration := getStageStrategyIteration
			oldGetStageStrategyIterations := getStageStrategyIterations
			oldIsParallelismEnabled := isParallelismEnabled
			oldIsStepParallelismEnabled := isStepParallelismEnabled
			oldIsStageParallelismEnabled := isStageParallelismEnabled
			defer func() {
				getStepStrategyIteration = oldGetStepStrategyIteration
				getStepStrategyIterations = oldGetStepStrategyIterations
				getStageStrategyIteration = oldGetStageStrategyIteration
				getStageStrategyIterations = oldGetStageStrategyIterations
				isParallelismEnabled = oldIsParallelismEnabled
				isStepParallelismEnabled = oldIsStepParallelismEnabled
				isStageParallelismEnabled = oldIsStageParallelismEnabled
			}()
			isParallelismEnabled = func() bool {
				return tt.isParallelismEnabledBool
			}
			isStepParallelismEnabled = func() bool {
				return tt.isStepParallelismEnabled
			}
			isStageParallelismEnabled = func() bool {
				return tt.isStageParallelismEnabled
			}
			getStepStrategyIteration = func() (int, error) {
				return tt.getStepStrategyIterationInt, tt.getStepStrategyIterationErr
			}
			getStepStrategyIterations = func() (int, error) {
				return tt.getStepStrategyIterationsInt, tt.getStepStrategyIterationsErr
			}
			getStageStrategyIteration = func() (int, error) {
				return tt.getStageStrategyIterationInt, tt.getStageStrategyIterationErr
			}
			getStageStrategyIterations = func() (int, error) {
				return tt.getStageStrategyIterationsInt, tt.getStageStrategyIterationsErr
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
				parallelizeTests:     true,
			}
			ignoreInstr := true
			selectTestsResponse := types.SelectTestsResp{}
			selectTestsResponse.Tests = tt.runnableTests

			ignoreInstrResp := r.computeSelectedTests(ctx, runner, &selectTestsResponse, ignoreInstr)
			fmt.Println(tt.name, selectTestsResponse.Tests)
			assert.Equal(t, r.runOnlySelectedTests, tt.runOnlySelectedTests)
			assert.Equal(t, selectTestsResponse.Tests, tt.selectTestsResponseTests)
			assert.Equal(t, ignoreInstrResp, tt.ignoreInstrResp)
		})
	}
}

func TestComputeSelectedTests_Skip(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	runner := mocks.NewMockTestRunner(ctrl)
	t1 := types.RunnableTest{
		Pkg:   "p1",
		Class: "c1",
	}
	t2 := types.RunnableTest{
		Pkg:   "p2",
		Class: "c2",
	}

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
	}
	ignoreInstr := true
	selectTestsResponse := types.SelectTestsResp{}
	selectTestsResponse.Tests = append(selectTestsResponse.Tests, t1, t2)

	ignoreInstrResp := r.computeSelectedTests(ctx, runner, &selectTestsResponse, ignoreInstr)
	assert.Equal(t, r.runOnlySelectedTests, false)
	assert.Equal(t, len(selectTestsResponse.Tests), 2)
	assert.Equal(t, ignoreInstrResp, ignoreInstr)
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, cgSt time.Time) error {
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, cgSt time.Time) error {
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
	collectCgFn = func(ctx context.Context, stepID, collectDataDir string, timeTakenMs int64, log *zap.SugaredLogger, cgSt time.Time) error {
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
	collectCgFn = func(ctx context.Context, stepID, collectcgDir string, timeTakenMs int64, log *zap.SugaredLogger, cgSt time.Time) error {
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
