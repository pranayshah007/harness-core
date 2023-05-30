// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package executor

import (
	"context"
	"io"
	"time"

	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	"github.com/harness/harness-core/commons/go/lib/utils"
	caddon "github.com/harness/harness-core/product/ci/addon/grpc/client"
	addonpb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/engine/new/executor/runtests"
	"github.com/harness/harness-core/product/ci/engine/output"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

const (
	maxAddonRetries = 100 // max retry time of 10 seconds
)

var (
	newAddonClient = caddon.NewAddonClient
)

// ExecuteStepOnAddon executes customer provided step on addon
func ExecuteStepOnAddon(ctx context.Context, step *pb.UnitStep, tmpFilePath string,
	log *zap.SugaredLogger, procWriter io.Writer) (*output.StepOutput, *pb.Artifact, error) {
	// execute runtest step
	if _, ok := step.GetStep().(*pb.UnitStep_RunTests); ok {
		stepOutput, _, err := runtests.NewRunTestsStep(step, tmpFilePath, nil, log, procWriter).Run(ctx)
		return stepOutput, nil, err
	}

	st := time.Now()

	stepID := step.GetId()

	addonClient, err := newAddonClient(GetContainerPort(step), log)
	if err != nil {
		log.Errorw("Unable to create CI addon client", "step_id", stepID,
			"elapsed_time_ms", utils.TimeSince(st), zap.Error(err))
		return nil, nil, errors.Wrap(err, "Could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	c := addonClient.Client()
	arg := &addonpb.ExecuteStepRequest{
		Step:        step,
		TmpFilePath: tmpFilePath,
	}
	ret, err := c.ExecuteStep(ctx, arg, grpc_retry.WithMax(maxAddonRetries))
	if err != nil {
		log.Errorw("Execute step RPC failed", "step_id", stepID,
			"elapsed_time_ms", utils.TimeSince(st), zap.Error(err))
		return nil, nil, err
	}

	log.Infow("Successfully executed step", "step_id", stepID,
		"elapsed_time_ms", utils.TimeSince(st))
	stepOutput := &output.StepOutput{}
	stepOutput.Output.Variables = ret.GetOutput()
	artifact := ret.GetArtifact()
	return stepOutput, artifact, nil
}

// StopAddon stops addon grpc service running on specified port
func StopAddon(ctx context.Context, stepID string, port uint32, log *zap.SugaredLogger) error {
	addonClient, err := newAddonClient(uint(port), log)
	if err != nil {
		log.Errorw("Could not create CI Addon client", "step_id", stepID, zap.Error(err))
		return errors.Wrap(err, "Could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	_, err = addonClient.Client().SignalStop(ctx, &addonpb.SignalStopRequest{})
	if err != nil {
		log.Errorw("Unable to send Stop server request", "step_id", stepID, zap.Error(err))
		return errors.Wrap(err, "Could not send stop server request")
	}

	log.Infow("Successfully stopped step container", "step_id", stepID, "port", port)
	return nil
}
