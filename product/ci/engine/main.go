// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

/*
	CI lite engine executes steps of stage provided as an input.
*/
import (
	"os"

	"github.com/alexflint/go-arg"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/commons/go/lib/metrics"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/consts"
	"github.com/harness/harness-core/product/ci/engine/grpc"
	"github.com/harness/harness-core/product/ci/engine/legacy/executor"
	"go.uber.org/zap"
)

const (
	applicationName = "CI-lite-engine"
	deployable      = "ci-lite-engine"
)

var (
	executeStage        = executor.ExecuteStage
	newHTTPRemoteLogger = external.GetHTTPRemoteLogger
	engineServer        = grpc.NewEngineServer
	getLogKey           = external.GetLogKey
)

// schema for executing a stage
type stageSchema struct {
	Input        string `arg:"--input, required" help:"base64 format of stage to execute"`
	TmpFilePath  string `arg:"--tmppath, required" help:"relative file path to store temporary files"`
	ServicePorts []uint `arg:"--svc_ports" help:"grpc service ports of integration service containers"`
	Debug        bool   `arg:"--debug" help:"Enables debug mode for checking run step logs by not exiting CI-addon"`
}

var args struct {
	Stage *stageSchema `arg:"subcommand:stage"`

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

	arg.MustParse(&args)
}

func init() {
	//TODO: perform any initialization
}

func main() {
	parseArgs()

	// Lite engine logs that are not part of any step are logged with ID engine:main
	remoteLogger := getRemoteLogger("engine:main")
	log := remoteLogger.BaseLogger
	logs.InitLogger(log)
	procWriter := remoteLogger.Writer
	defer procWriter.Close() // upload the logs to object store and close the stream

	if args.LogMetrics {
		metrics.Log(int32(os.Getpid()), "engine", log)
	}

	if args.Stage != nil {
		// Starting stage execution
		startServer(remoteLogger, true)
		log.Infow("Starting stage execution")
		err := executeStage(args.Stage.Input, args.Stage.TmpFilePath, args.Stage.ServicePorts, args.Stage.Debug, log)
		if err != nil {
			remoteLogger.Writer.Close()
			os.Exit(1) // Exit the lite engine with status code of 1
		}
		log.Infow("CI lite engine completed execution, now exiting")
	} else {
		// Starts the grpc server and waits for ExecuteStep grpc call to execute a step.
		startServer(remoteLogger, false)
	}
}

// starts grpc server in background
func startServer(rl *logs.RemoteLogger, background bool) {
	log := rl.BaseLogger
	procWriter := rl.Writer

	log.Infow("Starting CI engine server", "port", consts.LiteEnginePort)
	s, err := engineServer(consts.LiteEnginePort, log, procWriter)
	if err != nil {
		log.Errorw("error on running CI engine server", "port", consts.LiteEnginePort, "error_msg", zap.Error(err))
		rl.Writer.Close()
		os.Exit(1) // Exit engine with exit code 1
	}

	if background {
		// Start grpc server in separate goroutine. It will cater to pausing/resuming stage execution.
		go func() {
			if err := s.Start(); err != nil {
				log.Errorw("error in CI engine grpc server", "port", consts.LiteEnginePort, "error_msg", zap.Error(err))
				rl.Writer.Close()
			}
		}()
	} else {
		if err := s.Start(); err != nil {
			log.Errorw("error in CI engine grpc server", "port", consts.LiteEnginePort, "error_msg", zap.Error(err))
			rl.Writer.Close()
			os.Exit(1) // Exit engine with exit code 1
		}
	}
}

func getRemoteLogger(keyID string) *logs.RemoteLogger {
	key, err := getLogKey(keyID)
	if err != nil {
		panic(err)
	}
	remoteLogger, err := newHTTPRemoteLogger(key)
	if err != nil {
		// Could not create a logger
		panic(err)
	}

	lc := external.LogCloser()
	lc.Run()
	lc.Add(remoteLogger)

	return remoteLogger
}
