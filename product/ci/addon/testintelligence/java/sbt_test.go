// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"testing"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/ti-service/types"
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

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		want                 string
		expectedErr          bool
		tests                []types.RunnableTest
	}{
		{
			name:                 "run all tests with non-empty test list and -Duser parameters",
			args:                 "-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: false,
			want:                 "sbt -Duser.timezone=US/Mountain -Duser.locale=en/US 'set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")' 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "run all tests with empty test list and no -Duser parameters",
			args:                 "clean test",
			runOnlySelectedTests: false,
			want:                 "sbt clean test 'set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")' 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run selected tests with given test list and -Duser parameters",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			want:                 "sbt clean test -Duser.timezone=US/Mountain -Duser.locale=en/US 'set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")' 'testOnly pkg1.cls1 pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2},
		},
		{
			name:                 "run selected tests with zero tests and -Duser parameters",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			want:                 "echo \"Skipping test run, received no tests to execute\"",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run selected tests with repeating test list and -Duser parameters",
			args:                 "clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			want:                 "sbt clean test -B -2C-Duser.timezone=US/Mountain -Duser.locale=en/US 'set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")' 'testOnly pkg1.cls1 pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t1, t2, t1, t2},
		},
		{
			name:                 "run selected tests with single test and -Duser parameters and or condition",
			args:                 "clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true",
			runOnlySelectedTests: true,
			want:                 "sbt clean test -B -2C -Duser.timezone=US/Mountain -Duser.locale=en/US || true 'set javaOptions ++= Seq(\"-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini\")' 'testOnly pkg2.cls2'",
			expectedErr:          false,
			tests:                []types.RunnableTest{t2},
		},
	}

	for _, tc := range tests {
		got, err := runner.GetCmd(ctx, tc.tests, tc.args, "/test/tmp/config.ini", false, !tc.runOnlySelectedTests)
		if tc.expectedErr == (err == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
		assert.Equal(t, got, tc.want)
	}
}

func TestGetSBTCmd_Manual(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)

	runner := NewSBTRunner(log.Sugar(), fs, cmdFactory)

	tests := []struct {
		name                 string // description of test
		args                 string
		runOnlySelectedTests bool
		want                 string
		expectedErr          bool
		tests                []types.RunnableTest
	}{
		{
			name:                 "run all tests with empty test list and no -Duser parameters",
			args:                 "clean",
			runOnlySelectedTests: false,
			want:                 "sbt clean 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
		{
			name:                 "run selected tests with zero tests and -Duser parameters",
			args:                 "clean test -Duser.timezone=US/Mountain -Duser.locale=en/US",
			runOnlySelectedTests: true,
			want:                 "sbt clean test -Duser.timezone=US/Mountain -Duser.locale=en/US 'test'",
			expectedErr:          false,
			tests:                []types.RunnableTest{},
		},
	}

	for _, tc := range tests {
		got, err := runner.GetCmd(ctx, tc.tests, tc.args, "/test/tmp/config.ini", true, !tc.runOnlySelectedTests)
		if tc.expectedErr == (err == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
		assert.Equal(t, got, tc.want)
	}
}
