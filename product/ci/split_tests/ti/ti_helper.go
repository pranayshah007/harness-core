// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package ti

import (
	"context"
	b64 "encoding/base64"
	"os"

	logger "github.com/harness/harness-core/commons/go/lib/logs"
	ti_addon "github.com/harness/harness-core/product/ci/addon/remote"
	"github.com/harness/harness-core/product/ci/common/external"
	ticlient "github.com/harness/ti-client/client"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	getAccountId            = external.GetAccountId
	getOrgId                = external.GetOrgId
	getProjectId            = external.GetProjectId
	getPipelineId           = external.GetPipelineId
	getTiSvcToken           = external.GetTiSvcToken
	remoteTiClientWithToken = external.GetTiHTTPClientWithToken
	callLiteEngine          = ti_addon.GetTestTimes
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
	tiClient := remoteTiClientWithToken("", "", "", false, string(decodedToken))
	return tiClient, nil
}

// callTiSvc makes an API call to the TI service
func callTiSvc(ctx context.Context, log *zap.SugaredLogger, tiReq types.GetTestTimesReq) (types.GetTestTimesResp, error) {
	var tiResp types.GetTestTimesResp

	// TI Client
	tiClient, err := getTiClient(log)
	if err != nil {
		return tiResp, err
	}

	// GetTestTimes API call
	ctxWithLogger := logger.WithContext(ctx, log)
	tiResp, err = tiClient.GetTestTimes(ctxWithLogger, &tiReq)
	if err != nil {
		log.Errorw("Error occurred while requesting timing data: API failed")
		return tiResp, err
	}
	return tiResp, nil
}
