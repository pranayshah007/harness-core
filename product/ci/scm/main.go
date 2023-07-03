// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

/*
	scm service performs the following actions
*/
import (
	"github.com/alexflint/go-arg"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/product/ci/scm/grpc"
	"go.uber.org/zap"
)

const (
	applicationName = "CI-scm"
	deployable      = "ci-scm"
	port            = 8091
)

var (
	scmServer     = grpc.NewSCMServer
	Version       = "0.0.0"
	BuildCommitID = "NA"
)

type args struct {
	Verbose    bool   `arg:"--verbose" help:"enable verbose logging mode"`
	Port       uint   `arg:"--port" help:"port for running GRPC server"`
	UnixSocket string `arg:"--unix" help:"the unix socket to run on"`

	Deployment            string `arg:"env:DEPLOYMENT" help:"name of the deployment"`
	DeploymentEnvironment string `arg:"env:DEPLOYMENT_ENVIRONMENT" help:"environment of the deployment"`
}

func (args) Version() string {
	return Version
}

func parseArgs(a *args) {
	// set defaults here
	a.DeploymentEnvironment = "prod"
	a.Port = port
	a.Verbose = false
	a.UnixSocket = ""

	arg.MustParse(a)
}

func main() {
	var args args
	parseArgs(&args)

	// build initial log
	logBuilder := logs.NewBuilder().Verbose(args.Verbose).WithDeployment(args.Deployment).
		WithFields("deployable", deployable,
			"application_name", applicationName)
	logger := logBuilder.MustBuild().Sugar()
	logger.Infow("Starting CI GRPC scm server", "version", Version, "buildCommitID", BuildCommitID, "port", args.Port, "unixSocket", args.UnixSocket)
	s, err := scmServer(args.Port, args.UnixSocket, logger)
	if err != nil {
		logger.Fatalw("error while running CI GRPC scm server", "port", args.Port, "unixSocket", args.UnixSocket, zap.Error(err))
	}
	// Wait for stop signal and shutdown the server upon receiving it in a separate goroutine
	go s.Stop()
	s.Start()
}
