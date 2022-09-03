// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	caddon "github.com/harness/harness-core/product/ci/addon/grpc/client"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/engine/legacy/jexl"
	"github.com/harness/harness-core/product/ci/engine/legacy/steps"
	"github.com/harness/harness-core/product/ci/engine/logutil"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/harness-core/product/ci/engine/status"
	"github.com/pkg/errors"
	"go.uber.org/zap"
	grpcstatus "google.golang.org/grpc/status"
)

var (
	saveCacheStep        = steps.NewSaveCacheStep
	restoreCacheStep     = steps.NewRestoreCacheStep
	publishArtifactsStep = steps.NewPublishArtifactsStep
	runStep              = steps.NewRunStep
	pluginStep           = steps.NewPluginStep
	runTests             = steps.NewRunTestsStep
	sendStepStatus       = status.SendStepStatus
	newRemoteLogger      = logutil.GetGrpcRemoteLogger
	newAddonClient       = caddon.NewAddonClient
	evaluateJEXL         = jexl.EvaluateJEXL
)

//go:generate mockgen -source unit_executor.go -package=executor -destination mocks/unit_executor_mock.go UnitExecutor

// UnitExecutor represents an interface to execute a unit step
type UnitExecutor interface {
	Run(ctx context.Context, step *pb.UnitStep, so output.StageOutput, accountID string) (*output.StepOutput, error)
	Cleanup(ctx context.Context, step *pb.UnitStep) error
}

type unitExecutor struct {
	tmpFilePath string // File path to store generated temporary files
	log         *zap.SugaredLogger
}

// NewUnitExecutor creates a unit step executor
func NewUnitExecutor(tmpFilePath string, log *zap.SugaredLogger) UnitExecutor {
	return &unitExecutor{
		tmpFilePath: tmpFilePath,
		log:         log,
	}
}

func (e *unitExecutor) validate(step *pb.UnitStep) error {
	//if step.GetId() == "" {
	//	err := fmt.Errorf("Step ID should be non-empty")
	//	e.log.Errorw("Step ID is not set", zap.Error(err))
	//	return err
	//}
	//if step.GetCallbackToken() == "" {
	//	err := fmt.Errorf("Callback token should be non-empty")
	//	e.log.Errorw("Callback token is not set", zap.Error(err))
	//	return err
	//}
	//if step.GetTaskId() == "" {
	//	err := fmt.Errorf("Task ID should be non-empty")
	//	e.log.Errorw("Task ID is not set", zap.Error(err))
	//	return err
	//}
	return nil
}

// Executes a unit step
func (e *unitExecutor) Run(ctx context.Context, step *pb.UnitStep, so output.StageOutput,
	accountID string) (*output.StepOutput, error) {
	start := time.Now()
	e.log.Infow("Step info", "step", step.String(), "step_id", step.GetId())
	skip, err := e.skipStep(ctx, step, so)
	if err != nil {
		e.log.Errorw("failed to evaluate skip condition", zap.Error(err))
		e.updateStepStatus(ctx, step, statuspb.StepExecutionStatus_FAILURE, "", int32(1), nil, accountID, time.Since(start))
		return nil, err
	}

	if skip {
		e.log.Infow("Skipping the step", "step_id", step.GetId())
		statusErr := e.updateStepStatus(ctx, step, statuspb.StepExecutionStatus_SKIPPED, "", int32(1), nil,
			accountID, time.Since(start))
		return nil, statusErr
	}

	stepOutput, numRetries, err := e.execute(ctx, step, so)
	stepStatus := statuspb.StepExecutionStatus_SUCCESS
	errMsg := ""
	if err != nil {
		stepStatus = statuspb.StepExecutionStatus_FAILURE
		errMsg = err.Error()
		if e, ok := grpcstatus.FromError(err); ok {
			errMsg = e.Message()
		}
	}
	statusErr := e.updateStepStatus(ctx, step, stepStatus, errMsg, numRetries, stepOutput, accountID, time.Since(start))
	if statusErr != nil {
		return nil, statusErr
	}
	return stepOutput, err
}

func (e *unitExecutor) updateStepStatus(ctx context.Context, step *pb.UnitStep, stepStatus statuspb.StepExecutionStatus,
	errMsg string, numRetries int32, so *output.StepOutput, accountID string, timeTaken time.Duration) error {
	callbackToken := step.GetCallbackToken()
	taskID := step.GetTaskId()
	stepID := step.GetId()

	err := sendStepStatus(ctx, stepID, "", accountID, callbackToken, taskID, numRetries, timeTaken,
		stepStatus, errMsg, so, nil, e.log)
	if err != nil {
		e.log.Errorw("Failed to send step status. Bailing out stage execution", "step_id", stepID, zap.Error(err))
		return err
	}
	return nil
}

