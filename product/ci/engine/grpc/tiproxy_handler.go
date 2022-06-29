// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"context"
	"encoding/json"
	"fmt"
	fs "github.com/harness/harness-core/commons/go/lib/filesystem"
	cgp "github.com/harness/harness-core/product/ci/addon/parser/cg"
	"github.com/harness/harness-core/product/ci/common/avro"
	"github.com/pkg/errors"
	"io"
	"path/filepath"
	"strings"

	"github.com/harness/harness-core/product/ci/common/external"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
)

var (
	remoteTiClient = external.GetTiHTTPClient
	getOrgId       = external.GetOrgId
	getProjectId   = external.GetProjectId
	getPipelineId  = external.GetPipelineId
	getBuildId     = external.GetBuildId
	getStageId     = external.GetStageId
)

const (
	cgSchemaType = "callgraph"
)

// handler is used to implement EngineServer
type tiProxyHandler struct {
	log *zap.SugaredLogger
}

// NewTiProxyHandler returns a GRPC handler that implements pb.TiProxyServer
func NewTiProxyHandler(log *zap.SugaredLogger) pb.TiProxyServer {
	return &tiProxyHandler{log}
}

// SelectTests gets the list of selected tests to be run.
// TODO: Stream the response as there is a 4MB limit on message sizes in gRPC
func (h *tiProxyHandler) SelectTests(ctx context.Context, req *pb.SelectTestsRequest) (*pb.SelectTestsResponse, error) {
	var err error
	tc, err := remoteTiClient()
	if err != nil {
		h.log.Errorw("could not create a client to the TI service", zap.Error(err))
		return nil, err
	}
	step := req.GetStepId()
	if step == "" {
		return nil, errors.New("step ID not present in request")
	}
	sha := req.GetSha() // may not be set for manual execution
	repo := req.GetRepo()
	if repo == "" {
		return nil, errors.New("repo not present in request")
	}
	source := req.GetSourceBranch() // may not be set for manual execution
	target := req.GetTargetBranch()
	if target == "" {
		return nil, errors.New("target branch not present in request")
	}
	body := req.GetBody()
	org, err := getOrgId()
	if err != nil {
		return nil, err
	}
	project, err := getProjectId()
	if err != nil {
		return nil, err
	}
	pipeline, err := getPipelineId()
	if err != nil {
		return nil, err
	}
	build, err := getBuildId()
	if err != nil {
		return nil, err
	}
	stage, err := getStageId()
	if err != nil {
		return nil, err
	}
	selection, err := tc.SelectTests(ctx, org, project, pipeline, build, stage, step, repo, sha, source, target, body)
	if err != nil {
		return nil, err
	}

	jsonStr, err := json.Marshal(selection)
	if err != nil {
		return &pb.SelectTestsResponse{}, err
	}
	return &pb.SelectTestsResponse{
		Selected: string(jsonStr),
	}, nil
}

// WriteTests writes tests to the TI service.
func (h *tiProxyHandler) WriteTests(stream pb.TiProxy_WriteTestsServer) error {
	var err error
	tc, err := remoteTiClient()
	if err != nil {
		h.log.Errorw("could not create a client to the TI service", zap.Error(err))
		return err
	}
	var tests []*types.TestCase
	stepId := ""
	repo := ""
	sha := ""
	commitLink := ""
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			h.log.Errorw("received error from client stream while trying to receive test case data to upload", zap.Error(err))
			continue
		}
		stepId = msg.GetStepId()
		repo = msg.GetRepo()
		sha = msg.GetSha()
		commitLink = msg.GetCommitLink()
		ret := msg.GetTests()
		for _, bt := range ret {
			t := &types.TestCase{}
			err = json.Unmarshal([]byte(bt), t)
			if err != nil {
				return fmt.Errorf("could not unmarshal data: %s", err)
			}
			tests = append(tests, t)
		}
	}
	if stepId == "" {
		return errors.New("step ID not present in response")
	}
	org, err := getOrgId()
	if err != nil {
		return err
	}
	project, err := getProjectId()
	if err != nil {
		return err
	}
	pipeline, err := getPipelineId()
	if err != nil {
		return err
	}
	build, err := getBuildId()
	if err != nil {
		return err
	}
	stage, err := getStageId()
	if err != nil {
		return err
	}
	report := "junit" // get from proto if we need other reports in the future
	err = tc.Write(stream.Context(), org, project, pipeline, build, stage, stepId, report, repo, sha, commitLink, tests)
	if err != nil {
		h.log.Errorw("could not write test cases: ", zap.Error(err))
		return err
	}
	err = stream.SendAndClose(&pb.WriteTestsResponse{})
	if err != nil {
		h.log.Errorw("could not close test case data protobuf stream", zap.Error(err))
		return err
	}
	h.log.Infow("parsed test cases", "num_cases", len(tests))
	return nil
}

