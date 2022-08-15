// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package ti

import (
	"context"
	b64 "encoding/base64"
	"encoding/json"
	"fmt"
	logger "github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/consts"
	grpcclient "github.com/harness/harness-core/product/ci/engine/grpc/client"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	ticlient "github.com/harness/harness-core/product/ci/ti-service/client"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
	"os"
)

var (
	getAccountId            = external.GetAccountId
	getOrgId                = external.GetOrgId
	getProjectId            = external.GetProjectId
	getPipelineId           = external.GetPipelineId
	getTiSvcToken           = external.GetTiSvcToken
	remoteTiClientWithToken = external.GetTiHTTPClientWithToken
)

func getInfra() string {
	infra, _ := os.LookupEnv("HARNESS_INFRA")
	return infra
}

// GetTestTimes calls a TI API to get the test timing data
func GetTestTimes(ctx context.Context, log *zap.SugaredLogger, tiReq types.GetTestTimesReq) (types.GetTestTimesResp, error) {
	log.Debug("Getting timing data for the given argument")

	if getInfra() == "VM" {
		return callTiSvc(ctx, log, tiReq)
	}
	return callLiteEngine(ctx, log, tiReq)
}

func getTiClient(log *zap.SugaredLogger) (ticlient.Client, error) {
	token, err := getTiSvcToken()
	if err != nil {
		log.Errorw("No token present to call TI service")
		return nil, err
	}
	decodedToken, err := b64.StdEncoding.DecodeString(token)
	if err != nil {
		log.Errorw("Unable to decode the token present to call TI service")
		return nil, err
	}
	tc, err := remoteTiClientWithToken(string(decodedToken))
	if err != nil {
		log.Errorw("Error occurred while requesting timing data: remote connection failed")
		return nil, err
	}
	return tc, err
}

// callTiSvc makes an API call to the TI service
func callTiSvc(ctx context.Context, log *zap.SugaredLogger, tiReq types.GetTestTimesReq) (types.GetTestTimesResp, error) {
	var tiResp types.GetTestTimesResp
	tc, err := getTiClient(log)
	if err != nil {
		return tiResp, err
	}
	org, err := getOrgId()
	if err != nil {
		return tiResp, err
	}
	project, err := getProjectId()
	if err != nil {
		return tiResp, err
	}
	pipeline, err := getPipelineId()
	if err != nil {
		return tiResp, err
	}
	body, err := json.Marshal(&tiReq)
	if err != nil {
		return tiResp, err
	}
	ctxWithLogger := logger.WithContext(ctx, log)
	tiResp, err = tc.GetTestTimes(ctxWithLogger, org, project, pipeline, string(body))
	if err != nil {
		log.Errorw("Error occurred while requesting timing data: API failed")
		return tiResp, err
	}
	return tiResp, nil
}

// callLiteEngine makes a gRPC call to lite-engine
func callLiteEngine(ctx context.Context, log *zap.SugaredLogger, tiReq types.GetTestTimesReq) (types.GetTestTimesResp, error) {
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
