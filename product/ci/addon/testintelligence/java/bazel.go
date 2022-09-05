// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
)

var (
	bazelCmd = "bazel"
)

type bazelRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
}

func NewBazelRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory) *bazelRunner {
	return &bazelRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
	}
}

func (b *bazelRunner) AutoDetectPackages() ([]string, error) {
	return DetectPkgs(b.log, b.fs)
}

func (b *bazelRunner) AutoDetectTestFiles(ctx context.Context) ([]types.RunnableTest, error) {
	return []types.RunnableTest{}, nil
}

func (b *bazelRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	if ignoreInstr {
		b.log.Infow("ignoring instrumentation and not attaching Java agent")
		return fmt.Sprintf("%s %s //...", bazelCmd, userArgs), nil
	}

	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := fmt.Sprintf("--define=HARNESS_ARGS=%s", agentArg)
	defaultCmd := fmt.Sprintf("%s %s %s //...", bazelCmd, userArgs, instrArg) // run all the tests

	if runAll {
		// Run all the tests
		return defaultCmd, nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}
	// Use only unique classes
	pkgs := []string{}
	clss := []string{}
	set := make(map[string]interface{})
	ut := []string{}
	for _, t := range tests {
		if _, ok := set[t.Class]; ok {
			// The class has already been added
			continue
		}
		set[t.Class] = struct{}{}
		ut = append(ut, t.Class)
		pkgs = append(pkgs, t.Pkg)
		clss = append(clss, t.Class)
	}
	rulesM := make(map[string]struct{})
	rules := []string{} // List of unique bazel rules to be executed
	for i := 0; i < len(pkgs); i++ {
		c := fmt.Sprintf("%s query 'attr(name, %s.%s, //...)'", bazelCmd, pkgs[i], clss[i])
		cmdArgs := []string{"-c", c}
		resp, err := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
		if err != nil || len(resp) == 0 {
			b.log.Errorw(fmt.Sprintf("could not find an appropriate rule for pkgs %s and class %s", pkgs[i], clss[i]),
				"index", i, "command", c, zap.Error(err))
			// Hack to get bazel rules for portal
			// TODO: figure out how to generically get rules to be executed from a package and a class
			// Example commands:
			//     find . -path "*pkg.class" -> can have multiple tests (eg helper/base tests)
			//     export fullname=$(bazelisk query path.java)
			//     bazelisk query "attr('srcs', $fullname, ${fullname//:*/}:*)" --output=label_kind | grep "java_test rule"

			// Get list of paths for the tests
			pathCmd := fmt.Sprintf(`find . -path '*%s/%s*' | sed -e "s/^\.\///g"`, strings.Replace(pkgs[i], ".", "/", -1), clss[i])
			cmdArgs = []string{"-c", pathCmd}
			pathResp, pathErr := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
			if pathErr != nil {
				b.log.Errorw(fmt.Sprintf("could not find path for pkgs %s and class %s", pkgs[i], clss[i]), zap.Error(pathErr))
				continue
			}
			// Iterate over the paths and try to find the relevant rules
			for _, p := range strings.Split(string(pathResp), "\n") {
				p = strings.TrimSpace(p)
				if len(p) == 0 || !strings.Contains(p, "src/test") {
					continue
				}
				c = fmt.Sprintf("export fullname=$(%s query %s)\n"+
					"%s query \"attr('srcs', $fullname, ${fullname//:*/}:*)\" --output=label_kind | grep 'java_test rule'",
					bazelCmd, p, bazelCmd)
				cmdArgs = []string{"-c", c}
				resp2, err2 := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
				if err2 != nil || len(resp2) == 0 {
					b.log.Errorw(fmt.Sprintf("could not find an appropriate rule in failback for path %s", p), zap.Error(err2))
					continue
				}
				t := strings.Fields(string(resp2))
				resp = []byte(t[2])
				r := strings.TrimSuffix(string(resp), "\n")
				if _, ok := rulesM[r]; !ok {
					rules = append(rules, r)
					rulesM[r] = struct{}{}
				}
			}
		} else {
			r := strings.TrimSuffix(string(resp), "\n")
			if _, ok := rulesM[r]; !ok {
				rules = append(rules, r)
				rulesM[r] = struct{}{}
			}
		}

	}
	if len(rules) == 0 {
		return fmt.Sprintf("echo \"Could not find any relevant test rules. Skipping the run\""), nil
	}
	testList := strings.Join(rules, " ")
	return fmt.Sprintf("%s %s %s %s", bazelCmd, userArgs, instrArg, testList), nil
}
