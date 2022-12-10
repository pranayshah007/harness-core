// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"encoding/base64"
	"fmt"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	"github.com/golang/protobuf/proto"
	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/commons/go/lib/logs"
	caddon "github.com/harness/harness-core/product/ci/addon/grpc/client"
	amgrpc "github.com/harness/harness-core/product/ci/addon/grpc/client/mocks"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	mexecutor "github.com/harness/harness-core/product/ci/engine/legacy/executor/mocks"
	"github.com/harness/harness-core/product/ci/engine/legacy/state"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"google.golang.org/grpc"
)

type mockSvcClient struct {
	err error
}

func (c *mockSvcClient) ExecuteStep(ctx context.Context, in *addonpb.ExecuteStepRequest, opts ...grpc.CallOption) (*addonpb.ExecuteStepResponse, error) {
	return nil, nil
}

func (c *mockSvcClient) SignalStop(ctx context.Context, in *addonpb.SignalStopRequest, opts ...grpc.CallOption) (*addonpb.SignalStopResponse, error) {
	return nil, c.err
}

func getEncodedStageProto(t *testing.T, execution *pb.Execution) string {
	data, err := proto.Marshal(execution)
	if err != nil {
		t.Fatalf("marshaling error: %v", err)
	}
	return base64.StdEncoding.EncodeToString(data)
}

func getTestRunStep(id string, cmd string, envs []string, port uint32) *pb.UnitStep {
	ctx := &pb.StepContext{
		NumRetries: 2,
	}

	return &pb.UnitStep{
		Id:          id,
		DisplayName: fmt.Sprintf("step %s", id),
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Context:       ctx,
				Command:       cmd,
				EnvVarOutputs: envs,
				ContainerPort: port,
			},
		},
	}
}

func TestStageRun(t *testing.T) {
	tmpFilePath := "/tmp"
	executionProto1 := &pb.Execution{
		Steps: []*pb.Step{
			{
				Step: &pb.Step_Unit{
					Unit: &pb.UnitStep{
						Id: "test1",
						Step: &pb.UnitStep_Run{
							Run: &pb.RunStep{},
						},
					},
				},
			},
		},
	}
	executionProto2 := &pb.Execution{
		Steps: []*pb.Step{
			{
				Step: &pb.Step_Unit{
					Unit: &pb.UnitStep{
						Step: &pb.UnitStep_Run{
							Run: &pb.RunStep{},
						},
					},
				},
			},
		},
	}
	executionProto3 := &pb.Execution{
		Steps: []*pb.Step{
			{
				Step: &pb.Step_Parallel{
					Parallel: &pb.ParallelStep{},
				},
			},
		},
	}

	encodedExecutionProto1 := getEncodedStageProto(t, executionProto1)
	encodedExecutionProto2 := getEncodedStageProto(t, executionProto2)
	encodedExecutionProto3 := getEncodedStageProto(t, executionProto3)
	incorrectBase64Enc := "x"
	invalidStageEnc := "YWJjZA=="
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	tests := []struct {
		name         string
		encodedStage string
		expectedErr  bool
	}{
		{
			name:         "incorrect encoded stage",
			encodedStage: incorrectBase64Enc,
			expectedErr:  true,
		},
		{
			name:         "stage encoding not in execution proto format",
			encodedStage: invalidStageEnc,
			expectedErr:  true,
		},
		{
			name:         "empty stage success",
			encodedStage: "",
			expectedErr:  false,
		},
		{
			name:         "stage with error in run step",
			encodedStage: encodedExecutionProto1,
			expectedErr:  true,
		},
		{
			name:         "step ID is not set in stage",
			encodedStage: encodedExecutionProto2,
			expectedErr:  true,
		},
		{
			name:         "parallel step ID is not set in stage",
			encodedStage: encodedExecutionProto3,
			expectedErr:  true,
		},
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	// Initialize a mock CI addon
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return nil, errors.New("Could not create client")
	}

	for _, tc := range tests {
		e := NewStageExecutor(tc.encodedStage, tmpFilePath, nil, false, log.Sugar())
		got := e.Run()
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
	}
}

