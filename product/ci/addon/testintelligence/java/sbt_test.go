// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"testing"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/ti-client/types"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestSBT_GetCmd(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)

	runner := NewSBTRunner(log.Sugar(), fs, cmdFactory)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1", Method: "m1"}
	t2 := types.RunnableTest{Pkg: "pkg2", Class: "cls2", Method: "m2"}
	javaOpts := "set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")"

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		ignoreInstr          bool
		want                 string
		expectedErr          bool
		tests                []types.RunnableTest
	}{
		{
			name:                 "RunAllTests_UserParams_AgentAttached",
			args:                 "-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("sbt -Duser.timezone=US/Mountain -Duser.locale=en/US '%s' 'test'", javaOpts),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunAllTests_AgentAttached",
			args:                 "clean test",
			runOnlySelectedTests: false,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("sbt clean test '%s' 'test'", javaOpts),
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("sbt clean test -Duser.timezone=US/Mountain -Duser.locale=en/US '%s' 'testOnly pkg1.cls1 pkg2.cls2'", javaOpts),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_Duplicate_UserParams_AgentAttached",
			args:                 "clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("sbt clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US '%s' 'testOnly pkg1.cls1 pkg2.cls2'", javaOpts),
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTests_UserParams_OrCondition_AgentAttached",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          false,
			want:                 fmt.Sprintf("sbt clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true '%s' 'testOnly pkg2.cls2'", javaOpts),
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
		{
			name:                 "RunAllTests_UserParams_AgentNotAttached",
			args:                 "-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "sbt -Duser.timezone=US/Mountain -Duser.locale=en/US 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunAllTests_AgentAttached",
			args:                 "clean test",
			runOnlySelectedTests: false,
			ignoreInstr:          true,
			want:                 "sbt clean test 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_UserParams_AgentNotAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "sbt clean test -Duser.timezone=US/Mountain -Duser.locale=en/US 'testOnly pkg1.cls1 pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "RunSelectedTests_ZeroTests_UserParams_AgentNotAttached",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "RunSelectedTests_TwoTests_Duplicate_UserParams_AgentNotAttached",
			args:                 "clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "sbt clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US 'testOnly pkg1.cls1 pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "RunSelectedTests_OneTests_UserParams_OrCondition_AgentNotAttached",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			ignoreInstr:          true,
			want:                 "sbt clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true 'testOnly pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
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
