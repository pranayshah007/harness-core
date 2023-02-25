// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package client

import (
	"context"
	"fmt"
	"github.com/harness/harness-core/product/ci/ti-service/types"
)

// Error is a custom error struct
type Error struct {
	Code    int
	Message string
}

func (e *Error) Error() string {
	return fmt.Sprintf("%d: %s", e.Code, e.Message)
}

// Client defines a TI service client.
type Client interface {
	// Write test cases to DB
	Write(ctx context.Context, step, report string, tests []*types.TestCase) error

	// SelectTests returns list of tests which should be run intelligently
	SelectTests(ctx context.Context, step, source, target string, in *types.SelectTestsReq) (types.SelectTestsResp, error)

	// UploadCg uploads avro encoded callgraph to ti server
	UploadCg(ctx context.Context, step, source, target string, timeMs int64, cg []byte) error

	// DownloadLink returns a list of links where the relevant agent artifacts can be downloaded
	DownloadLink(ctx context.Context, language, os, arch, framework, version, env string) ([]types.DownloadLink, error)

	// GetTestTimes returns the test timing data
	GetTestTimes(ctx context.Context, in *types.GetTestTimesReq) (types.GetTestTimesResp, error)
}
