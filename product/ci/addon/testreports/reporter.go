// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package testreports

import (
	"context"

	"github.com/harness/ti-client/types"
)

// TestReporter is any interface which can send us tests in our custom format
//
//go:generate mockgen -source reporter.go -package=testreports -destination mocks/reporter.go TestReporter
type TestReporter interface {
	// Get test case information
	GetTests(context.Context) <-chan *types.TestCase
}
