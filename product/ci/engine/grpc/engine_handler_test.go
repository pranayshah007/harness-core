// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestUpdateUnknownStatus(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.UpdateStateRequest{}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.UpdateState(ctx, arg)
	assert.NotNil(t, err)
}

func TestUpdateToPause(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.UpdateStateRequest{
		Action: pb.UpdateStateRequest_PAUSE,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.UpdateState(ctx, arg)
	assert.Nil(t, err)
}

func TestUpdateToResume(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.UpdateStateRequest{
		Action: pb.UpdateStateRequest_RESUME,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.UpdateState(ctx, arg)
	assert.Nil(t, err)
}

func TestGetImageEntrypointWithNoImage(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.GetImageEntrypointRequest{}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.GetImageEntrypoint(ctx, arg)
	assert.NotNil(t, err)
}

func TestGetImageEntrypointWithNoSecretSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	commands := []string{"git"}
	arg := &pb.GetImageEntrypointRequest{
		Id:    "git",
		Image: "plugins/git",
	}

	oldImgMetadata := getPublicImgMetadata
	getPublicImgMetadata = func(image string) ([]string, []string, error) {
		return commands, nil, nil
	}
	defer func() { getPublicImgMetadata = oldImgMetadata }()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.GetImageEntrypoint(ctx, arg)
	assert.Nil(t, err)
}

func TestGetImageEntrypointWithSecretSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	commands := []string{"git"}
	arg := &pb.GetImageEntrypointRequest{
		Id:     "git",
		Image:  "plugins/git",
		Secret: "foo",
	}

	oldImgMetadata := getPrivateImgMetadata
	getPrivateImgMetadata = func(image, secret string, transport *http.Transport) ([]string, []string, error) {
		return commands, nil, nil
	}
	defer func() { getPrivateImgMetadata = oldImgMetadata }()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.GetImageEntrypoint(ctx, arg)
	assert.Nil(t, err)
}

func TestGetImageEntrypointWithSecretErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.GetImageEntrypointRequest{
		Id:     "git",
		Image:  "plugins/git",
		Secret: "foo",
	}

	oldImgMetadata := getPrivateImgMetadata
	getPrivateImgMetadata = func(image, secret string, transport *http.Transport) ([]string, []string, error) {
		return nil, nil, fmt.Errorf("failed to find entrypoint")
	}
	defer func() { getPrivateImgMetadata = oldImgMetadata }()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.GetImageEntrypoint(ctx, arg)
	assert.NotNil(t, err)
}

func TestEvaluateJEXLErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.EvaluateJEXLRequest{
		StepId:      "test",
		Expressions: []string{"${foo.bar}"},
	}

	oldEvaluateJEXL := evaluateJEXL
	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput, isSkipCondition bool, log *zap.SugaredLogger) (
		map[string]string, error) {
		return nil, fmt.Errorf("invalid expression")
	}
	defer func() { evaluateJEXL = oldEvaluateJEXL }()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.EvaluateJEXL(ctx, arg)
	assert.NotNil(t, err)
}

func TestEvaluateJEXLSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.EvaluateJEXLRequest{
		StepId:      "test",
		Expressions: []string{"${foo.bar}"},
	}

	oldEvaluateJEXL := evaluateJEXL
	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput, isSkipCondition bool, log *zap.SugaredLogger) (
		map[string]string, error) {
		return nil, nil
	}
	defer func() { evaluateJEXL = oldEvaluateJEXL }()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.EvaluateJEXL(ctx, arg)
	assert.Nil(t, err)
}

func TestPing(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.PingRequest{}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	h := NewEngineHandler(log.Sugar(), new(bytes.Buffer))
	_, err := h.Ping(ctx, arg)
	assert.Nil(t, err)
}
