// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/ti-client/types"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestGetBazelCmd(t *testing.T) {
	// Bazel impl is pretty hacky right now and tailored to running portal.
	// Will add this once we have a more generic implementation.
}

func TestGetBazelCmd_DuplicateTests(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)
	runner := NewBazelRunner(log.Sugar(), fs, cmdFactory)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1"}
	t2 := types.RunnableTest{Pkg: "pkg1", Class: "cls1"}
	t3 := types.RunnableTest{Pkg: "pkg2", Class: "cls2"}
	tests := []types.RunnableTest{t1, t2, t3}
	expectedCmd := "bazel  --define=HARNESS_ARGS=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini //module1:pkg1.cls1 //module1:pkg2.cls2"

	c := fmt.Sprintf("%s query 'attr(name, \"pkg1.cls1|pkg2.cls2\", //...)'", bazelCmd)
	cmdArgs := append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte("//module1:pkg1.cls1\n//module1:pkg2.cls2\n"), nil)

	command, _ := runner.GetCmd(ctx, tests, "", "/test/tmp/config.ini", false, false)
	assert.Equal(t, expectedCmd, command)
}

func TestGetBazelCmd_TestsWithRules(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	runner := NewBazelRunner(log.Sugar(), fs, cmdFactory)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1"}
	t1.Autodetect.Rule = "//module1:pkg1.cls1"
	t2 := types.RunnableTest{Pkg: "pkg1", Class: "cls2"}
	t2.Autodetect.Rule = "//module1:pkg1.cls2"
	t3 := types.RunnableTest{Pkg: "pkg2", Class: "cls2"}
	t3.Autodetect.Rule = "//module1:pkg2.cls2"
	tests := []types.RunnableTest{t1, t2, t3}
	expectedCmd := "bazel  --define=HARNESS_ARGS=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini //module1:pkg1.cls1 //module1:pkg1.cls2 //module1:pkg2.cls2"

	// Since all the tests have test rules, there won't be an additional bazel query call to find the test rules
	cmd, _ := runner.GetCmd(ctx, tests, "", "/test/tmp/config.ini", false, false)
	assert.Equal(t, expectedCmd, cmd)
}

func TestGetBazelCmd_GetBazelTestRules(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)
	runner := NewBazelRunner(log.Sugar(), fs, cmdFactory)

	tests := []types.RunnableTest{
		{
			Pkg:   "io.harness",
			Class: "GraphQLExceptionHandlingTest",
		},
		{
			Pkg:   "io.harness",
			Class: "GenerateOpenApiSpecCommandTest",
		},
		{
			Pkg:   "io.harness.ng",
			Class: "GenerateOpenApiSpecCommandTest",
		},
		{
			Pkg:   "io.harness.mongo",
			Class: "MongoIndexesTest",
		},
	}
	expectedCmd := "bazel  --define=HARNESS_ARGS=-javaagent:/addon/bin/java-agent.jar=/test/tmp/config.ini //220-graphql-test:io.harness.GraphQLExceptionHandlingTest //pipeline-service/service:io.harness.GenerateOpenApiSpecCommandTest //120-ng-manager:io.harness.ng.GenerateOpenApiSpecCommandTest //400-rest:io.harness.mongo.tests"

	c := fmt.Sprintf("%s query 'attr(name, \"io.harness.GraphQLExceptionHandlingTest|io.harness.GenerateOpenApiSpecCommandTest|io.harness.ng.GenerateOpenApiSpecCommandTest|io.harness.mongo.MongoIndexesTest\", //...)'", bazelCmd)
	cmdArgs := append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte("//120-ng-manager:io.harness.ng.GenerateOpenApiSpecCommandTest\n//220-graphql-test:io.harness.GraphQLExceptionHandlingTest\n//pipeline-service/service:io.harness.GenerateOpenApiSpecCommandTest\n"), nil)

	// No test rule found for MongoIndexesTest - Fail back query
	c = fmt.Sprintf("find . -path '*io/harness/mongo/MongoIndexesTest*' | sed -e \"s/^\\.\\///g\"")
	cmdArgs = append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte("400-rest/src/test/java/io/harness/mongo/MongoIndexesTest.java"), nil)

	c = fmt.Sprintf("export fullname=$(%s query 400-rest/src/test/java/io/harness/mongo/MongoIndexesTest.java)\n%s query \"attr('srcs', $fullname, ${fullname//:*/}:*)\" --output=label_kind | grep 'java_test rule'", bazelCmd, bazelCmd)
	cmdArgs = append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte("java_test rule //400-rest:io.harness.mongo.tests"), nil)

	command, _ := runner.GetCmd(ctx, tests, "", "/test/tmp/config.ini", false, false)
	assert.Equal(t, expectedCmd, command)
}

func TestBazelAutoDetectTests(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)
	runner := NewBazelRunner(log.Sugar(), fs, cmdFactory)

	// bazel query output
	q1 := "//module1:pkg1.cls1"
	q2 := "//module1:pkg1.cls2"
	q3 := "//module1:pkg2"
	q4 := "//module1:pkg2/cls2"
	bazelRuleStrings := fmt.Sprintf("%s\n%s\n%s\n%s\n", q1, q2, q3, q4)

	t1 := types.RunnableTest{Pkg: "pkg1", Class: "cls1"}
	t1.Autodetect.Rule = q1
	t2 := types.RunnableTest{Pkg: "pkg1", Class: "cls2"}
	t2.Autodetect.Rule = q2
	// t3 is invalid
	t4 := types.RunnableTest{Pkg: "pkg2", Class: "cls2"}
	t4.Autodetect.Rule = q4
	testsExpected := []types.RunnableTest{t1, t2, t4}

	c := fmt.Sprintf("%s query 'kind(java.*, tests(//...))'", bazelCmd)
	cmdArgs := append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte(bazelRuleStrings), nil)

	c = fmt.Sprintf("%s query 'kind(scala.*, tests(//...))'", bazelCmd)
	cmdArgs = append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte(""), nil)

	c = fmt.Sprintf("%s query 'kind(kt.*, tests(//...))'", bazelCmd)
	cmdArgs = append(make([]interface{}, 0), "-c", c)
	cmdFactory.EXPECT().CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Return(cmd)
	cmd.EXPECT().Output().Return([]byte(""), nil)

	tests, _ := runner.AutoDetectTests(ctx, []string{})
	assert.Equal(t, testsExpected, tests)
}
