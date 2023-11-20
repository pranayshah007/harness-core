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

	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/log-service/client"
	"github.com/harness/harness-core/product/log-service/stream"
	"go.uber.org/zap"
)

// handler is used to implement EngineServer
type logProxyHandler struct {
	log    *zap.SugaredLogger
	client client.Client
}

// NewEngineHandler returns a GRPC handler that implements pb.EngineServer
func NewLogProxyHandler(log *zap.SugaredLogger, client client.Client) pb.LogProxyServer {
	return &logProxyHandler{log, client}
}

// Write writes to a log stream.
// Connects to the log service to invoke the write to stream API.
func (h *logProxyHandler) Write(ctx context.Context, in *pb.WriteRequest) (*pb.WriteResponse, error) {
	var err error
	h.log.Infow("LogProxy - starting write", "key", in.GetKey())
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
	err = h.client.Write(ctx, in.GetKey(), lines)
	if err != nil {
		fmt.Println("error while writing to the stream: ", err)
		h.log.Errorw("Could not write to the log stream", "key", in.GetKey(), zap.Error(err))
		return &pb.WriteResponse{}, err
	}
	h.log.Infow("LogProxy - completed write API call", "key", in.GetKey())
	return &pb.WriteResponse{}, nil
}

// UploadLink returns an upload link for the logs.
// Connects to the log service to invoke the UploadLink to store API.
func (h *logProxyHandler) UploadLink(ctx context.Context, in *pb.UploadLinkRequest) (*pb.UploadLinkResponse, error) {
	link, err := h.client.UploadLink(ctx, in.GetKey())
	if err != nil {
		fmt.Println("error while generating an upload link: ", err)
		h.log.Errorw("Could not generate an upload link for log upload", "key", in.GetKey(), zap.Error(err))
		return &pb.UploadLinkResponse{}, err
	}
	return &pb.UploadLinkResponse{Link: link.Value}, nil
}

// UploadUsingLink uploads logs to an uploadable link (directly to blob storage).
func (h *logProxyHandler) UploadUsingLink(stream pb.LogProxy_UploadUsingLinkServer) error {
	var err error
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

	err = h.client.UploadUsingLink(stream.Context(), link, data)
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

	h.log.Infow("Start to upload to the log service", key, zap.Error(err))

	if key == "" {
		return errors.New("no key received from client for UploadRPC")
	}

	err = h.client.Upload(stream.Context(), key, data)
	if err != nil {
		fmt.Println("error while uploading the stream: ", err)
		h.log.Errorw("could not upload logs using uploadRPC", key, zap.Error(err))
		return err
	}
	err = stream.SendAndClose(&pb.UploadResponse{})
	if err != nil {
		h.log.Errorw("could not close upload protobuf stream", zap.Error(err))
		return err
	}
	h.log.Infow("Finished uploading to the log service", key, zap.Error(err))
	return nil
}

// Open opens the log stream.
// Connects to the log service to invoke the open stream API.
func (h *logProxyHandler) Open(ctx context.Context, in *pb.OpenRequest) (*pb.OpenResponse, error) {
	var err error
	h.log.Infow("LogProxy - starting open stream API call", "key", in.GetKey())
	err = h.client.Open(ctx, in.GetKey())
	if err != nil {
		fmt.Println("error while opening the stream: ", err)
		h.log.Errorw("Could not open log stream", "key", in.GetKey(), zap.Error(err))
		return &pb.OpenResponse{}, err
	}
	h.log.Infow("LogProxy - completed open stream API call", "key", in.GetKey())
	return &pb.OpenResponse{}, nil
}

// Close closes the log stream.
// Connects to the log service and closes a stream.
func (h *logProxyHandler) Close(ctx context.Context, in *pb.CloseRequest) (*pb.CloseResponse, error) {
	var err error
	h.log.Infow("LogProxy - starting close stream API call", "key", in.GetKey())
	err = h.client.Close(ctx, in.GetKey())
	if err != nil {
		fmt.Println("error while closing the stream: ", err)
		h.log.Errorw("Could not close log stream", "key", in.GetKey(), zap.Error(err))
		return &pb.CloseResponse{}, err
	}
	h.log.Infow("LogProxy - completed close stream API call", "key", in.GetKey())
	return &pb.CloseResponse{}, nil
}
