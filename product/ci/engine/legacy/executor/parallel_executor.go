// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"time"

	statuspb "github.com/harness/harness-core/910-delegate-task-grpc-service/src/main/proto/io/harness/task/service"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

//go:generate mockgen -source parallel_executor.go -package=executor -destination mocks/parallel_executor_mock.go ParallelExecutor

type stepStatus int

const (
	pending stepStatus = iota
	completed
)

// ParallelExecutor represents an interface to execute a parallel step
type ParallelExecutor interface {
	Run(ctx context.Context, step *pb.ParallelStep, so output.StageOutput, accountID string) (map[string]*output.StepOutput, error)
	Cleanup(ctx context.Context, ps *pb.ParallelStep) error
}

type parallelExecutor struct {
	tmpFilePath  string // File path to store generated temporary files
	log          *zap.SugaredLogger
	unitExecutor UnitExecutor
}

type unitStepResponse struct {
	stepID     string
	stepOutput *output.StepOutput
	err        error
}

// NewParallelExecutor creates a parallel step executor
func NewParallelExecutor(tmpFilePath string, log *zap.SugaredLogger) ParallelExecutor {
	unitExecutor := NewUnitExecutor(tmpFilePath, log)
	return &parallelExecutor{
		tmpFilePath:  tmpFilePath,
		unitExecutor: unitExecutor,
		log:          log,
	}
}

// Executes a parallel step by executing all the steps in parallel.
func (e *parallelExecutor) Run(ctx context.Context, ps *pb.ParallelStep, so output.StageOutput,
	accountID string) (map[string]*output.StepOutput, error) {
	start := time.Now()
	if err := e.validate(ps); err != nil {
		return nil, err
	}

	stepStatusByID := make(map[string]stepStatus)
	// Figure out the run step tasks to run in parallel
	numSteps := len(ps.GetSteps())
	tasks := make(chan *pb.UnitStep, numSteps)
	results := make(chan unitStepResponse, numSteps)
	for _, step := range ps.GetSteps() {
		s := step
		stepStatusByID[s.GetId()] = pending
		go func() {
			stepOutput, err := e.unitExecutor.Run(ctx, s, so, accountID)
			results <- unitStepResponse{
				stepID:     s.GetId(),
				stepOutput: stepOutput,
				err:        err,
			}
		}()
	}
	close(tasks)

	stepOutputByID := make(map[string]*output.StepOutput)
	// Evaluate the parallel step results
	for i := 0; i < numSteps; i++ {
		result := <-results
		stepStatusByID[result.stepID] = completed
		if result.err != nil {
			e.log.Errorw(
				"failed to execute parallel step",
				"step_id", result.stepID,
				"elapsed_time_ms", utils.TimeSince(start),
				zap.Error(result.err),
			)
			e.sendAbortToPendingSteps(ctx, stepStatusByID, ps.GetSteps(), start, accountID)
			return nil, result.err
		}

		stepOutputByID[result.stepID] = result.stepOutput
	}

	e.log.Infow(
		"Successfully executed parallel step", "elapsed_time_ms", utils.TimeSince(start),
	)
	return stepOutputByID, nil
}

// Sends the abort stepStatus to all the pending steps.
func (e *parallelExecutor) sendAbortToPendingSteps(ctx context.Context, stepStatusByID map[string]stepStatus, steps []*pb.UnitStep,
	startTime time.Time, accountID string) error {
	stepByID := make(map[string]*pb.UnitStep)
	for _, s := range steps {
		stepByID[s.GetId()] = s
	}

	for stepID, status := range stepStatusByID {
		step, ok := stepByID[stepID]
		if !ok {
			e.log.Warnw("Step not present for ID. This should not happen", "step_id", stepID)
			continue
		}
		if status == pending {
			callbackToken := step.GetCallbackToken()
			taskID := step.GetTaskId()
			stepID := step.GetId()

			err := sendStepStatus(ctx, stepID, "", "", "", "", accountID, callbackToken, taskID, int32(1), time.Since(startTime),
				statuspb.StepExecutionStatus_ABORTED, "", nil, nil, e.log)
			if err != nil {
				e.log.Errorw("Failed to send abort step stepStatus to pending step in parallel section", "step_id", stepID, zap.Error(err))
				return err
			}
		}
	}
	return nil
}

// Validates parallel step
func (e *parallelExecutor) validate(ps *pb.ParallelStep) error {
	if ps.GetId() == "" {
		err := errors.New("Parallel step ID should be non-empty")
		e.log.Errorw("Parallel step ID is not set", zap.Error(err))
		return err
	}
	return nil
}

func (e *parallelExecutor) Cleanup(ctx context.Context, ps *pb.ParallelStep) error {
	var cleanupErr error
	for _, step := range ps.GetSteps() {
		err := e.unitExecutor.Cleanup(ctx, step)
		if err != nil {
			cleanupErr = err
		}
	}
	return cleanupErr
}
