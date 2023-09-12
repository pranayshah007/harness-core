// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"fmt"
	"time"

	"github.com/harness/harness-core/commons/go/lib/logs"
	addonlogs "github.com/harness/harness-core/product/ci/addon/logs"
	pb "github.com/harness/harness-core/product/ci/addon/proto"
	"github.com/harness/harness-core/product/ci/addon/tasks"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/logutil"
	enginepb "github.com/harness/harness-core/product/ci/engine/proto"
	"go.uber.org/zap"
	"golang.org/x/net/context"
)

// handler is used to implement AddonServer
type handler struct {
	stopCh     chan bool
	logMetrics bool
	log        *zap.SugaredLogger
}

var (
	newGrpcRemoteLogger = logutil.GetGrpcRemoteLogger
	newRunTask          = tasks.NewRunTask
	newRunTestsTask     = tasks.NewRunTestsTask
	newPluginTask       = tasks.NewPluginTask
)

// NewAddonHandler returns a GRPC handler that implements pb.AddonServer
func NewAddonHandler(stopCh chan bool, logMetrics bool, log *zap.SugaredLogger) pb.AddonServer {
	return &handler{stopCh, logMetrics, log}
}

// SignalStop sends a signal to stop the GRPC service.
func (h *handler) SignalStop(ctx context.Context, in *pb.SignalStopRequest) (*pb.SignalStopResponse, error) {

	go func() {
		h.log.Infow("sending stop signal to the channel")
		// Ensure that all the addon service tasks are complete before sending the signal.
		// Sleep will ensure that this RPC completes successfully
		time.Sleep(1 * time.Second)
		h.stopCh <- true
		h.log.Infow("successfully sent stop signal to the channel")
	}()
	// Explicitly close pending logs before returning back, as they depend on the lite engine
	// server being up.
	h.log.Infow("attempting to close pending logs")
	addonlogs.LogState().ClosePendingLogs()
	h.log.Infow("successfully closed pending logs")
	return &pb.SignalStopResponse{}, nil
}

// ExecuteStep executes a unit step.
func (h *handler) ExecuteStep(ctx context.Context, in *pb.ExecuteStepRequest) (*pb.ExecuteStepResponse, error) {
	rl, err := newGrpcRemoteLogger(in.GetStep().GetLogKey())
	if err != nil {
		return &pb.ExecuteStepResponse{}, err
	}

	lc := external.LogCloser()
	lc.Add(rl)

	h.log.Infow("Executing step", "arg", in)

	switch x := in.GetStep().GetStep().(type) {
	case *enginepb.UnitStep_Run:
		detach := in.GetStep().GetRun().GetDetach()
		stepOutput, numRetries, err := newRunTask(in.GetStep(), in.GetPrevStepOutputs(), in.GetTmpFilePath(), rl.BaseLogger,
			rl.Writer, false, h.log).Run(ctx)
		// close log stream only if detach == false
		if !detach {
			h.log.Infow("closing the stream after Run step execution", "step_id", in.GetStep().GetId())
			err = close(rl.Writer, err)
			h.log.Infow("closed the stream after Run step execution", "step_id", in.GetStep().GetId())
		}
		response := &pb.ExecuteStepResponse{
			Output:     stepOutput,
			NumRetries: numRetries,
		}
		return response, err
	case *enginepb.UnitStep_RunTests:
		stepOutput, numRetries, err := newRunTestsTask(in.GetStep(), in.GetTmpFilePath(), rl.BaseLogger, rl.Writer, false, h.log).Run(ctx)
		response := &pb.ExecuteStepResponse{
			Output:     stepOutput,
			NumRetries: numRetries,
		}
		h.log.Infow("closing the stream after RunTests step execution", "step_id", in.GetStep().GetId())
		err = close(rl.Writer, err)
		h.log.Infow("closed the stream after RunTests step execution", "step_id", in.GetStep().GetId())
		return response, err
	case *enginepb.UnitStep_Plugin:
		stepOutput, artifact, numRetries, err := newPluginTask(in.GetStep(), in.GetPrevStepOutputs(), in.GetTmpFilePath(), rl.BaseLogger, rl.Writer, false, h.log).Run(ctx)
		response := &pb.ExecuteStepResponse{
			Artifact:   artifact,
			Output:     stepOutput,
			NumRetries: numRetries,
		}
		h.log.Infow("closing the stream after Plugin step execution", "step_id", in.GetStep().GetId())
		err = close(rl.Writer, err)
		h.log.Infow("closed the stream after Plugin step execution", "step_id", in.GetStep().GetId())
		return response, err
	case nil:
		return &pb.ExecuteStepResponse{}, fmt.Errorf("UnitStep is not set")
	default:
		return &pb.ExecuteStepResponse{}, fmt.Errorf("UnitStep has unexpected type %T", x)
	}
}

// Close log stream and enhance the error message if failures were encountered while
// trying to close the log stream.
func close(w logs.StreamWriter, err error) error {
	logErr := w.Close()
	// Try to improve the error message by adding any context we found in the logs
	if err != nil && w.Error() != nil {
		err = fmt.Errorf("%w\n\n%s", err, w.Error().Error())
	}
	// If log upload fails, the build should be marked as failure
	if logErr != nil {
		if err != nil {
			// Wrap error with log upload error
			err = fmt.Errorf("%w\n%s", err, logErr)
		} else {
			err = logErr
		}
	}
	return err
}
