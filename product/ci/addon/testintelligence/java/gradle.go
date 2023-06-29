// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	gradleWrapperCmd = "./gradlew"
	gradleCmd        = "gradle"
)

type gradleRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
}

func NewGradleRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory) *gradleRunner {
	return &gradleRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
	}
}

func (g *gradleRunner) AutoDetectPackages() ([]string, error) {
	return DetectPkgs(g.log, g.fs)
}

func (g *gradleRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
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

/*
The following needs to be added to a build.gradle to make it compatible with test intelligence:
// This adds HARNESS_JAVA_AGENT to the testing command if it's provided through the command line.
// Local builds will still remain same as it only adds if the parameter is provided.

	tasks.withType(Test) {
	  if(System.getProperty("HARNESS_JAVA_AGENT")) {
	    jvmArgs += [System.getProperty("HARNESS_JAVA_AGENT")]
	  }
	}

// This makes sure that any test tasks for subprojects don't fail in case the test filter does not match
// with any tests. This is needed since we want to search for a filter in all subprojects without failing if
// the filter does not match with any of the subprojects.

	gradle.projectsEvaluated {
	        tasks.withType(Test) {
	            filter {
	                setFailOnNoMatchingTests(false)
	            }
	        }
	}
*/
func (g *gradleRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	// Check if gradlew exists. If not, fallback to gradle
	gc := gradleWrapperCmd
	_, err := g.fs.Stat("gradlew")
	if errors.Is(err, os.ErrNotExist) {
		gc = gradleCmd
	}

	// Agent arg
	var orCmd string
	inputUserArgs := userArgs
	if strings.Contains(userArgs, "||") {
		// args = "test || orCond1 || orCond2" gets split as:
		// [test, orCond1 || orCond2]
		s := strings.SplitN(userArgs, "||", 2)
		orCmd = s[1]
		userArgs = s[0]
	}
	userArgs = strings.TrimSpace(userArgs)
	if orCmd != "" {
		orCmd = "|| " + strings.TrimSpace(orCmd)
	}
	agentArg := fmt.Sprintf(javaAgentArg, agentConfigPath)

	// Run all the tests
	if runAll {
		if ignoreInstr {
			return strings.TrimSpace(fmt.Sprintf("%s %s", gc, inputUserArgs)), nil
		}
		return strings.TrimSpace(fmt.Sprintf("%s %s -DHARNESS_JAVA_AGENT=%s %s", gc, userArgs, agentArg, orCmd)), nil
	}
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}

	// Use only unique <package, class> tuples
	set := make(map[types.RunnableTest]interface{})
	var testStr string
	for _, t := range tests {
		w := types.RunnableTest{Pkg: t.Pkg, Class: t.Class}
		if _, ok := set[w]; ok {
			// The test has already been added
			continue
		}
		set[w] = struct{}{}
		testStr = testStr + " --tests " + fmt.Sprintf("\"%s.%s\"", t.Pkg, t.Class)
	}

	if ignoreInstr {
		return strings.TrimSpace(fmt.Sprintf("%s %s%s %s", gc, userArgs, testStr, orCmd)), nil
	}
	return strings.TrimSpace(fmt.Sprintf("%s %s -DHARNESS_JAVA_AGENT=%s%s %s", gc, userArgs, agentArg, testStr, orCmd)), nil
}
