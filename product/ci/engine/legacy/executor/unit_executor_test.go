// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/golang/mock/gomock"
	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	caddon "github.com/harness/harness-core/product/ci/addon/grpc/client"
	amgrpc "github.com/harness/harness-core/product/ci/addon/grpc/client/mocks"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/engine/legacy/steps"
	msteps "github.com/harness/harness-core/product/ci/engine/legacy/steps/mocks"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
	"google.golang.org/grpc"
)

type mockClient struct {
	err error
}

func (c *mockClient) ExecuteStep(ctx context.Context, in *addonpb.ExecuteStepRequest, opts ...grpc.CallOption) (*addonpb.ExecuteStepResponse, error) {
	return nil, nil
}

func (c *mockClient) SignalStop(ctx context.Context, in *addonpb.SignalStopRequest, opts ...grpc.CallOption) (*addonpb.SignalStopResponse, error) {
	return nil, c.err
}

func TestStepValidations(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	callbackToken := "token"
	taskID := "taskID"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto1 := &pb.UnitStep{
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{},
		},
	}
	stepProto2 := &pb.UnitStep{
		Id: "test1",
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
			name:        "stage with error in run step",
			step:        stepProto2,
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
			name:        "empty step success",
			step:        stepProto5,
			expectedErr: false,
		},
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}
	for _, tc := range tests {
		e := NewUnitExecutor(tmpFilePath, log.Sugar())
		_, got := e.Run(ctx, tc.step, nil, accountID)
		if tc.expectedErr == (got == nil) {
			t.Fatalf("%s: expected error: %v, got: %v", tc.name, tc.expectedErr, got)
		}
	}
}

func TestStepRunError(t *testing.T) {
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
	}

	retries := int32(3)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockRunStep(ctrl)
	mockStep.EXPECT().Run(ctx).Return(nil, retries, fmt.Errorf("run step failed"))

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := runStep
	defer func() { runStep = oldStep }()
	runStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		log *zap.SugaredLogger) steps.RunStep {
		return mockStep
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
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
	}

	outputKey := "foo"
	outputVal := "bar"

	o := &output.StepOutput{}
	o.Output.Variables = map[string]string{outputKey: outputVal}
	retries := int32(3)

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockRunStep(ctrl)
	mockStep.EXPECT().Run(ctx).Return(o, retries, nil)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := runStep
	defer func() { runStep = oldStep }()
	runStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		log *zap.SugaredLogger) steps.RunStep {
		return mockStep
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	ret, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
	assert.Equal(t, ret.Output.Variables[outputKey], outputVal)
}

func TestStepPluginSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_Plugin{
			Plugin: &pb.PluginStep{
				Image:         "plugin/drone-git",
				ContainerPort: uint32(8000),
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}

	retries := int32(3)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockPluginStep(ctrl)
	mockStep.EXPECT().Run(ctx).Return(nil, retries, nil)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := pluginStep
	defer func() { pluginStep = oldStep }()
	pluginStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput, log *zap.SugaredLogger) steps.PluginStep {
		return mockStep
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}

func TestStepSaveCacheError(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	callbackToken := "token"
	taskID := "taskID"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_SaveCache{
			SaveCache: &pb.SaveCacheStep{
				Key:   "key",
				Paths: []string{"/tmp/m2"},
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	mockStep := msteps.NewMockSaveCacheStep(ctrl)
	mockStep.EXPECT().Run(ctx).Return(nil, errors.New("caching failed"))

	oldStep := saveCacheStep
	defer func() { saveCacheStep = oldStep }()
	saveCacheStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		fs filesystem.FileSystem, log *zap.SugaredLogger) steps.SaveCacheStep {
		return mockStep
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.NotEqual(t, err, nil)
}

func TestStepSaveCacheSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	key := "key"
	stepProto := &pb.UnitStep{
		Id: "test2",
		Step: &pb.UnitStep_SaveCache{
			SaveCache: &pb.SaveCacheStep{
				Key:   key,
				Paths: []string{"/tmp/m2"},
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}

	o := &output.StepOutput{}
	o.Output.Variables = map[string]string{"key": key}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockSaveCacheStep(ctrl)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := saveCacheStep
	defer func() { saveCacheStep = oldStep }()
	saveCacheStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		fs filesystem.FileSystem, log *zap.SugaredLogger) steps.SaveCacheStep {
		return mockStep
	}

	mockStep.EXPECT().Run(ctx).Return(o, nil)

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}

func TestStepRestoreCacheErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test3",
		Step: &pb.UnitStep_RestoreCache{
			RestoreCache: &pb.RestoreCacheStep{
				Key: "key",
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockRestoreCacheStep(ctrl)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := restoreCacheStep
	defer func() { restoreCacheStep = oldStep }()
	restoreCacheStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		fs filesystem.FileSystem, log *zap.SugaredLogger) steps.RestoreCacheStep {
		return mockStep
	}

	mockStep.EXPECT().Run(ctx).Return(errors.New("restore failed"))

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.NotEqual(t, err, nil)
}

func TestStepRestoreCacheSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test3",
		Step: &pb.UnitStep_RestoreCache{
			RestoreCache: &pb.RestoreCacheStep{
				Key: "key",
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockRestoreCacheStep(ctrl)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := restoreCacheStep
	defer func() { restoreCacheStep = oldStep }()
	restoreCacheStep = func(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
		fs filesystem.FileSystem, log *zap.SugaredLogger) steps.RestoreCacheStep {
		return mockStep
	}

	mockStep.EXPECT().Run(ctx).Return(nil)

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}

func TestPublishArtifactsSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test3",
		Step: &pb.UnitStep_PublishArtifacts{
			PublishArtifacts: &pb.PublishArtifactsStep{
				Images: []*pb.BuildPublishImage{},
				Files:  []*pb.UploadFile{},
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockPublishArtifactsStep(ctrl)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := publishArtifactsStep
	defer func() { publishArtifactsStep = oldStep }()
	publishArtifactsStep = func(step *pb.UnitStep, so output.StageOutput,
		log *zap.SugaredLogger) steps.PublishArtifactsStep {
		return mockStep
	}

	mockStep.EXPECT().Run(ctx).Return(errors.New("Could not publish artifacts"))

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.NotEqual(t, err, nil)
}

func TestPublishArtifactsErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	oldLogger := newRemoteLogger
	defer func() { newRemoteLogger = oldLogger }()
	newRemoteLogger = func(key string) (rl *logs.RemoteLogger, err error) {
		log, _ := logs.GetObservedLogger(zap.InfoLevel)
		return &logs.RemoteLogger{BaseLogger: log.Sugar(), Writer: logs.NopWriter()}, nil
	}

	tmpFilePath := "/tmp"
	stepProto := &pb.UnitStep{
		Id: "test3",
		Step: &pb.UnitStep_PublishArtifacts{
			PublishArtifacts: &pb.PublishArtifactsStep{
				Images: []*pb.BuildPublishImage{},
				Files:  []*pb.UploadFile{},
			},
		},
		CallbackToken: callbackToken,
		TaskId:        taskID,
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	mockStep := msteps.NewMockPublishArtifactsStep(ctrl)

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	oldStep := publishArtifactsStep
	defer func() { publishArtifactsStep = oldStep }()
	publishArtifactsStep = func(step *pb.UnitStep, so output.StageOutput,
		log *zap.SugaredLogger) steps.PublishArtifactsStep {
		return mockStep
	}

	mockStep.EXPECT().Run(ctx).Return(nil)

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}

// Client creation failing
func TestCleanupClientErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()
	tmpPath := "/tmp/"
	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "cd . ; ls",
				ContainerPort: port,
			},
		},
	}

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return nil, errors.New("client create error")
	}

	e := NewUnitExecutor(tmpPath, log.Sugar())
	err := e.Cleanup(ctx, step)
	assert.NotNil(t, err)
}

