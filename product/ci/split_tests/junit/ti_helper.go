// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package junit

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/harness/harness-core/product/ci/engine/consts"
	grpcclient "github.com/harness/harness-core/product/ci/engine/grpc/client"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
)

func GetTestTimes(ctx context.Context, log *zap.SugaredLogger) (types.GetTestTimesResp, error) {
	// Result of this function will be same as TI response for the API
	var res types.GetTestTimesResp

	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return res, err
	}
	defer client.CloseConn()

	// Serialize the request body for TI as string which will be
	// a part of engine gRPC request
	b, err := json.Marshal(&types.GetTestTimesReq{IncludeTestSuite: true})
	if err != nil {
		return res, err
	}
	req := &pb.GetTestTimesRequest{Body: string(b)}

	// Call the gRPC for getting the test time data
	resp, err := client.Client().GetTestTimes(ctx, req)
	if err != nil {
		return res, err
	}

	// Response will contain a string which when deserialized will convert to
	// TI response object
	err = json.Unmarshal([]byte(resp.GetMapList()), &res)
	if err != nil {
		fmt.Println("could not unmarshal select tests response on split tests", zap.Error(err))
		return res, err
	}
	return res, nil
}
