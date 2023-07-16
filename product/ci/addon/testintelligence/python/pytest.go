// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

/*
Package python
Any Python application that can run through the pytest CLI
should be able to use this to perform test intelligence.

Test filtering:
pytest test
*/
package python

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	pytestCmd  = "pytest"
	pythonCmd  = "python3"
	currentDir = "."
)

type pytestRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
	agentPath         string
}

func NewPytestRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory, agentPath string) *pytestRunner {
	return &pytestRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
		agentPath:         agentPath,
	}
}

func (b *pytestRunner) AutoDetectPackages() ([]string, error) {
	return []string{}, errors.New("not implemented")
}

func (b *pytestRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	return GetPythonTests(testGlobs)
}

func (b *pytestRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	// Run all the tests
	testCmd := ""
	scriptPath := filepath.Join(b.agentPath, "harness", "python-agent", "python_agent.py")
	userCmd := strings.TrimSpace(fmt.Sprintf("\"%s %s\"", pytestCmd, userArgs))
	if runAll {
		if ignoreInstr {
			return strings.TrimSpace(fmt.Sprintf("%s -m %s %s", pythonCmd, pytestCmd, userArgs)), nil
		}
		testCmd = strings.TrimSpace(fmt.Sprintf("%s %s %s --test_harness %s --config_file %s",
			pythonCmd, scriptPath, currentDir, userCmd, agentConfigPath))
		return testCmd, nil
	}
	if len(tests) == 0 {
		return "echo \"Skipping test run, received no tests to execute\"", nil
	}
	// Use only unique class
	set := make(map[types.RunnableTest]interface{})
	ut := []string{}
	for _, t := range tests {
		w := types.RunnableTest{Class: t.Class}
		if _, ok := set[w]; ok {
			// The test has already been added
			continue
		}
		set[w] = struct{}{}
		ut = append(ut, t.Class)
	}

	if ignoreInstr {
		testStr := strings.Join(ut, " ")
		return strings.TrimSpace(fmt.Sprintf("%s -m %s %s %s", pythonCmd, pytestCmd, testStr, userArgs)), nil
	}

	testStr := strings.Join(ut, ",")
	testCmd = fmt.Sprintf("%s %s %s --test_harness %s --test_files %s --config_file %s",
		pythonCmd, scriptPath, currentDir, userCmd, testStr, agentConfigPath)
	return testCmd, nil
}
