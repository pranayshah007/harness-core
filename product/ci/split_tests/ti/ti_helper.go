// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package ti

import (
	"context"
	b64 "encoding/base64"
	"encoding/json"
	logger "github.com/harness/harness-core/commons/go/lib/logs"
	ti_addon "github.com/harness/harness-core/product/ci/addon/remote"
	"github.com/harness/harness-core/product/ci/common/external"
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
