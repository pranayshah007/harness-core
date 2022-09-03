// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package logutil

import (
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/common/external"
	plogs "github.com/harness/harness-core/product/ci/common/logs"
)

// GetGrpcRemoteLogger is a helper method that returns a logger than can communicate with the
// gRPC log server hosted on lite engine.
func GetGrpcRemoteLogger(key string) (*logs.RemoteLogger, error) {
	grpcClient, err := NewGrpcRemoteClient()
	if err != nil {
		return nil, err
	}
	indirectUpload, err := external.GetLogUploadFF()
	indirectUpload = true
	if err != nil {
		return nil, err
	}
	rw, err := plogs.NewRemoteWriter(grpcClient, key, external.GetNudges(), indirectUpload)
	if err != nil {
		return nil, err
	}
	rws := logs.NewReplacer(rw, external.GetSecrets()) // Remote writer with secrets masked
	rl, err := logs.NewRemoteLogger(rws)
	if err != nil {
		return nil, err
	}
	return rl, nil
}
