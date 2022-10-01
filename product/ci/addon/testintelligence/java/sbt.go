// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
	"strings"
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

func (m *sbtRunner) AutoDetectTests(ctx context.Context) ([]types.RunnableTest, error) {
	return []types.RunnableTest{}, nil
}

func (b *sbtRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	if ignoreInstr {
		b.log.Infow("ignoring instrumentation and not attaching Java agent")
		return fmt.Sprintf("%s %s 'test'", sbtCmd, userArgs), nil
	}

	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := fmt.Sprintf("'set javaOptions ++= Seq(\"%s\")'", agentArg)
	defaultCmd := fmt.Sprintf("%s %s %s 'test'", sbtCmd, userArgs, instrArg) // run all the tests

	if runAll {
		// Run all the tests
		return defaultCmd, nil
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
		testsList = append(testsList, t.Pkg + "."+ t.Class)
	}
	return fmt.Sprintf("%s %s %s 'testOnly %s'", sbtCmd, userArgs, instrArg, strings.Join(testsList, " ")), nil
}
