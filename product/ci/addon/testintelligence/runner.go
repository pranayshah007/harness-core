// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// TestRunner provides the test command and java agent config
// which is needed to run test intelligence.
package testintelligence

import (
	"context"

	"github.com/harness/ti-client/types"
)

//go:generate mockgen -source runner.go -package=testintelligence -destination mocks/runner_mock.go TestRunner
type TestRunner interface {
	// GetCmd gets the command which needs to be executed to run only the specified tests.
	// tests: list of selected tests which need to be executed
	// agentConfigPath: path to the java agent config. This needs to be added to the
	// command if instrumentation is required.
	// ignoreInstr: instrumentation might not be required in some cases like manual executions
	// runAll: if there was any issue in figuring out which tests to run, this parameter is set as true
	GetCmd(ctx context.Context, tests []types.RunnableTest, userArgs, agentConfigPath string, ignoreInstr, runAll bool) (string, error)

	// AutoDetectPackages detects the list of packages to be instrumented.
	// Return an error if we could not detect or if it's unimplemented.
	AutoDetectPackages() ([]string, error)

	// AutoDetectTests detects the list of tests in the workspace
	// Return an error if we could not detect or if it's unimplemented
	AutoDetectTests(ctx context.Context, testGlobs []string) ([]types.RunnableTest, error)
}
