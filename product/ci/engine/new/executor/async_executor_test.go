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

	"github.com/golang/mock/gomock"
	"github.com/harness/harness-core/commons/go/lib/logs"
	mexecutor "github.com/harness/harness-core/product/ci/engine/new/executor/mocks"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

func TestExecuteStepInAsync(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.ExecuteStepRequest{
		ExecutionId: "test",
		Step: &pb.UnitStep{
			Id: "test2",
			Step: &pb.UnitStep_Run{
				Run: &pb.RunStep{
					Command: "ls",
				},
			},
			ContainerPort: uint32(8000),
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	ExecuteStepInAsync(ctx, arg, log.Sugar(), new(bytes.Buffer))
}

func TestExecuteStepSuccess(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	arg := &pb.ExecuteStepRequest{
		ExecutionId: "test",
		Step: &pb.UnitStep{
			Id: "test2",
			Step: &pb.UnitStep_Run{
				Run: &pb.RunStep{
					Command: "ls",
				},
			},
			ContainerPort: uint32(8000),
		},
	}

	mockStepExecutor := mexecutor.NewMockStepExecutor(ctrl)
	mockStepExecutor.EXPECT().Run(gomock.Any(), gomock.Any()).AnyTimes().Return(nil)

	oldStepExecutor := newStepExecutor
	defer func() { newStepExecutor = oldStepExecutor }()
	newStepExecutor = func(tmpFilePath, delegateSvcEndpoint, managerSvcEndpoint, delegateID, accountKey string, log *zap.SugaredLogger, buf io.Writer) StepExecutor {
		return mockStepExecutor
	}
	executeStep(arg, log.Sugar(), new(bytes.Buffer))
}

func TestExecuteStepFail(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	arg := &pb.ExecuteStepRequest{
		ExecutionId: "test",
		Step: &pb.UnitStep{
			Id: "test2",
			Step: &pb.UnitStep_Run{
				Run: &pb.RunStep{
					Command: "ls",
				},
			},
			ContainerPort: uint32(8000),
		},
	}

	mockStepExecutor := mexecutor.NewMockStepExecutor(ctrl)
	mockStepExecutor.EXPECT().Run(gomock.Any(), gomock.Any()).AnyTimes().Return(errors.New("failed"))

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	oldStepExecutor := newStepExecutor
	defer func() { newStepExecutor = oldStepExecutor }()
	newStepExecutor = func(tmpFilePath, delegateSvcEndpoint, managerSvcEndpoint, delegateID, accountKey string, log *zap.SugaredLogger, buf io.Writer) StepExecutor {
		return mockStepExecutor
	}
	executeStep(arg, log.Sugar(), new(bytes.Buffer))
}