func (h *tiProxyHandler) UploadCg(ctx context.Context, req *pb.UploadCgRequest) (*pb.UploadCgResponse, error) {
	step := req.GetStepId()
	res := &pb.UploadCgResponse{}
	if step == "" {
		return res, fmt.Errorf("step ID not present in request")
	}
	sha := req.GetSha()
	repo := req.GetRepo()
	if repo == "" {
		return res, fmt.Errorf("repo not present in request")
	}
	source := req.GetSource()
	if source == "" {
		return res, fmt.Errorf("source branch not present in request")
	}
	target := req.GetTarget()
	if target == "" {
		return res, fmt.Errorf("target branch not present in request")
	}
	timeMs := req.GetTimeMs()
	client, err := remoteTiClient()
	if err != nil {
		return res, errors.Wrap(err, "failed to create tiClient")
	}
	org, err := getOrgId()
	if err != nil {
		return res, errors.Wrap(err, "org id not found")
	}
	project, err := getProjectId()
	if err != nil {
		return res, errors.Wrap(err, "project id not found")
	}
	pipeline, err := getPipelineId()
	if err != nil {
		return res, errors.Wrap(err, "pipeline id not found")
	}
	build, err := getBuildId()
	if err != nil {
		return res, errors.Wrap(err, "build id not found")
	}
	stage, err := getStageId()
	if err != nil {
		return res, errors.Wrap(err, "stage id not found")
	}

	//Upload callgraph to TI server
	encCg, err := h.getEncodedData(req)
	if err != nil {
		return res, errors.Wrap(err, "failed to get avro encoded callgraph")
	}
	err = client.UploadCg(ctx, org, project, pipeline, build, stage, step, repo, sha, source, target, timeMs, encCg)
	if err != nil {
		return res, errors.Wrap(err, "failed to upload cg to ti server")
	}
	return res, nil
}

// getCgFiles return list of cg files in given directory
func (h *tiProxyHandler) getCgFiles(dir, ext1, ext2 string) ([]string, []string, error) {
	if !strings.HasSuffix(dir, "/") {
		dir = dir + "/"
	}
	cgFiles, err1 := filepath.Glob(dir + "*." + ext1)
	visFiles, err2 := filepath.Glob(dir + "*." + ext2)

	if err1 != nil || err2 != nil {
		h.log.Errorw(fmt.Sprintf("error in getting files list in dir %s", dir), zap.Error(err1), zap.Error(err2))
	}
	return cgFiles, visFiles, nil
}

// DownloadLink calls TI service to provide download link(s) for given input
func (h *tiProxyHandler) DownloadLink(ctx context.Context, req *pb.DownloadLinkRequest) (*pb.DownloadLinkResponse, error) {
	var err error
	tc, err := remoteTiClient()
	if err != nil {
		h.log.Errorw("could not create a client to the TI service", zap.Error(err))
		return nil, err
	}
	language := req.GetLanguage()
	os := req.GetOs()
	arch := req.GetArch()
	framework := req.GetFramework()
	version := req.GetVersion()
	env := req.GetEnv()
	link, err := tc.DownloadLink(ctx, language, os, arch, framework, version, env)
	if err != nil {
		return nil, err
	}

	jsonStr, err := json.Marshal(link)
	if err != nil {
		return &pb.DownloadLinkResponse{}, err
	}
	return &pb.DownloadLinkResponse{
		Links: string(jsonStr),
	}, nil
}

// getEncodedData reads all files of specified format from datadir folder and returns byte array of avro encoded format
func (h *tiProxyHandler) getEncodedData(req *pb.UploadCgRequest) ([]byte, error) {
	var parser cgp.Parser

	visDir := req.GetDataDir()
	if visDir == "" {
		return nil, fmt.Errorf("dataDir not present in request")
	}
	cgFiles, visFiles, err := h.getCgFiles(visDir, "json", "csv")
	if err != nil {
		return nil, errors.Wrap(err, "failed to fetch files inside the directory")
	}
	fs := fs.NewOSFileSystem(h.log)
	parser = cgp.NewCallGraphParser(h.log, fs)
	cg, err := parser.Parse(cgFiles, visFiles)
	if err != nil {
		return nil, errors.Wrap(err, "failed to parse visgraph")
	}
	h.log.Infow(fmt.Sprintf("size of nodes: %d, testReln: %d, visReln %d", len(cg.Nodes), len(cg.TestRelations), len(cg.VisRelations)))

	cgMap := cg.ToStringMap()
	cgSer, err := avro.NewCgphSerialzer(cgSchemaType)
	if err != nil {
		return nil, errors.Wrap(err, "failed to create serializer")
	}
	encCg, err := cgSer.Serialize(cgMap)
	if err != nil {
		return nil, errors.Wrap(err, "failed to encode callgraph")
	}
	return encCg, nil
}
