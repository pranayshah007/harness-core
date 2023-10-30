// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

/*
	CI-addon is an entrypoint for run step & plugin step container images. It executes a step on receiving GRPC.
*/
import (
	"fmt"
	"os"

	"github.com/alexflint/go-arg"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/commons/go/lib/metrics"
	"github.com/harness/harness-core/product/ci/addon/grpc"
	addonlogs "github.com/harness/harness-core/product/ci/addon/logs"
	"github.com/harness/harness-core/product/ci/addon/services"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/logutil"
	"go.uber.org/zap"
)

const (
	applicationName = "CI-addon"
	deployable      = "ci-addon"
)

var (
	addonServer         = grpc.NewAddonServer
	newGrpcRemoteLogger = logutil.GetGrpcRemoteLogger
	newIntegrationSvc   = services.NewIntegrationSvc
	getLogKey           = external.GetLogKey
	getServiceLogKey    = external.GetServiceLogKey
)

// schema for running functional test service
type service struct {
	ID         string   `arg:"--id, required" help:"Service ID"`
	Image      string   `arg:"--image, required" help:"docker image name for the service"`
	Entrypoint []string `arg:"env:HARNESS_SERVICE_ENTRYPOINT" help:"entrypoint for the service"`
	Args       []string `arg:"env:HARNESS_SERVICE_ARGS" help:"arguments for the service"`
}

var args struct {
	Service *service `arg:"subcommand:service" help:"integration service arguments"`

	Port                  uint   `arg:"--port, required" help:"port for running GRPC server"`
	Verbose               bool   `arg:"--verbose" help:"enable verbose logging mode"`
	LogMetrics            bool   `arg:"--log_metrics" help:"enable metric logging"`
	Deployment            string `arg:"env:DEPLOYMENT" help:"name of the deployment"`
	DeploymentEnvironment string `arg:"env:DEPLOYMENT_ENVIRONMENT" help:"environment of the deployment"`
}

func parseArgs() {
	// set defaults here
	args.DeploymentEnvironment = "prod"
	args.Verbose = false
	args.LogMetrics = true
	args.Port = 20002

	arg.MustParse(&args)
}

func init() {
	//TODO: perform any initialization
}

func main() {
	parseArgs()

	lc := external.LogCloser()
	lc.Run()

	// Addon logs not part of a step go to addon:<port>
	logState := addonlogs.LogState()
	pendingLogs := logState.PendingLogs()

	remoteLogger := getRemoteLogger(fmt.Sprintf("addon:%d", args.Port))
	pendingLogs <- remoteLogger
	log := remoteLogger.BaseLogger

	if args.LogMetrics {
		metrics.Log(int32(os.Getpid()), "addon", log)
	}

	var serviceLogger *logs.RemoteLogger
	// Start integration test service in a separate goroutine
	if args.Service != nil {
		// create logger for service logs
		serviceLogger = getSvcRemoteLogger()
		pendingLogs <- serviceLogger

		svc := args.Service
		go func() {
			newIntegrationSvc(svc.ID, svc.Image, svc.Entrypoint, svc.Args, serviceLogger.BaseLogger,
				serviceLogger.Writer, false, log).Run()
		}()
	}

	log.Infow("Starting CI addon server", "port", args.Port)
	s, err := addonServer(args.Port, args.LogMetrics, log)
	if err != nil {
		log.Errorw("error while running CI addon server", "port", args.Port, "error_msg", zap.Error(err))
		addonlogs.LogState().ClosePendingLogs()
		os.Exit(1) // Exit addon with exit code 1
	}

	// Wait for stop signal and shutdown the server upon receiving it in a separate goroutine
	go s.Stop()
	if err := s.Start(); err != nil {
		addonlogs.LogState().ClosePendingLogs()
		os.Exit(1) // Exit addon with exit code 1
	}
}

func getRemoteLogger(keyID string) *logs.RemoteLogger {
	key, err := getLogKey(keyID)
	if err != nil {
		panic(err)
	}
	remoteLogger, err := newGrpcRemoteLogger(key)
	if err != nil {
		// Could not create a logger
		panic(err)
	}

	lc := external.LogCloser()
	lc.Add(remoteLogger)

	return remoteLogger
}

func getSvcRemoteLogger() *logs.RemoteLogger {
	key, err := getServiceLogKey()
	if err != nil {
		panic(err)
	}

	remoteLogger, err := newGrpcRemoteLogger(key)
	if err != nil {
		panic(err)
	}

	lc := external.LogCloser()
	lc.Add(remoteLogger)

	return remoteLogger
}
