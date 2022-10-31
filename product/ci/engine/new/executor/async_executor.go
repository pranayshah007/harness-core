// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"io"

	"github.com/harness/harness-core/product/ci/engine/new/state"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"go.uber.org/zap"
)

var (
	newStepExecutor = NewStepExecutor
)

// ExecuteStepInAsync executes a step asynchronously.
// It starts execution of a step in a goroutine. Status of step is sent to
// delegate agent service after execution of step finishes.
func ExecuteStepInAsync(ctx context.Context, in *pb.ExecuteStepRequest,
	log *zap.SugaredLogger, procWriter io.Writer) {
	log.Infow("Received step for execution", "in", in)
	go func() {
		s := state.ExecutionState()
		executionID := in.GetExecutionId()
		if s.CanRun(executionID) {
			executeStep(in, log, procWriter)
		} else {
			log.Infow("Job is already running with same execution ID",
				"id", executionID, "arg", in)
		}
	}()
}

// Execute a step
func executeStep(in *pb.ExecuteStepRequest, log *zap.SugaredLogger, procWriter io.Writer) {
	ctx := context.Background()
	e := newStepExecutor(in.GetTmpFilePath(), in.GetDelegateSvcEndpoint(), in.GetManagerSvcEndpoint(), in.GetDelegateId(), in.GetAccountKey(), log, procWriter)
	err := e.Run(ctx, in.GetStep())
	if err != nil {
		log.Errorw("Job failed with execution ID",
			"id", in.GetExecutionId(), "arg", in, zap.Error(err))
	} else {
		log.Infow("Successfully finished job with execution ID",
			"id", in.GetExecutionId(), "arg", in)
	}
}
