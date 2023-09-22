// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

/*
Package python
Any Python application that can run through the rspec CLI
should be able to use this to perform test intelligence.

Test filtering:
rspec test
*/
package ruby

import (
	"context"
	"errors"
	"fmt"
	"path/filepath"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	rspecCmd = "bundle exec rspec"
)

type rspecRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
	agentPath         string
}

func NewRspecRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory, agentPath string) *rspecRunner {
	return &rspecRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
		agentPath:         agentPath,
	}
}

func (b *rspecRunner) AutoDetectPackages() ([]string, error) {
	return []string{}, errors.New("not implemented")
}

func (b *rspecRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	if len(testGlobs) == 0 {
		testGlobs = utils.RUBY_TEST_PATTERN
	}
	return utils.GetTestsFromLocal(testGlobs, "rb", utils.LangType_RUBY)
}

func (b *rspecRunner) ReadPackages(files []types.File) []types.File {
	return files
}

func (b *rspecRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	testCmd := ""
	tiFlag := "TI=1"
	repoPath := filepath.Join(b.agentPath, "harness", "ruby-agent")
	if !ignoreInstr {
	    err := WriteGemFile(repoPath)
	}
	if err != nil {
		return testCmd, err
	}
	// Run all the tests
	if runAll {
		if ignoreInstr {
			return strings.TrimSpace(fmt.Sprintf("%s %s", rspecCmd, userArgs)), nil
		}
		testCmd = strings.TrimSpace(fmt.Sprintf("%s %s %s ",
			tiFlag, rspecCmd, userArgs))
		return testCmd, nil
	}

	if len(tests) == 0 {
		return "echo \"Skipping test run, received no tests to execute\"", nil
	}

	ut := utils.GetUniqueTestStrings(tests)
	testStr := strings.Join(ut, " ")

	if ignoreInstr {
		return strings.TrimSpace(fmt.Sprintf("%s %s %s", rspecCmd, userArgs, testStr)), nil
	}

	testCmd = fmt.Sprintf("%s %s %s %s",
		tiFlag, rspecCmd, userArgs, testStr)
	return testCmd, nil
}
