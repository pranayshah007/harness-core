// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"path/filepath"
	"strings"

	fs "github.com/harness/harness-core/commons/go/lib/filesystem"
	cgp "github.com/harness/harness-core/product/ci/addon/parser/cg"
	"github.com/harness/harness-core/product/ci/common/avro"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/pkg/errors"

	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	getRemoteTiClient = external.GetTiHTTPClient
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
	// TI Client
	repo := req.GetRepo()
	sha := req.GetSha()
	commitLink := ""
	skipVerify := false
	tiClient := getRemoteTiClient(repo, sha, commitLink, skipVerify)

	// SelectTests API call
	stepID := req.GetStepId()
	source := req.GetSourceBranch()
	target := req.GetTargetBranch()
	body := req.GetBody()
	var tiReq *types.SelectTestsReq
	err := json.Unmarshal([]byte(body), &tiReq)
	if err != nil {
		return nil, err
	}
	selection, err := tiClient.SelectTests(ctx, stepID, source, target, tiReq)
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
	// Stream all the test results
	var tests []*types.TestCase
	var stepID, repo, sha, commitLink string
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			h.log.Errorw("received error from client stream while trying to receive test case data to upload", zap.Error(err))
			continue
		}
		stepID = msg.GetStepId()
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

	// TI Client
	skipVerify := false
	tiClient := getRemoteTiClient(repo, sha, commitLink, skipVerify)

	// Write API call
	report := "junit" // get from proto if we need other reports in the future
	err := tiClient.Write(stream.Context(), stepID, report, tests)
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
	// TI Client
	repo := req.GetRepo()
	sha := req.GetSha()
	commitLink := ""
	skipVerify := false
	tiClient := getRemoteTiClient(repo, sha, commitLink, skipVerify)

	// UploadCg API call
	stepID := req.GetStepId()
	source := req.GetSource()
	target := req.GetTarget()
	timeMs := req.GetTimeMs()
	encCg, msg, err := h.getEncodedData(req)
	if err != nil {
		return nil, errors.Wrap(err, "failed to get avro encoded callgraph")
	}

	err = tiClient.UploadCg(ctx, stepID, source, target, timeMs, encCg)
	if err != nil {
		return nil, errors.Wrap(err, "failed to upload cg to ti server")
	}
	return &pb.UploadCgResponse{CgMsg: msg}, nil
}

// getCgFiles return list of cg files in given directory
func (h *tiProxyHandler) getCgFiles(dir, ext1, ext2 string) ([]string, []string, error) {
	if !strings.HasSuffix(dir, "/") {
		dir = dir + "/"
	}
	cgFiles, err1 := filepath.Glob(dir + "*." + ext1)
	visFiles, err2 := filepath.Glob(dir + "*." + ext2)
	h.log.Infow(fmt.Sprintf(strings.Join(cgFiles, ", ")))
	h.log.Infow(fmt.Sprintf(strings.Join(visFiles, ", ")))

	if err1 != nil || err2 != nil {
		h.log.Errorw(fmt.Sprintf("error in getting files list in dir %s", dir), zap.Error(err1), zap.Error(err2))
	}
	return cgFiles, visFiles, nil
}

// DownloadLink calls TI service to provide download link(s) for given input
func (h *tiProxyHandler) DownloadLink(ctx context.Context, req *pb.DownloadLinkRequest) (*pb.DownloadLinkResponse, error) {
	// TI Client
	repo := ""
	sha := ""
	commitLink := ""
	skipVerify := false
	tiClient := getRemoteTiClient(repo, sha, commitLink, skipVerify)

	// DownloadLink API call
	language := req.GetLanguage()
	os := req.GetOs()
	arch := req.GetArch()
	framework := req.GetFramework()
	version := req.GetVersion()
	env := req.GetEnv()
	link, err := tiClient.DownloadLink(ctx, language, os, arch, framework, version, env)
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
func (h *tiProxyHandler) getEncodedData(req *pb.UploadCgRequest) ([]byte, string, error) {
	var parser cgp.Parser

	visDir := req.GetDataDir()
	if visDir == "" {
		return nil, "", fmt.Errorf("dataDir not present in request")
	}
	cgFiles, visFiles, err := h.getCgFiles(visDir, "json", "csv")
	if err != nil {
		return nil, "", errors.Wrap(err, "failed to fetch files inside the directory")
	}
	fs := fs.NewOSFileSystem(h.log)
	parser = cgp.NewCallGraphParser(h.log, fs)
	cg, err := parser.Parse(cgFiles, visFiles)
	if err != nil {
		return nil, "", errors.Wrap(err, "failed to parse visgraph")
	}
	msg := fmt.Sprintf("Size of Test nodes: %d, Test relations: %d, Vis Relations %d", len(cg.Nodes), len(cg.TestRelations), len(cg.VisRelations))
	h.log.Infow(msg)

	cgMap := cg.ToStringMap()
	cgSer, err := avro.NewCgphSerialzer(cgSchemaType, false)
	if err != nil {
		return nil, "", errors.Wrap(err, "failed to create serializer")
	}
	encCg, err := cgSer.Serialize(cgMap)
	if err != nil {
		return nil, "", errors.Wrap(err, "failed to encode callgraph")
	}
	return encCg, msg, nil
}

// GetTestTimes gets the test timing data from the TI service
func (h *tiProxyHandler) GetTestTimes(ctx context.Context, req *pb.GetTestTimesRequest) (*pb.GetTestTimesResponse, error) {
	// TI Client
	repo := ""
	sha := ""
	commitLink := ""
	skipVerify := false
	tiClient := getRemoteTiClient(repo, sha, commitLink, skipVerify)

	// GetTestTimes API call
	reqBody := req.GetBody()
	var tiReq *types.GetTestTimesReq
	err := json.Unmarshal([]byte(reqBody), &tiReq)
	if err != nil {
		return nil, err
	}
	timeMap, err := tiClient.GetTestTimes(ctx, tiReq)
	if err != nil {
		return nil, err
	}

	// Serialize the API output to a string and add
	// it to the response
	timeDataMapStr, err := json.Marshal(timeMap)
	if err != nil {
		return &pb.GetTestTimesResponse{}, err
	}
	return &pb.GetTestTimesResponse{
		TimeDataMap: string(timeDataMapStr),
	}, nil
}
