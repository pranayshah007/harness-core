// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package remote

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/harness/harness-core/product/ci/engine/consts"
	grpcclient "github.com/harness/harness-core/product/ci/engine/grpc/client"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

// GetTestTimes makes a gRPC call to lite-engine to get the test times
func GetTestTimes(ctx context.Context, log *zap.SugaredLogger, tiReq types.GetTestTimesReq) (types.GetTestTimesResp, error) {
	// Result of this function will be same as TI response for the API
	var tiResp types.GetTestTimesResp

	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		log.Errorw("Error occurred while requesting timing data: proxy connection failed")
		return tiResp, err
	}
	defer client.CloseConn()

	// Serialize the request body for TI API as string which will be
	// a part of engine gRPC request
	body, err := json.Marshal(&tiReq)
	if err != nil {
		return tiResp, err
	}
	req := &pb.GetTestTimesRequest{Body: string(body)}

	// Call the gRPC for getting the test time data
	resp, err := client.Client().GetTestTimes(ctx, req)
	if err != nil {
		log.Errorw("Error occurred while requesting timing data: API failed")
		return tiResp, err
	}
	log.Debug(fmt.Sprintf("Test timing data from api: %s", resp))

	// Response will contain a string which when deserialized will convert to
	// TI response object
	err = json.Unmarshal([]byte(resp.GetTimeDataMap()), &tiResp)
	if err != nil {
		log.Errorw("Could not unmarshal timing data response", zap.Error(err))
		return tiResp, err
	}
	return tiResp, nil
}