// skipStep checks whether a step needs to be skipped.
// Returns true if a step needs to be skipped. Else false.
func (e *unitExecutor) skipStep(ctx context.Context, step *pb.UnitStep, so output.StageOutput) (bool, error) {
	skipCondition := step.GetSkipCondition()
	if skipCondition == "" {
		return false, nil
	}

	e.log.Infow("Evaluating skip condition", "condition", skipCondition)
	ret, err := evaluateJEXL(ctx, step.GetId(), []string{skipCondition}, so, true, e.log)
	if err != nil {
		return false, errors.Wrap(err, fmt.Sprintf("failed to evalue skip condition: %s", skipCondition))
	}

	resolvedExpr := skipCondition
	if val, ok := ret[skipCondition]; ok {
		resolvedExpr = val
	}

	skip, err := strconv.ParseBool(strings.ToLower(resolvedExpr))
	if err != nil {
		return false, fmt.Errorf("invalid skip condition: %s, resolved expression value: %s", skipCondition, resolvedExpr)
	}

	return skip, nil
}

func (e *unitExecutor) execute(ctx context.Context, step *pb.UnitStep,
	so output.StageOutput) (*output.StepOutput, int32, error) {
	numRetries := int32(1)
	var rl *logs.RemoteLogger
	var err error
	var stepOutput *output.StepOutput

	fs := filesystem.NewOSFileSystem(e.log)
	if err = e.validate(step); err != nil {
		return nil, numRetries, err
	}

	switch x := step.GetStep().(type) {
	case *pb.UnitStep_Run:
		e.log.Infow("Run step info", "step", x.Run.String(), "step_id", step.GetId())
		stepOutput, numRetries, err = runStep(step, e.tmpFilePath, so, e.log).Run(ctx)
		if err != nil {
			return nil, numRetries, err
		}
	case *pb.UnitStep_RunTests:
		e.log.Infow("Test intelligence step info", "step", x.RunTests.String(), "step_id", step.GetId())
		stepOutput, numRetries, err = runTests(step, e.tmpFilePath, so, e.log).Run(ctx)
		if err != nil {
			return nil, numRetries, err
		}
	case *pb.UnitStep_Plugin:
		e.log.Infow("Plugin step info", "step", x.Plugin.String(), "step_id", step.GetId())
		stepOutput, numRetries, err = pluginStep(step, e.tmpFilePath, so, e.log).Run(ctx)
		if err != nil {
			return nil, numRetries, err
		}
	case *pb.UnitStep_SaveCache:
		rl, err = newRemoteLogger(step.GetLogKey())
		if err != nil {
			return nil, numRetries, err
		}
		defer rl.Writer.Close()
		e.log.Infow("Save cache step info", "step", x.SaveCache.String(), "step_id", step.GetId())
		stepOutput, err = saveCacheStep(step, e.tmpFilePath, so, fs, rl.BaseLogger).Run(ctx)
		if err != nil {
			return nil, numRetries, err
		}
	case *pb.UnitStep_RestoreCache:
		rl, err = newRemoteLogger(step.GetLogKey())
		if err != nil {
			return nil, numRetries, err
		}
		defer rl.Writer.Close()
		e.log.Infow("Restore cache step info", "step", x.RestoreCache.String(), "step_id", step.GetId())
		if err = restoreCacheStep(step, e.tmpFilePath, so, fs, rl.BaseLogger).Run(ctx); err != nil {
			return nil, numRetries, err
		}
	case *pb.UnitStep_PublishArtifacts:
		rl, err = newRemoteLogger(step.GetLogKey())
		if err != nil {
			return nil, numRetries, err
		}
		defer rl.Writer.Close()
		e.log.Infow("Publishing artifact info", "step", x.PublishArtifacts.String(), "step_id", step.GetId())
		if err = publishArtifactsStep(step, so, rl.BaseLogger).Run(ctx); err != nil {
			return nil, numRetries, err
		}
	case nil:
		e.log.Infow("Field is not set", "step", x)
	default:
		return nil, numRetries, fmt.Errorf("UnitStep has unexpected type %T", x)
	}

	return stepOutput, numRetries, nil
}

func (e *unitExecutor) Cleanup(ctx context.Context, step *pb.UnitStep) error {
	switch x := step.GetStep().(type) {
	case *pb.UnitStep_Run:
		port := x.Run.GetContainerPort()
		return e.stopAddonServer(ctx, step.GetId(), uint(port))
	case *pb.UnitStep_RunTests:
		port := x.RunTests.GetContainerPort()
		return e.stopAddonServer(ctx, step.GetId(), uint(port))
	case *pb.UnitStep_Plugin:
		port := x.Plugin.GetContainerPort()
		return e.stopAddonServer(ctx, step.GetId(), uint(port))
	}
	return nil
}

// Stops CI-Addon GRPC server
func (e *unitExecutor) stopAddonServer(ctx context.Context, stepID string, port uint) error {
	addonClient, err := newAddonClient(port, e.log)
	if err != nil {
		e.log.Errorw("Could not create CI Addon client", "step_id", stepID, zap.Error(err))
		return errors.Wrap(err, "Could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	_, err = addonClient.Client().SignalStop(ctx, &addonpb.SignalStopRequest{})
	if err != nil {
		e.log.Errorw("Unable to send Stop server request", "step_id", stepID, zap.Error(err))
		return errors.Wrap(err, "Could not send stop server request")
	}
	return nil
}
