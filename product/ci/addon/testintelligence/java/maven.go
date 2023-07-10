// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"regexp"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	mavenCmd = "mvn"
)

type mavenRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
}

func NewMavenRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory) *mavenRunner {
	return &mavenRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
	}
}

func (m *mavenRunner) AutoDetectPackages() ([]string, error) {
	return DetectPkgs(m.log, m.fs)
}

func (m *mavenRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	javaTests, err := GetJavaTests(testGlobs)
	if err != nil {
		return tests, err
	}
	scalaTests, err := GetScalaTests(testGlobs)
	if err != nil {
		return tests, err
	}
	kotlinTests, err := GetKotlinTests(testGlobs)
	if err != nil {
		return tests, err
	}
	tests = append(tests, javaTests...)
	tests = append(tests, scalaTests...)
	tests = append(tests, kotlinTests...)
	return tests, nil
}

func (m *mavenRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	// If instrumentation needs to be ignored, we run all the tests without adding the agent config
	inputUserArgs := userArgs
	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := agentArg
	re := regexp.MustCompile(`(-Duser\.\S*)`)
	s := re.FindAllString(userArgs, -1)
	if s != nil {
		// If user args are present, move them to instrumentation
		userArgs = re.ReplaceAllString(userArgs, "")                        // Remove from arg
		instrArg = fmt.Sprintf("\"%s %s\"", strings.Join(s, " "), agentArg) // Add to instrumentation
	}
	// Run all the tests
	if runAll {
		if ignoreInstr {
			return strings.TrimSpace(fmt.Sprintf("%s %s", mavenCmd, inputUserArgs)), nil
		}
		return strings.TrimSpace(fmt.Sprintf("%s -am -DharnessArgLine=%s -DargLine=%s %s", mavenCmd, instrArg, instrArg, userArgs)), nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}
	// Use only unique <package, class> tuples
	set := make(map[types.RunnableTest]interface{})
	ut := []string{}
	for _, t := range tests {
		w := types.RunnableTest{Pkg: t.Pkg, Class: t.Class}
		if _, ok := set[w]; ok {
			// The test has already been added
			continue
		}
		set[w] = struct{}{}
		if t.Pkg != "" {
			ut = append(ut, t.Pkg+"."+t.Class) // We should always have a package name. If not, use class to run
		} else {
			ut = append(ut, t.Class)
		}
	}
	testStr := strings.Join(ut, ",")

	if ignoreInstr {
		return strings.TrimSpace(fmt.Sprintf("%s -Dtest=%s %s", mavenCmd, testStr, inputUserArgs)), nil
	}
	return strings.TrimSpace(fmt.Sprintf("%s -Dtest=%s -am -DharnessArgLine=%s -DargLine=%s %s", mavenCmd, testStr, instrArg, instrArg, userArgs)), nil
}
