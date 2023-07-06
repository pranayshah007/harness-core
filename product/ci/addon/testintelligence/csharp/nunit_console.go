// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Supports running tests via the nunit console test runner for C#
//
// Test filtering:
//
//	nunit3-console.exe <path-to-dll> --where "class =~ FirstTest || class =~ SecondTest"
package csharp

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

type nunitConsoleRunner struct {
	fs                filesystem.FileSystem
	log               *zap.SugaredLogger
	cmdContextFactory exec.CmdContextFactory
	agentPath         string
}

func NewNunitConsoleRunner(log *zap.SugaredLogger, fs filesystem.FileSystem, factory exec.CmdContextFactory, agentPath string) *nunitConsoleRunner {
	return &nunitConsoleRunner{
		fs:                fs,
		log:               log,
		cmdContextFactory: factory,
		agentPath:         agentPath,
	}
}

func (_ *nunitConsoleRunner) AutoDetectPackages() ([]string, error) {
	return []string{}, errors.New("not implemented")
}

func (b *nunitConsoleRunner) AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error) {
	return GetCsharpTests(testGlobs)
}

func (b *nunitConsoleRunner) GetCmd(_ context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error) {
	/*
		i) Get the DLL list from the command (assume it runs at the root of the repository)
		ii) Run the injector through all the DLLs
		iii) Add test filtering

		Working command:
			. nunit3-console.exe <path-to-dll> --where "class =~ FirstTest || class =~ SecondTest"
	*/
	var cmd string
	pathToInjector := filepath.Join(b.agentPath, "dotnet-agent", "dotnet-agent.injector.exe")

	// Run all the DLLs through the injector
	args := strings.Split(userArgs, " ")
	for _, s := range args {
		if strings.HasSuffix(s, ".dll") {
			absPath := s
			cmd += fmt.Sprintf(". %s %s %s\n", pathToInjector, absPath, agentConfigPath)
		}
	}

	if runAll {
		if ignoreInstr {
			return userArgs, nil
		}
		return fmt.Sprintf("%s %s", cmd, userArgs), nil
	}

	if len(tests) == 0 {
		return "echo \"Skipping test run, received no tests to execute\"", nil
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
			testStr += " || "
		}
		testStr += fmt.Sprintf("class =~ %s", t)
	}

	if ignoreInstr {
		return fmt.Sprintf("%s --where %q", userArgs, testStr), nil
	}
	return fmt.Sprintf("%s %s --where %q", cmd, userArgs, testStr), nil
}
