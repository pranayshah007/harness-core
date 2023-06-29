// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	sbtCmd = "sbt"
)

type sbtRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
}

func NewSBTRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory) *sbtRunner {
	return &sbtRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
	}
}

func (b *sbtRunner) AutoDetectPackages() ([]string, error) {
	return DetectPkgs(b.log, b.fs)
}

func (b *sbtRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	javaTests, err := GetJavaTests(testGlobs)
	if err != nil {
		return tests, err
	}
	scalaTests, err := GetScalaTests(testGlobs)
	if err != nil {
		return tests, err
	}
	tests = append(tests, javaTests...)
	tests = append(tests, scalaTests...)
	return tests, nil
}

func (b *sbtRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	// Agent arg
	inputUserArgs := userArgs
	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := fmt.Sprintf("'set javaOptions ++= Seq(\"%s\")'", agentArg)

	// Run all the tests
	if runAll {
		if ignoreInstr {
			return fmt.Sprintf("%s %s 'test'", sbtCmd, inputUserArgs), nil
		}
		return fmt.Sprintf("%s %s %s 'test'", sbtCmd, userArgs, instrArg), nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}

	// Use only unique classes
	testsList := []string{}
	set := make(map[string]interface{})
	for _, t := range tests {
		if _, ok := set[t.Class]; ok {
			// The class has already been added
			continue
		}
		set[t.Class] = struct{}{}
		testsList = append(testsList, t.Pkg+"."+t.Class)
	}

	if ignoreInstr {
		return fmt.Sprintf("%s %s 'testOnly %s'", sbtCmd, userArgs, strings.Join(testsList, " ")), nil
	}
	return fmt.Sprintf("%s %s %s 'testOnly %s'", sbtCmd, userArgs, instrArg, strings.Join(testsList, " ")), nil
}