func TestStageSuccess(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	tmpFilePath := "/tmp"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	outputKey := "foo"
	outputVal1 := "step1"
	outputVal2 := "step2"
	outputVal3 := "step3"

	stepOutput1 := &output.StepOutput{}
	stepOutput1.Output.Variables = map[string]string{outputKey: outputVal1}

	stepOutput2 := &output.StepOutput{}
	stepOutput2.Output.Variables = map[string]string{outputKey: outputVal2}

	stepOutput3 := &output.StepOutput{}
	stepOutput3.Output.Variables = map[string]string{outputKey: outputVal3}

	psOutput := map[string]*output.StepOutput{"step1": stepOutput1, "step2": stepOutput2}

	step1 := getTestRunStep("step1", `echo step1`, []string{}, uint32(8000))
	step2 := getTestRunStep("step2", `echo step2`, []string{}, uint32(8001))
	step3 := getTestRunStep("step3", `echo step3`, []string{}, uint32(8001))
	pstep := &pb.ParallelStep{
		Id:    "parallel",
		Steps: []*pb.UnitStep{step1, step2},
	}

	execution := &pb.Execution{
		Steps: []*pb.Step{
			{
				Step: &pb.Step_Parallel{Parallel: pstep},
			},
			{
				Step: &pb.Step_Unit{Unit: step3},
			},
		},
	}
	encodedStage := getEncodedStageProto(t, execution)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	// Initialize a mock CI addon
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return nil, errors.New("Could not create client")
	}

	// Mock out unit Executor run step
	mockUnitExecutor := mexecutor.NewMockUnitExecutor(ctrl)
	mockParalleExecutor := mexecutor.NewMockParallelExecutor(ctrl)
	e := &stageExecutor{
		unitExecutor:     mockUnitExecutor,
		parallelExecutor: mockParalleExecutor,
		tmpFilePath:      tmpFilePath,
		log:              log.Sugar(),
		encodedStage:     encodedStage,
		stageOutput:      make(output.StageOutput),
	}
	mockParalleExecutor.EXPECT().Run(gomock.Any(), gomock.Any(), gomock.Any(), gomock.Any()).Return(psOutput, nil)
	mockParalleExecutor.EXPECT().Cleanup(gomock.Any(), gomock.Any()).Return(nil)
	mockUnitExecutor.EXPECT().Run(gomock.Any(), gomock.Any(), gomock.Any(), gomock.Any()).Return(stepOutput3, nil)
	mockUnitExecutor.EXPECT().Cleanup(gomock.Any(), gomock.Any()).Return(nil)

	err := e.Run()
	assert.Equal(t, err, nil)
	assert.Equal(t, e.stageOutput["step1"], stepOutput1)
	assert.Equal(t, e.stageOutput["step2"], stepOutput2)
	assert.Equal(t, e.stageOutput["step3"], stepOutput3)
}

func TestExecuteStage(t *testing.T) {
	tmpFilePath := "/tmp"
	emptyStage := ""
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	ExecuteStage(emptyStage, tmpFilePath, nil, false, log.Sugar())
}

// Client creation failing
func TestStopIntegrationSvcClientErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return nil, errors.New("client create error")
	}

	e := &stageExecutor{
		log: log.Sugar(),
	}
	err := e.stopIntegrationSvc(ctx, port)
	assert.NotNil(t, err)
}

// Failed to send GRPC request
func TestStopIntegrationSvcErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	c := &mockSvcClient{
		err: errors.New("server not running"),
	}
	mClient := amgrpc.NewMockAddonClient(ctrl)
	mClient.EXPECT().CloseConn().Return(nil)
	mClient.EXPECT().Client().Return(c)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return mClient, nil
	}

	e := &stageExecutor{
		log: log.Sugar(),
	}
	err := e.stopIntegrationSvc(ctx, port)
	assert.NotNil(t, err)
}

func TestStopIntegrationSvcSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	c := &mockSvcClient{
		err: nil,
	}
	mClient := amgrpc.NewMockAddonClient(ctrl)
	mClient.EXPECT().CloseConn().Return(nil)
	mClient.EXPECT().Client().Return(c)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return mClient, nil
	}

	e := &stageExecutor{
		log: log.Sugar(),
	}
	err := e.stopIntegrationSvc(ctx, port)
	assert.Nil(t, err)
}

func TestWaitForRunningState(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	e := &stageExecutor{
		log: log.Sugar(),
	}

	s := state.ExecutionState()
	s.SetState(state.PAUSED)
	go func() {
		time.Sleep(100 * time.Millisecond)
		s.SetState(state.RUNNING)
		ch := s.ResumeSignal()
		ch <- true
	}()

	e.waitForRunningState()
}
