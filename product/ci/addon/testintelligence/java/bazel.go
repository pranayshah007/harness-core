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
	"github.com/harness/ti-client/types"
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

	c1 := fmt.Sprintf("%s query 'kind(java.*, tests(//...))'", bazelCmd)  // bazel query 'kind(java.*, tests(//...))'
	c2 := fmt.Sprintf("%s query 'kind(scala.*, tests(//...))'", bazelCmd) // bazel query 'kind(scala.*, tests(//...))'
	c3 := fmt.Sprintf("%s query 'kind(kt.*, tests(//...))'", bazelCmd)    // bazel query 'kind(kt.*, tests(//...))'
	for _, c := range []string{c1, c2, c3} {
		cmdArgs := []string{"-c", c}
		resp, err := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
		if err != nil {
			b.log.Errorw("Got an error while querying bazel", err)
			return tests, err
		}
		// Convert rules to RunnableTest list
		for _, r := range strings.Split(string(resp), "\n") {
			test, err := parseBazelTestRule(r)
			if err != nil {
				b.log.Errorw(fmt.Sprintf("Error parsing bazel test rule: %s", err))
				continue
			}
			tests = append(tests, test)
		}
	}
	return tests, nil
}

func getBazelTestRules(ctx context.Context, tests []types.RunnableTest, b *bazelRunner) []types.RunnableTest {
	var testList []types.RunnableTest
	var testStrings []string

	// Convert list of tests to "pkg1.cls1|pkg2.cls2|pkg3.cls3"
	testSet := map[string]bool{}
	for _, test := range tests {
		if test.Autodetect.Rule != "" {
			continue
		}
		testString := fmt.Sprintf("%s.%s", test.Pkg, test.Class)
		if _, ok := testSet[testString]; ok {
			continue
		}
		testSet[testString] = true
		testStrings = append(testStrings, testString)
	}
	if len(testStrings) == 0 {
		return tests
	}
	queryString := strings.Join(testStrings, "|")

	// bazel query 'attr(name, "pkg1.cls1|pkg2.cls2|pkg3.cls3", //...)'
	c := fmt.Sprintf("%s query 'attr(name, \"%s\", //...)'", bazelCmd, queryString)
	cmdArgs := []string{"-c", c}
	resp, err := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
	if err != nil {
		b.log.Errorf("Got an error while querying bazel %s", err)
		return tests
	}
	ruleString := strings.TrimSuffix(string(resp), "\n")

	// Map: {pkg1.cls1 : //rule:pkg1.cls1}
	testRuleMap := map[string]string{}
	for _, r := range strings.Split(ruleString, "\n") {
		test, err := parseBazelTestRule(r)
		if err != nil {
			b.log.Errorf("Failed to parse test rule: %s", err)
			continue
		}
		testId := fmt.Sprintf("%s.%s", test.Pkg, test.Class)
		if _, ok := testRuleMap[testId]; !ok {
			testRuleMap[testId] = r
		}
	}

	// Loop over all the tests and check if we were able to find the rule
	for _, test := range tests {
		testId := fmt.Sprintf("%s.%s", test.Pkg, test.Class)
		if _, ok := testRuleMap[testId]; ok && test.Autodetect.Rule == "" {
			test.Autodetect.Rule = testRuleMap[testId]
		}
		testList = append(testList, test)
	}
	b.log.Infof("Running tests with bazel rules: %s", testList)
	return testList
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

	// Populate the test rules in tests
	tests = getBazelTestRules(ctx, tests, b)

	// Use only unique classes
	rules := make([]string, 0) // List of unique bazel rules to be executed
	rulesSet := make(map[string]bool)
	classSet := make(map[string]bool)
	for _, test := range tests {
		pkg := test.Pkg
		cls := test.Class
		rule := test.Autodetect.Rule

		// Check if class has already been queried
		testId := fmt.Sprintf("%s.%s", pkg, cls)
		if _, ok := classSet[testId]; ok {
			continue
		}
		classSet[testId] = true

		// If the rule is present in the test, use it and skip querying bazel to get the rule
		if rule != "" {
			if _, ok := rulesSet[rule]; !ok {
				rules = append(rules, rule)
				rulesSet[rule] = true
			}
			continue
		}
		b.log.Errorw(fmt.Sprintf("could not find an appropriate rule for pkg %s and class %s, trying failback", pkg, cls))
		// Hack to get bazel rules for portal
		// TODO: figure out how to generically get rules to be executed from a package and a class
		// Example commands:
		//     find . -path "*pkg.class" -> can have multiple tests (eg helper/base tests)
		//     export fullname=$(bazelisk query path.java)
		//     bazelisk query "attr('srcs', $fullname, ${fullname//:*/}:*)" --output=label_kind | grep "java_test rule"
		// Get list of paths for the tests
		pathCmd := fmt.Sprintf(`find . -path '*%s/%s*' | sed -e "s/^\.\///g"`, strings.Replace(pkg, ".", "/", -1), cls)
		cmdArgs := []string{"-c", pathCmd}
		pathResp, pathErr := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
		if pathErr != nil {
			b.log.Errorw(fmt.Sprintf("could not find path for pkgs %s and class %s", pkg, cls), zap.Error(pathErr))
			continue
		}
		// Iterate over the paths and try to find the relevant rules
		for _, p := range strings.Split(string(pathResp), "\n") {
			p = strings.TrimSpace(p)
			if len(p) == 0 || !strings.Contains(p, "src/test") {
				continue
			}
			c := fmt.Sprintf("export fullname=$(%s query %s)\n"+
				"%s query \"attr('srcs', $fullname, ${fullname//:*/}:*)\" --output=label_kind | grep 'java_test rule'",
				bazelCmd, p, bazelCmd)
			cmdArgs = []string{"-c", c}
			resp2, err2 := b.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), "sh", cmdArgs...).Output()
			if err2 != nil || len(resp2) == 0 {
				b.log.Errorw(fmt.Sprintf("could not find an appropriate rule in failback for path %s", p), zap.Error(err2))
				continue
			}
			t := strings.Fields(string(resp2))
			resp := []byte(t[2])
			r := strings.TrimSuffix(string(resp), "\n")
			if _, ok := rulesSet[r]; !ok {
				rules = append(rules, r)
				rulesSet[r] = true
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
