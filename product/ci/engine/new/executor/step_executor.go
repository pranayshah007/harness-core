// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/signal"
	"syscall"
	"time"

	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/engine/status"
	"go.uber.org/zap"
	grpcstatus "google.golang.org/grpc/status"
)

var (
	sendStepStatus     = status.SendStepStatus
	executeStepOnAddon = ExecuteStepOnAddon
	stopAddon          = StopAddon
)

//go:generate mockgen -source step_executor.go -package=executor -destination mocks/step_executor_mock.go StepExecutor

// StepExecutor represents an interface to execute a unit step
type StepExecutor interface {
	Run(ctx context.Context, step *pb.UnitStep) error
}

type stepExecutor struct {
	tmpFilePath         string // File path to store generated temporary files
	delegateSvcEndpoint string // Delegate service endpoint
	managerSvcEndpoint  string // Manager service endpoint
	delegateID          string //delegate id
	accountKey          string ////account secret key
	log                 *zap.SugaredLogger
	procWriter          io.Writer
}

// NewStepExecutor creates a unit step executor
func NewStepExecutor(tmpFilePath, delegateSvcEndpoint, managerSvcEndpoint, delegateID, accountKey string,
	log *zap.SugaredLogger, procWriter io.Writer) StepExecutor {
	return &stepExecutor{
		tmpFilePath:         tmpFilePath,
		delegateSvcEndpoint: delegateSvcEndpoint,
		managerSvcEndpoint:  managerSvcEndpoint,
		delegateID:          delegateID,
		accountKey:          accountKey,
		log:                 log,
		procWriter:          procWriter,
	}
}

// Executes a unit step and returns whether step execution completed successfully or not.
func (e *stepExecutor) Run(ctx context.Context, step *pb.UnitStep) error {
	start := time.Now()
	e.log.Infow("Step info", "step", step.String(), "step_id", step.GetId())

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, syscall.SIGTERM)
	go func() {
		sig := <-ch
		// On SIGTERM, loggers will also start getting closed so printing in both places for precaution.
		errStr := fmt.Sprintf("Received signal: %s. Canceled step execution.\nPossible reason: Pod was evicted.\n", sig)
		fmt.Printf(errStr)
		e.log.Errorw(errStr, "step_id", step.GetId())
		e.updateStepStatus(context.Background(), step, nil, nil, errors.New(errStr), time.Since(start))
		cancel()
	}()

	stepOutput, artifact, err := e.execute(ctx, step)
	// Stops the addon container if step executed successfully.
	// If step fails, then it can be retried on the same container.
	// Hence, not stopping failed step containers.

	detach := false
	if _, ok := step.GetStep().(*pb.UnitStep_Run); ok {
		detach = step.GetRun().GetDetach()
	}

	if err == nil && !detach {
		stopAddon(context.Background(), step.GetId(), uint32(GetContainerPort(step)), e.log)
	}

	statusErr := e.updateStepStatus(context.Background(), step, stepOutput, artifact, err, time.Since(start))
	if statusErr != nil {
		return statusErr
	}

	return err
}

func (e *stepExecutor) validate(step *pb.UnitStep) error {
	if step.GetId() == "" {
		err := fmt.Errorf("Step ID should be non-empty")
		e.log.Errorw("Step ID is not set", zap.Error(err))
		return err
	}
	if step.GetCallbackToken() == "" {
		err := fmt.Errorf("Callback token should be non-empty")
		e.log.Errorw("Callback token is not set", "step_id", step.GetId(),
			zap.Error(err))
		return err
	}
	if step.GetTaskId() == "" {
		err := fmt.Errorf("Task ID should be non-empty")
		e.log.Errorw("Task ID is not set", "step_id", step.GetId(),
			zap.Error(err))
		return err
	}
	if step.GetAccountId() == "" {
		err := fmt.Errorf("Account ID should be non-empty")
		e.log.Errorw("Account ID is not set", "step_id", step.GetId(),
			zap.Error(err))
		return err
	}
	return nil
}

func (e *stepExecutor) execute(ctx context.Context, step *pb.UnitStep) (
	*output.StepOutput, *pb.Artifact, error) {
	if err := e.validate(step); err != nil {
		return nil, nil, err
	}

	return executeStepOnAddon(ctx, step, e.tmpFilePath, e.log, e.procWriter)
}

func (e *stepExecutor) updateStepStatus(ctx context.Context, step *pb.UnitStep,
	so *output.StepOutput, artifact *pb.Artifact, stepErr error, timeTaken time.Duration) error {
	callbackToken := step.GetCallbackToken()
	taskID := step.GetTaskId()
	stepID := step.GetId()
	accountID := step.GetAccountId()

	stepStatus := statuspb.StepExecutionStatus_SUCCESS
	errMsg := ""
	if stepErr != nil {
		stepStatus = statuspb.StepExecutionStatus_FAILURE
		if errors.Is(stepErr, context.DeadlineExceeded) {
			stepStatus = statuspb.StepExecutionStatus_ABORTED
		}

		errMsg = stepErr.Error()
		if e, ok := grpcstatus.FromError(stepErr); ok {
			errMsg = e.Message()
		}
	}

	err := sendStepStatus(ctx, stepID, e.delegateSvcEndpoint, e.managerSvcEndpoint, e.delegateID, e.accountKey, accountID, callbackToken,
		taskID, int32(1), timeTaken, stepStatus, errMsg, so, artifact, e.log)
	if err != nil {
		e.log.Errorw("Failed to send step status. Failing execution of step",
			"step_id", stepID, "endpoint", e.delegateSvcEndpoint, zap.Error(err))
		return err
	}
	return nil
}
