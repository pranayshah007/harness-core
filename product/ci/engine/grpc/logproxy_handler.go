// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"

	"github.com/harness/harness-core/product/ci/common/external"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/log-service/stream"
	"go.uber.org/zap"
)

var (
	remoteLogClient = external.GetRemoteHTTPClient
)

// handler is used to implement EngineServer
type logProxyHandler struct {
	log *zap.SugaredLogger
}

// NewEngineHandler returns a GRPC handler that implements pb.EngineServer
func NewLogProxyHandler(log *zap.SugaredLogger) pb.LogProxyServer {
	return &logProxyHandler{log}
}

// Write writes to a log stream.
// Connects to the log service to invoke the write to stream API.
func (h *logProxyHandler) Write(ctx context.Context, in *pb.WriteRequest) (*pb.WriteResponse, error) {
	h.log.Infow("LogProxy - starting write", "key", in.GetKey())
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("could not create a client to the log service", zap.Error(err))
		return &pb.WriteResponse{}, err
	}
	var lines []*stream.Line
	for _, strLine := range in.GetLines() {
		l := &stream.Line{}
		err = json.Unmarshal([]byte(strLine), l)
		if err != nil {
			h.log.Errorw(fmt.Sprintf("unable to marshal received lines, first error instance: %s", strLine))
			return nil, fmt.Errorf("could not unmarshal received lines, first error instance: %s", strLine)
		}
		lines = append(lines, l)
	}
	h.log.Infow("LogProxy - starting write API call", "key", in.GetKey())
	err = lc.Write(ctx, in.GetKey(), lines)
	if err != nil {
		h.log.Errorw("Could not write to the log stream", zap.Error(err))
		return &pb.WriteResponse{}, err
	}
	h.log.Infow("LogProxy - completed write API call", "key", in.GetKey())
	return &pb.WriteResponse{}, nil
}

// UploadLink returns an upload link for the logs.
// Connects to the log service to invoke the UploadLink to store API.
func (h *logProxyHandler) UploadLink(ctx context.Context, in *pb.UploadLinkRequest) (*pb.UploadLinkResponse, error) {
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("Could not create a client to the log service", zap.Error(err))
		return &pb.UploadLinkResponse{}, err
	}
	link, err := lc.UploadLink(ctx, in.GetKey())
	if err != nil {
		h.log.Errorw("Could not generate an upload link for log upload", zap.Error(err))
		return &pb.UploadLinkResponse{}, err
	}
	return &pb.UploadLinkResponse{Link: link.Value}, nil
}

// UploadUsingLink uploads logs to an uploadable link (directly to blob storage).
func (h *logProxyHandler) UploadUsingLink(stream pb.LogProxy_UploadUsingLinkServer) error {
	var err error
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("could not create a client to the log service", zap.Error(err))
		return err
	}
	data := new(bytes.Buffer)
	link := ""
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			h.log.Errorw("received error from client stream while trying to receive log data to upload", zap.Error(err))
			continue
		}
		link = msg.GetLink()
		for _, line := range msg.GetLines() {
			data.Write([]byte(line))
		}
	}

	if link == "" {
		return errors.New("no link received from client")
	}

	err = lc.UploadUsingLink(stream.Context(), link, data)
	if err != nil {
		h.log.Errorw("could not upload logs using upload link", zap.Error(err))
		return err
	}
	err = stream.SendAndClose(&pb.UploadUsingLinkResponse{})
	if err != nil {
		h.log.Errorw("could not close upload protobuf stream", zap.Error(err))
		return err
	}
	return nil
}

// Upload uploads logs to log service (which in turn uploads to blob storage).
func (h *logProxyHandler) Upload(stream pb.LogProxy_UploadServer) error {
	var err error
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("could not create a client to the log service", zap.Error(err))
		return err
	}
	data := new(bytes.Buffer)
	key := ""
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			h.log.Errorw("received error from client stream while trying to receive log data to upload for UploadRPC", zap.Error(err))
			continue
		}
		key = msg.GetKey()
		for _, line := range msg.GetLines() {
			data.Write([]byte(line))
		}
	}

	if key == "" {
		return errors.New("no key received from client for UploadRPC")
	}

	err = lc.Upload(stream.Context(), key, data)
	if err != nil {
		h.log.Errorw("could not upload logs using uploadRPC", zap.Error(err))
		return err
	}
	err = stream.SendAndClose(&pb.UploadResponse{})
	if err != nil {
		h.log.Errorw("could not close upload protobuf stream", zap.Error(err))
		return err
	}
	return nil
}

// Open opens the log stream.
// Connects to the log service to invoke the open stream API.
func (h *logProxyHandler) Open(ctx context.Context, in *pb.OpenRequest) (*pb.OpenResponse, error) {
	var err error
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("Could not create a client to the log service", zap.Error(err))
		return &pb.OpenResponse{}, err
	}
	h.log.Infow("LogProxy - starting open stream API call", "key", in.GetKey())
	err = lc.Open(ctx, in.GetKey())
	if err != nil {
		h.log.Errorw("Could not open log stream", zap.Error(err))
		return &pb.OpenResponse{}, err
	}
	h.log.Infow("LogProxy - completed open stream API call", "key", in.GetKey())
	return &pb.OpenResponse{}, nil
}

// Close closes the log stream.
// Connects to the log service and closes a stream.
func (h *logProxyHandler) Close(ctx context.Context, in *pb.CloseRequest) (*pb.CloseResponse, error) {
	var err error
	lc, err := remoteLogClient()
	if err != nil {
		h.log.Errorw("Could not create a client to the log service", zap.Error(err))
		return &pb.CloseResponse{}, err
	}
	h.log.Infow("LogProxy - starting close stream API call", "key", in.GetKey())
	err = lc.Close(ctx, in.GetKey())
	if err != nil {
		h.log.Errorw("Could not close log stream", "key", in.GetKey(), zap.Error(err))
		return &pb.CloseResponse{}, err
	}
	h.log.Infow("LogProxy - completed close stream API call", "key", in.GetKey())
	return &pb.CloseResponse{}, nil
}
