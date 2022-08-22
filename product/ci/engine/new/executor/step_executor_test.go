// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"bytes"
	"context"
	"io"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestStepValidations(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	callbackToken := "token"
	taskID := "taskID"
	tmpFilePath := "/tmp"

	stepProto1 := &pb.UnitStep{
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{},
		},
	}
	stepProto3 := &pb.UnitStep{
		Id: "test1",
	}

	stepProto4 := &pb.UnitStep{
		Id:            "test1",
		CallbackToken: callbackToken,
	}

	stepProto5 := &pb.UnitStep{
		Id:            "test1",
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	tests := []struct {
		name        string
		step        *pb.UnitStep
		expectedErr bool
	}{
		{
			name:        "step ID is not set",
			step:        stepProto1,
			expectedErr: true,
		},
		{
			name:        "callback token is not set",
			step:        stepProto3,
			expectedErr: true,
		},
		{
			name:        "task ID is not set",
			step:        stepProto4,
			expectedErr: true,
		},
		{
			name:        "account ID is not set",
			step:        stepProto5,
			expectedErr: true,
		},
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}
	for _, tc := range tests {
		e := NewStepExecutor(tmpFilePath, "", log.Sugar(), new(bytes.Buffer))
		got := e.Run(ctx, tc.step)
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
	}
}

func TestStepError(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "ls",
				ContainerPort: uint32(8000),
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
		AccountId:     accountID,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	oldAddonExecutor := executeStepOnAddon
	defer func() { executeStepOnAddon = oldAddonExecutor }()
	executeStepOnAddon = func(ctx context.Context, step *pb.UnitStep, tmpFilePath string,
		log *zap.SugaredLogger, buf io.Writer) (*output.StepOutput, *pb.Artifact, error) {
		return nil, nil, errors.New("failed")
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewStepExecutor(tmpFilePath, "foo", log.Sugar(), new(bytes.Buffer))
	err := e.Run(ctx, stepProto)
	assert.NotEqual(t, err, nil)
}

func TestStepRunSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "ls",
				ContainerPort: uint32(8000),
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
		AccountId:     accountID,
	}

	outputKey := "foo"
	outputVal := "bar"

	o := &output.StepOutput{}
	o.Output.Variables = map[string]string{outputKey: outputVal}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	oldAddonExecutor := executeStepOnAddon
	defer func() { executeStepOnAddon = oldAddonExecutor }()
	executeStepOnAddon = func(ctx context.Context, step *pb.UnitStep, tmpFilePath string,
		log *zap.SugaredLogger, buf io.Writer) (*output.StepOutput, *pb.Artifact, error) {
		return o, nil, nil
	}

	oldStopAdddon := stopAddon
	defer func() { stopAddon = oldStopAdddon }()
	stopAddon = func(ctx context.Context, stepID string, port uint32,
		log *zap.SugaredLogger) error {
		return nil
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewStepExecutor(tmpFilePath, "foo", log.Sugar(), new(bytes.Buffer))
	err := e.Run(ctx, stepProto)
	assert.Equal(t, err, nil)
}

func TestDetachStepRunSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "ls",
				ContainerPort: uint32(8000),
				Detach:        true,
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
		AccountId:     accountID,
	}

	o := &output.StepOutput{}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	oldAddonExecutor := executeStepOnAddon
	defer func() { executeStepOnAddon = oldAddonExecutor }()
	executeStepOnAddon = func(ctx context.Context, step *pb.UnitStep, tmpFilePath string,
		log *zap.SugaredLogger, buf io.Writer) (*output.StepOutput, *pb.Artifact, error) {
		return o, nil, nil
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewStepExecutor(tmpFilePath, "foo", log.Sugar(), new(bytes.Buffer))
	err := e.Run(ctx, stepProto)
	assert.Equal(t, err, nil)
}
