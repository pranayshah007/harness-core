// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/ti-client/types"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"os"
	"testing"
)

func TestGetGradleCmd(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	fs.EXPECT().Stat("gradlew").Return(nil, nil).AnyTimes()

	runner := NewGradleRunner(log.Sugar(), fs, cmdFactory)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}
	agent := "-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini"

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		ignoreInstr          bool
		want                 string
		expectedErr          bool
		tests                []types.RunnableTest
	}{
		// PR run
		{
			name:                 "RunAllTests_AgentAttached",
			args:                 "test",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("./gradlew test -DHARNESS_JAVA_AGENT=%s", agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentAttached",
			args:                 "test -Duser.timezone=US/Mountain",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("./gradlew test -Duser.timezone=US/Mountain -DHARNESS_JAVA_AGENT=%s --tests \"pkg1.cls1\" --tests \"pkg2.cls2\"", agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests__Duplicate_UserParams_AgentAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US -DHARNESS_JAVA_AGENT=%s --tests \"pkg1.cls1\" --tests \"pkg2.cls2\"", agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_OrCondition_AgentAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US -DHARNESS_JAVA_AGENT=%s --tests \"pkg2.cls2\" || true", agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_MultipleOrCondition_AgentAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US || true || false || other",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US -DHARNESS_JAVA_AGENT=%s --tests \"pkg2.cls2\" || true || false || other", agent),
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
		// Ignore instrumentation true: Manual run or RunOnlySelectedTests task input is false
		{
			name:                 "RunAllTests_AgentNotAttached",
			args:                 "test",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "./gradlew test",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentNotAttached",
			args:                 "test -Duser.timezone=US/Mountain",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "./gradlew test -Duser.timezone=US/Mountain --tests \"pkg1.cls1\" --tests \"pkg2.cls2\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentNotAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests__Duplicate_UserParams_AgentNotAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US --tests \"pkg1.cls1\" --tests \"pkg2.cls2\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_OrCondition_AgentNotAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US --tests \"pkg2.cls2\" || true",
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
		{
			name:                 "RunSelectedTests_OneTest_UserParams_MultipleOrCondition_AgentNotAttached",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US || true || false || other",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "./gradlew test -Duser.timezone=US/Mountain -Duser.locale=en/US --tests \"pkg2.cls2\" || true || false || other",
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			got, err := runner.GetCmd(ctx, tc.tests, tc.args, "/test/tmp/config.ini", tc.ignoreInstr, !tc.runOnlySelectedTests)
			if tc.expectedErr == (err == nil) {
				t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
			}
			assert.Equal(t, got, tc.want, tc.name)
		})
	}
}

func TestGetGradleCmd_Manual(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	fs.EXPECT().Stat("gradlew").Return(nil, os.ErrNotExist).AnyTimes()

	runner := NewGradleRunner(log.Sugar(), fs, cmdFactory)

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		want                 string
		expectedErr          bool
		ignoreInstr          bool
		tests                []types.RunnableTest
	}{
		{
			name:                 "run all tests with empty test list and run only selected tests as false",
			args:                 "test -Duser.timezone=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "gradle test -Duser.timezone=en/US",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run selected tests with run only selected tests as true",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "gradle test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run all tests with empty test list and run only selected tests as false",
			args:                 "test -Duser.timezone=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 "gradle test -Duser.timezone=en/US -DHARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run selected tests with run only selected tests as true",
			args:                 "test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 "gradle test -Duser.timezone=US/Mountain -Duser.locale=en/US -DHARNESS_JAVA_AGENT=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
	}

	for _, tc := range tests {
		got, err := runner.GetCmd(ctx, tc.tests, tc.args, "/test/tmp/config.ini", tc.ignoreInstr, !tc.runOnlySelectedTests)
		if tc.expectedErr == (err == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
		assert.Equal(t, got, tc.want)
	}
}