// Failed to send GRPC request
func TestCleanupServerErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()
	tmpPath := "/tmp/"
	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "cd . ; ls",
				ContainerPort: port,
			},
		},
	}

	c := &mockClient{
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

	e := NewUnitExecutor(tmpPath, log.Sugar())
	err := e.Cleanup(ctx, step)
	assert.NotNil(t, err)
}

// Success
func TestCleanupRunStepSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	tmpPath := "/tmp/"
	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Run{
			Run: &pb.RunStep{
				Command:       "cd . ; ls",
				ContainerPort: port,
			},
		},
	}

	c := &mockClient{
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

	e := NewUnitExecutor(tmpPath, log.Sugar())
	err := e.Cleanup(ctx, step)
	assert.Nil(t, err)
}

// Success
func TestCleanupPluginStepSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	tmpPath := "/tmp/"
	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Plugin{
			Plugin: &pb.PluginStep{
				Image:         "plugin/drone-git",
				ContainerPort: port,
			},
		},
	}

	c := &mockClient{
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

	e := NewUnitExecutor(tmpPath, log.Sugar())
	err := e.Cleanup(ctx, step)
	assert.Nil(t, err)
}

// Success
func TestCleanupNotRunStepSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()
	tmpPath := "/tmp/"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
	}

	e := NewUnitExecutor(tmpPath, log.Sugar())
	err := e.Cleanup(ctx, step)
	assert.Nil(t, err)
}

func TestStepSkipSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	log, _ := logs.GetObservedLogger(zap.InfoLevel)

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
		SkipCondition: "true",
	}

	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput,
		isSkipCondition bool, log *zap.SugaredLogger) (map[string]string, error) {
		return nil, nil
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}

func TestStepInvalidSkipCondition(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
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
		SkipCondition: "foo",
	}

	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput,
		isSkipCondition bool, log *zap.SugaredLogger) (map[string]string, error) {
		return nil, nil
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.NotEqual(t, err, nil)
}

func TestStepInvalidJEXLSkipCondition(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
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
		SkipCondition: "${foo.bar${}",
	}

	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput,
		isSkipCondition bool, log *zap.SugaredLogger) (map[string]string, error) {
		return nil, fmt.Errorf("Invalid JEXL")
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.NotEqual(t, err, nil)
}

func TestStepJEXLSkipCondition(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	accountID := "test"
	taskID := "taskID"
	callbackToken := "token"
	expr := "${foo.bar}"
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
		SkipCondition: expr,
	}

	evaluateJEXL = func(ctx context.Context, stepID string, expressions []string, o output.StageOutput,
		isSkipCondition bool, log *zap.SugaredLogger) (map[string]string, error) {
		m := make(map[string]string)
		m[expr] = "true"
		return m, nil
	}

	oldSendStepStatus := sendStepStatus
	defer func() { sendStepStatus = oldSendStepStatus }()
	sendStepStatus = func(ctx context.Context, stepID, endpoint, managerSvcEndpoint, delegateID, accountKey, accountID, callbackToken, taskID string, numRetries int32, timeTaken time.Duration,
		status statuspb.StepExecutionStatus, errMsg string, stepOutput *output.StepOutput, artifact *pb.Artifact, log *zap.SugaredLogger) error {
		return nil
	}

	e := NewUnitExecutor(tmpFilePath, log.Sugar())
	_, err := e.Run(ctx, stepProto, nil, accountID)
	assert.Equal(t, err, nil)
}
