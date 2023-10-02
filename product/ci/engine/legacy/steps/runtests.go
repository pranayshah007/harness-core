// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package steps

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	"github.com/harness/harness-core/commons/go/lib/utils"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/harness/ti-client/types"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

var (
	getWrkspcPath = external.GetWrkspcPath
	getChFiles    = external.GetChangedFiles
)

// RunTestsStep represents interface to execute a runTests step
type runTestsStep struct {
	id            string           // Id of the step
	runTestsInfo  *pb.RunTestsStep // Run tests step information
	step          *pb.UnitStep
	tmpFilePath   string
	containerPort uint32
	so            output.StageOutput // Output variables of the stage
	log           *zap.SugaredLogger // Logger
}

// RunTestsStep represents interface to execute a run step
type RunTestsStep interface {
	Run(ctx context.Context) (*output.StepOutput, int32, error)
}

// NewRunTestsStep creates a run step executor
func NewRunTestsStep(step *pb.UnitStep, tmpFilePath string, so output.StageOutput, log *zap.SugaredLogger) RunTestsStep {
	return &runTestsStep{
		id:           step.GetId(),
		tmpFilePath:  tmpFilePath,
		runTestsInfo: step.GetRunTests(),
		step:         step,
		so:           so,
		log:          log,
	}
}

func (e *runTestsStep) getDiffFiles(ctx context.Context) ([]types.File, error) {
	workspace, err := getWrkspcPath()
	if err != nil {
		return []types.File{}, err
	}
	chFiles, err := getChFiles(ctx, workspace, "", false, e.log, nil) // Legacy code. Using <nil> for procWriter as it's only used for local testing.
	if err != nil {
		e.log.Errorw("failed to get changed filed in runTests step", "step_id", e.id, zap.Error(err))
		return []types.File{}, err
	}

	e.log.Infow(fmt.Sprintf("using changed files list %s to figure out which tests to run", chFiles), "step_id", e.id)
	return chFiles, nil
}

// Run executes tests with provided args with retries and timeout handling
func (e *runTestsStep) Run(ctx context.Context) (*output.StepOutput, int32, error) {
	if err := e.validate(); err != nil {
		e.log.Errorw("failed to validate runTests step", "step_id", e.id, zap.Error(err))
		return nil, int32(1), err
	}
	// TODO: Add JEXL resolution to fields that need to be resolved
	return e.execute(ctx)
}

// validate the container port and language
func (e *runTestsStep) validate() error {
	if e.runTestsInfo.GetContainerPort() == 0 {
		return fmt.Errorf("runTestsStep container port is not set")
	}
	return nil
}

// execute step and send the rpc call to addon server for running tests
func (e *runTestsStep) execute(ctx context.Context) (*output.StepOutput, int32, error) {
	st := time.Now()

	diffFiles, err := e.getDiffFiles(ctx)
	if err != nil {
		return nil, int32(1), err
	}

	addonClient, err := newAddonClient(uint(e.runTestsInfo.GetContainerPort()), e.log)
	if err != nil {
		e.log.Errorw("unable to create CI addon client", "step_id", e.id, zap.Error(err))
		return nil, int32(1), errors.Wrap(err, "could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	c := addonClient.Client()
	// Marshal diff'ed files
	b, err := json.Marshal(diffFiles)
	if err != nil {
		return nil, int32(1), errors.Wrap(err, "could not marshal changed file")
	}

	arg := e.getExecuteStepArg(string(b))
	ret, err := c.ExecuteStep(ctx, arg, grpc_retry.WithMax(maxAddonRetries))
	if err != nil {
		e.log.Errorw("execute run tests step RPC failed", "step_id", e.id, "elapsed_time_ms",
			utils.TimeSince(st), zap.Error(err))
		return nil, int32(1), err
	}
	e.log.Infow("successfully executed run tests step", "elapsed_time_ms", utils.TimeSince(st))
	stepOutput := &output.StepOutput{}
	stepOutput.Output.Variables = ret.GetOutput()
	return stepOutput, ret.GetNumRetries(), nil
}

func (e *runTestsStep) getExecuteStepArg(diffFiles string) *addonpb.ExecuteStepRequest {
	// not the best practice, can take up proxying git calls later
	e.runTestsInfo.DiffFiles = diffFiles
	e.step.Step = &pb.UnitStep_RunTests{
		RunTests: e.runTestsInfo,
	}
	return &addonpb.ExecuteStepRequest{
		Step:        e.step,
		TmpFilePath: e.tmpFilePath,
	}
}
