// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package steps

import (
	"context"
	"fmt"
	"time"

	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	"github.com/harness/harness-core/commons/go/lib/utils"
	caddon "github.com/harness/harness-core/product/ci/addon/grpc/client"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/engine/legacy/jexl"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

//go:generate mockgen -source run.go -package=steps -destination mocks/run_mock.go RunStep

const (
	maxAddonRetries = 2000 // max retry time of 200 seconds
)

var (
	evaluateJEXL   = jexl.EvaluateJEXL
	newAddonClient = caddon.NewAddonClient
)

// RunStep represents interface to execute a run step
type RunStep interface {
	Run(ctx context.Context) (*output.StepOutput, int32, error)
}

type runStep struct {
	id            string
	command       string
	step          *pb.UnitStep
	tmpFilePath   string
	containerPort uint32
	stageOutput   output.StageOutput
	log           *zap.SugaredLogger
}

// NewRunStep creates a run step executor
func NewRunStep(step *pb.UnitStep, tmpFilePath string, so output.StageOutput,
	log *zap.SugaredLogger) RunStep {
	r := step.GetRun()
	return &runStep{
		id:            step.GetId(),
		command:       r.GetCommand(),
		step:          step,
		containerPort: r.GetContainerPort(),
		tmpFilePath:   tmpFilePath,
		stageOutput:   so,
		log:           log,
	}
}

// Executes customer provided run step commands with retries and timeout handling
func (e *runStep) Run(ctx context.Context) (*output.StepOutput, int32, error) {
	if err := e.validate(); err != nil {
		e.log.Errorw("failed to validate run step", "step_id", e.id, zap.Error(err))
		return nil, int32(1), err
	}
	return e.execute(ctx)
}

func (e *runStep) validate() error {
	if len(e.command) == 0 {
		err := fmt.Errorf("command in run step should be non-empty string")
		return err
	}
	if e.containerPort == 0 {
		err := fmt.Errorf("run step container port is not set")
		return err
	}
	return nil
}

func (e *runStep) execute(ctx context.Context) (*output.StepOutput, int32, error) {
	st := time.Now()

	addonClient, err := newAddonClient(uint(e.containerPort), e.log)
	if err != nil {
		e.log.Errorw("Unable to create CI addon client", "step_id", e.id, zap.Error(err))
		return nil, int32(1), errors.Wrap(err, "Could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	c := addonClient.Client()
	arg := e.getExecuteStepArg()
	ret, err := c.ExecuteStep(ctx, arg, grpc_retry.WithMax(maxAddonRetries))
	if err != nil {
		stepOutput := &output.StepOutput{}
    	stepOutput.Output.Variables = ret.GetOutput()
		e.log.Errorw("Execute run step RPC failed", "step_id", e.id, "elapsed_time_ms", utils.TimeSince(st), zap.Error(err))
		return stepOutput, int32(1), err
	}
	e.log.Infow("Successfully executed step", "elapsed_time_ms", utils.TimeSince(st))
	stepOutput := &output.StepOutput{}
	stepOutput.Output.Variables = ret.GetOutput()
	return stepOutput, ret.GetNumRetries(), nil
}

func (e *runStep) getExecuteStepArg() *addonpb.ExecuteStepRequest {
	prevStepOutputs := make(map[string]*pb.StepOutput)
	for stepID, stepOutput := range e.stageOutput {
		if stepOutput != nil {
			prevStepOutputs[stepID] = &pb.StepOutput{Output: stepOutput.Output.Variables}
		}
	}

	return &addonpb.ExecuteStepRequest{
		Step:            e.step,
		TmpFilePath:     e.tmpFilePath,
		PrevStepOutputs: prevStepOutputs,
	}
}
