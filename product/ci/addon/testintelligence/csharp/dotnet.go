// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

/*
Any C# application that can run through the dotnet CLI
should be able to use this to perform test intelligence.

Test filtering:
dotnet test --filter "FullyQualifiedName~Namespace.Class|FullyQualifiedName~Namespace2.Class2..."
*/
package csharp

import (
	"context"
	"errors"
	"fmt"
	"path"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/product/ci/ti-service/types"

	"go.uber.org/zap"
)

var (
	dotnetCmd = "dotnet"
)

type dotnetRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
	agentPath         string
}

func NewDotnetRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory, agentPath string) *dotnetRunner {
	return &dotnetRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
		agentPath:         agentPath,
	}
}

func (b *dotnetRunner) AutoDetectPackages() ([]string, error) {
	return []string{}, errors.New("not implemented")
}

func (b *dotnetRunner) GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	defaultSetupCmd := fmt.Sprintf("%s %s", dotnetCmd, userArgs)
	defaultRunCmd := fmt.Sprintf("%s test --no-build --logger \"junit;LogFilePath=test_results.xml\"", dotnetCmd)
	defaultCmd := fmt.Sprintf("%s; %s", defaultSetupCmd, defaultRunCmd)
	agentFullName := path.Join(b.agentPath, "dotnet-agent.injector.dll")
	instrumentCmd := fmt.Sprintf("%s %s %s %s", dotnetCmd, agentFullName, userArgs, agentConfigPath)
	if ignoreInstr {
		b.log.Infow("ignoring instrumentation and not attaching agent")
		return defaultCmd, nil
	}

	if runAll {
		return fmt.Sprintf("%s; %s", instrumentCmd, defaultRunCmd), nil // Add instrumentation here
	}

	// Need to handle this for Windows as well
	if len(tests) == 0 {
		return fmt.Sprintf("echo \"Skipping test run, received no tests to execute\""), nil
	}

	// Use only unique <pkg, class> tuples (pkg is same as namespace for .Net)
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
	var testStr string
	for idx, t := range ut {
		if idx != 0 {
			testStr += "|"
		}
		testStr += "FullyQualifiedName~" + t
	}
	// dotnet /dotnet-agent.injector.dll /TestProject1.dll ./Config.yaml
	runtestCmd := fmt.Sprintf("%s test --no-build --logger \"junit;LogFilePath=test_results.xml\" --filter \"%s\"", dotnetCmd, testStr)
	return fmt.Sprintf("%s; %s", instrumentCmd, runtestCmd), nil
}
