// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

/*
	CI lite engine executes steps of stage provided as an input.
*/
import (
	"encoding/base64"
	"fmt"
	"os"

	"github.com/alexflint/go-arg"
	"github.com/golang/protobuf/proto"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/harness/harness-core/commons/go/lib/metrics"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/consts"
	"github.com/harness/harness-core/product/ci/engine/grpc"
	"github.com/harness/harness-core/product/ci/engine/legacy/executor"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
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

func generate() string {
	// paths := []string{"/Users/vistaarjuneja/Downloads/step-exec/workspace/190-deployment-functional-tests",
	// "/Users/vistaarjuneja/Downloads/step-exec/workspace/200-functional-test"}
	command := `
pwd
cd ~/step-exec/.harness/tmp
rm -rf jhttp-rutvij
git clone https://github.com/rutvijmehta-harness/jhttp-rutvij.git
cd jhttp-rutvij
ls
sleep 100
pwd
`
	runStep := &pb.UnitStep_Run{
		Run: &pb.RunStep{
			//Command:       "echo $HARNESS_ACCOUNT_ID\nrm -rf harness-core\ngit clone https://github.com/harness/harness-core.git\ncd harness-core\ngit branch\npwd\n",
			Command:       command,
			ContainerPort: 8081,
		},
	}
	step0 := &pb.UnitStep{
		Id:          "step4",
		DisplayName: "display_name",
		Step:        runStep,
		LogKey:      "omg2",
	}
	var steps []*pb.Step
	steps = append(steps, &pb.Step{Step: &pb.Step_Unit{Unit: step0}})
	execution := &pb.Execution{
		Steps: steps,
	}
	data, err := proto.Marshal(execution)
	if err != nil {
		fmt.Println("marshaling error: %v", err)
	}
	encoded := base64.StdEncoding.EncodeToString(data)
	return encoded
}

func generateRunTests() string {
	runTestsStep := &pb.UnitStep_RunTests{
		RunTests: &pb.RunTestsStep{
			Args:                 " -Dmaven.repo.local=/Users/rutvijmehta/Desktop/harness/jhttp-rutvij test",
			Language:             "java",
			BuildTool:            "maven",
			Packages:             "io.harness",
			RunOnlySelectedTests: true,
			ContainerPort:        8081,
			PreTestCommand:       "cd /Users/rutvijmehta/Desktop/harness/jhttp-rutvij\nls",

			//string args = 1;
			//string language = 2;   // language used for running tests. Java | Python | Go etc.
			//string buildTool = 3;  // build tool used for running tests. maven | bazel | gradle.
			//string testAnnotations = 4;
			//string packages = 5;
			//bool runOnlySelectedTests = 6;
			//StepContext context = 7;
			//uint32 container_port = 8;           // Port of the container on which run step needs to be executed.
			//repeated Report reports = 9;         // Spec for publishing junit reports
			//string preTestCommand = 10;          // Pre-commands to setup environment before running tests
			//string postTestCommand = 11;         // Post commands after running tests
			//repeated string envVarOutputs = 12;  // produced output variables
			//// TODO (Vistaar): Proxy this call from addon to LE.
			//string diff_files = 13;
			//map<string, string> environment = 14;
			//string buildEnvironment = 15;  // Dot net build environment Core | Framework
			//string frameworkVersion = 16;  // Dot net version 6.0 | 5.0
			//string namespaces = 17;        // Same funtion as java package for namespace languages
		},
	}
	step0 := &pb.UnitStep{
		Id:          "step4",
		DisplayName: "display_name",
		Step:        runTestsStep,
		LogKey:      "omgtest",
	}
	var steps []*pb.Step
	steps = append(steps, &pb.Step{Step: &pb.Step_Unit{Unit: step0}})
	execution := &pb.Execution{
		Steps: steps,
	}
	data, err := proto.Marshal(execution)
	if err != nil {
		fmt.Println("marshaling error: %v", err)
	}
	encoded := base64.StdEncoding.EncodeToString(data)
	return encoded
}

func main() {
	parseArgs()

	// Lite engine logs that are not part of any step are logged with ID engine:main
	remoteLogger := getRemoteLogger("engine-main")
	log := remoteLogger.BaseLogger
	logs.InitLogger(log)
	procWriter := remoteLogger.Writer
	defer procWriter.Close() // upload the logs to object store and close the stream

	if args.LogMetrics {
		metrics.Log(int32(os.Getpid()), "engine", log)
	}

	path := "/Users/rutvijmehta/step-exec/.harness/tmp/"
	args.Stage = &stageSchema{Input: generateRunTests(), TmpFilePath: path, Debug: true}

	if args.Stage != nil {
		// Starting stage execution
		startServer(remoteLogger, true)
		log.Infow("Starting stage execution")
		fmt.Println("Starting stage execution")
		err := executeStage(args.Stage.Input, args.Stage.TmpFilePath, args.Stage.ServicePorts, args.Stage.Debug, log)
		fmt.Println("rutvijdone")
		fmt.Println("rutvijerror: ", err)
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
