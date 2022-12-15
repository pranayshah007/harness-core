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
	bazelCmd         = "bazel"
	bazelRuleSepList = []string{".", "/"}
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

func (b *bazelRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)

	// bazel query 'kind(java.*, tests(//...))'
	c := fmt.Sprintf("%s query 'kind(java.*, tests(//...))'", bazelCmd)
	cmdArgs := []string{"-c", c}
	resp, err := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
	if err != nil {
		b.log.Errorw("Got an error while querying bazel", err)
		return tests, err
	}
	// Convert rules to RunnableTest list
	var test types.RunnableTest
	for _, r := range strings.Split(string(resp), "\n") {
		if r == "" {
			continue
		}
		if !strings.Contains(r, ":") || len(strings.Split(r, ":")) < 2 {
			b.log.Errorw(fmt.Sprintf("Rule does not follow the default format: %s", r))
			continue
		}
		fullPkg := strings.Split(r, ":")[1]
		for _, s := range bazelRuleSepList {
			fullPkg = strings.Replace(fullPkg, s, ".", -1)
		}
		pkgList := strings.Split(fullPkg, ".")
		if len(pkgList) < 2 {
			b.log.Errorw(fmt.Sprintf("Rule does not follow the default format: %s", r))
			continue
		}
		cls := pkgList[len(pkgList)-1]
		pkg := strings.TrimSuffix(fullPkg, "."+cls)
		test = types.RunnableTest{Pkg: pkg, Class: cls}
		test.Autodetect.Rule = r
		tests = append(tests, test)
	}
	return tests, nil
}

func (b *bazelRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)
	instrArg := fmt.Sprintf("--define=HARNESS_ARGS=%s", agentArg)

	// Run all the tests
	if runAll {
		if ignoreInstr {
			return fmt.Sprintf("%s %s //...", bazelCmd, userArgs), nil
		}
		return fmt.Sprintf("%s %s %s //...", bazelCmd, userArgs, instrArg), nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}

	// Use only unique classes
	pkgs := []string{}
	clss := []string{}
	ut := []string{}
	rls := []string{}
	for _, t := range tests {
		ut = append(ut, t.Class)
		pkgs = append(pkgs, t.Pkg)
		clss = append(clss, t.Class)
		rls = append(rls, t.Autodetect.Rule)
	}
	rulesM := make(map[string]struct{})
	rules := []string{} // List of unique bazel rules to be executed
	set := make(map[string]interface{})
	for i := 0; i < len(pkgs); i++ {
		// If the rule is present in the test, use it and skip querying bazel to get the rule
		if rls[i] != "" {
			rules = append(rules, rls[i])
			continue
		}
		if _, ok := set[clss[i]]; ok {
			// The class has already been queried
			continue
		}
		set[clss[i]] = struct{}{}
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
	if ignoreInstr {
		return fmt.Sprintf("%s %s %s", bazelCmd, userArgs, testList), nil
	}
	return fmt.Sprintf("%s %s %s %s", bazelCmd, userArgs, instrArg, testList), nil
}
