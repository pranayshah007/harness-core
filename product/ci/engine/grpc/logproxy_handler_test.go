// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"bytes"
	"google.golang.org/grpc"
	"io"

	"context"
	"encoding/json"
	"errors"
	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/log-service/client"
	"github.com/harness/harness-core/product/log-service/mock"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"testing"
)

type uploadServer struct {
	err     error
	ctx     context.Context
	request *pb.UploadUsingLinkRequest
	done    bool
	grpc.ServerStream
}

func (s *uploadServer) SendAndClose(in *pb.UploadUsingLinkResponse) error {
	return nil
}

func (s *uploadServer) Recv() (*pb.UploadUsingLinkRequest, error) {
	if !s.done {
		s.done = true
		return s.request, nil
	}
	return &pb.UploadUsingLinkRequest{}, io.EOF
}

func (s *uploadServer) Context() context.Context {
	return s.ctx
}

func NewUploadUsingLinkMock(err error, ctx context.Context, req *pb.UploadUsingLinkRequest) *uploadServer {
	return &uploadServer{err: err, ctx: ctx, request: req}
}

func TestWrite_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"
	var lines []*stream.Line
	l1 := &stream.Line{Level: "info", Message: "test", Args: map[string]string{"k1": "v1"}}
	json1, _ := json.Marshal(l1)
	l2 := &stream.Line{Level: "info", Message: "test2", Args: map[string]string{"k2": "v2"}}
	json2, _ := json.Marshal(l2)
	lines = append(lines, l1)
	lines = append(lines, l2)

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().Write(ctx, key, lines).Return(nil)

	var jsonLines []string
	jsonLines = append(jsonLines, string(json1))
	jsonLines = append(jsonLines, string(json2))

	in := &pb.WriteRequest{Key: key, Lines: jsonLines}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Write(ctx, in)
	assert.Nil(t, err)
}

func TestWrite_Failure_IncorrectLineFormat(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)

	var lines []string
	lines = append(lines, "incorrect format")

	in := &pb.WriteRequest{Key: key, Lines: lines}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Write(ctx, in)
	assert.NotNil(t, err)
}


func Test_UploadLink_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"
	strLink := "http://minio:9000"
	link := &client.Link{Value: strLink}

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().UploadLink(ctx, key).Return(link, nil)

	in := &pb.UploadLinkRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	resp, err := h.UploadLink(ctx, in)
	assert.Nil(t, err)
	assert.Equal(t, resp.GetLink(), strLink)
}

func Test_UploadLink_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().UploadLink(ctx, key).Return(nil, errors.New("upload link failure"))

	in := &pb.UploadLinkRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.UploadLink(ctx, in)
	assert.NotNil(t, err)
}

func Test_Open_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().Open(ctx, key).Return(nil)

	in := &pb.OpenRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Open(ctx, in)
	assert.Nil(t, err)
}

func Test_Open_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().Open(ctx, key).Return(errors.New("failed to open stream"))

	in := &pb.OpenRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Open(ctx, in)
	assert.NotNil(t, err)
}

func Test_Close_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().Close(ctx, key).Return(nil)

	in := &pb.CloseRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Close(ctx, in)
	assert.Nil(t, err)
}

func Test_Close_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	key := "key"

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().Close(ctx, key).Return(errors.New("failed to open stream"))

	in := &pb.CloseRequest{Key: key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	_, err := h.Close(ctx, in)
	assert.NotNil(t, err)
}

func Test_UploadUsingLink_Success(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	link := "http://minio:9000"

	l1 := &stream.Line{Level: "info", Message: "test", Args: map[string]string{"k1": "v1"}}
	json1, _ := json.Marshal(l1)
	l2 := &stream.Line{Level: "info", Message: "test2", Args: map[string]string{"k2": "v2"}}
	json2, _ := json.Marshal(l2)

	var jsonLines []string
	jsonLines = append(jsonLines, string(json1))
	jsonLines = append(jsonLines, string(json2))

	data := new(bytes.Buffer)
	for _, line := range jsonLines {
		data.Write([]byte(line))
	}

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().UploadUsingLink(ctx, link, data).Return(nil)

	req := &pb.UploadUsingLinkRequest{Link: link, Lines: jsonLines}
	in := NewUploadUsingLinkMock(nil, ctx, req)

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	err := h.UploadUsingLink(in)
	assert.Nil(t, err)
}

func Test_UploadUsingLink_Failure(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	link := "http://minio:9000"

	l1 := &stream.Line{Level: "info", Message: "test", Args: map[string]string{"k1": "v1"}}
	json1, _ := json.Marshal(l1)
	l2 := &stream.Line{Level: "info", Message: "test2", Args: map[string]string{"k2": "v2"}}
	json2, _ := json.Marshal(l2)

	var jsonLines []string
	jsonLines = append(jsonLines, string(json1))
	jsonLines = append(jsonLines, string(json2))

	data := new(bytes.Buffer)
	for _, line := range jsonLines {
		data.Write([]byte(line))
	}

	mLogClient := mock.NewMockClient(ctrl)
	mLogClient.EXPECT().UploadUsingLink(ctx, link, data).Return(errors.New("upload using link failure"))

	req := &pb.UploadUsingLinkRequest{Link: link, Lines: jsonLines}
	in := NewUploadUsingLinkMock(nil, ctx, req)

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewLogProxyHandler(log.Sugar(), mLogClient)
	err := h.UploadUsingLink(in)
	assert.NotNil(t, err)
}