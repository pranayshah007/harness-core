// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/images"
	"github.com/harness/harness-core/commons/go/lib/utils"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"go.uber.org/zap"
)

//go:generate mockgen -source run.go -package=tasks -destination mocks/run_mock.go RunTask

const (
	defaultTimeoutSecs int64         = 14400 // 4 hour
	defaultNumRetries  int32         = 1
	outputEnvSuffix    string        = ".out"
	outputDotEnvSuffix string        = "-output.env"
	cmdExitWaitTime    time.Duration = time.Duration(0)
	batchSize                        = 100
	boldYellowColor    string        = "\u001b[33;1m"
)

// RunTask represents interface to execute a run step
type RunTask interface {
	Run(ctx context.Context) (map[string]string, int32, error)
}

type runTask struct {
	id                string
	displayName       string
	command           string
	shellType         pb.ShellType
	envVarOutputs     []string
	environment       map[string]string
	timeoutSecs       int64
	numRetries        int32
	tmpFilePath       string
	prevStepOutputs   map[string]*pb.StepOutput
	reports           []*pb.Report
	logMetrics        bool
	log               *zap.SugaredLogger
	addonLogger       *zap.SugaredLogger
	procWriter        io.Writer
	fs                filesystem.FileSystem
	cmdContextFactory exec.CmdContextFactory
	detach            bool
	image             string
	entrypoint        []string
}

// NewRunTask creates a run step executor
func NewRunTask(step *pb.UnitStep, prevStepOutputs map[string]*pb.StepOutput, tmpFilePath string,
	log *zap.SugaredLogger, w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) RunTask {
	r := step.GetRun()
	fs := filesystem.NewOSFileSystem(log)

	timeoutSecs := r.GetContext().GetExecutionTimeoutSecs()
	if timeoutSecs == 0 {
		timeoutSecs = defaultTimeoutSecs
	}

	numRetries := r.GetContext().GetNumRetries()
	if numRetries == 0 {
		numRetries = defaultNumRetries
	}
	return &runTask{
		id:                step.GetId(),
		displayName:       step.GetDisplayName(),
		command:           r.GetCommand(),
		shellType:         r.GetShellType(),
		tmpFilePath:       tmpFilePath,
		envVarOutputs:     r.GetEnvVarOutputs(),
		environment:       r.GetEnvironment(),
		reports:           r.GetReports(),
		timeoutSecs:       timeoutSecs,
		numRetries:        numRetries,
		cmdContextFactory: exec.OsCommandContextGracefulWithLog(log),
		prevStepOutputs:   prevStepOutputs,
		logMetrics:        logMetrics,
		log:               log,
		fs:                fs,
		procWriter:        w,
		addonLogger:       addonLogger,
		detach:            r.GetDetach(),
		image:             r.GetImage(),
		entrypoint:        r.GetEntrypoint(),
	}
}

// Executes customer provided run step command with retries and timeout handling
func (r *runTask) Run(ctx context.Context) (map[string]string, int32, error) {
	var err error
	var o map[string]string
	for i := int32(1); i <= r.numRetries; i++ {
		if o, err = r.execute(ctx, i); err == nil {
			st := time.Now()
			err = collectTestReports(ctx, r.reports, r.id, r.log, st)
			if err != nil {
				// If there's an error in collecting reports, we won't retry but
				// the step will be marked as an error
				r.log.Errorw("unable to collect test reports", zap.Error(err))
				return nil, r.numRetries, err
			}
			return o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect reports, ignore any errors during report collection itself
		errc := collectTestReports(ctx, r.reports, r.id, r.log, time.Now())
		if errc != nil {
			r.log.Errorw("error while collecting test reports", zap.Error(errc))
		}
		return nil, r.numRetries, err
	}
	return nil, r.numRetries, err
}

func (r *runTask) execute(ctx context.Context, retryCount int32) (map[string]string, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(r.timeoutSecs))
	defer cancel()

	outputFile := filepath.Join(r.tmpFilePath, fmt.Sprintf("%s%s", r.id, outputDotEnvSuffix))
	cmdToExecute, err := r.getScript(ctx, outputFile)
	if err != nil {
		return nil, err
	}

	envVars, err := resolveExprInEnv(r.environment)
	if err != nil {
		return nil, err
	}

	cmdArgs, err := r.getEntrypoint(ctx, cmdToExecute)
	if err != nil {
		return nil, err
	}

	if len(r.command) > 0 {
		r.log.Infof("%sExecuting the following command(s):\n%s", string(boldYellowColor), r.command)
	}

	if r.detach {
		go func() {
			ctx := context.Background()
			cmd := r.cmdContextFactory.CmdContextWithSleep(ctx, cmdExitWaitTime, cmdArgs[0], cmdArgs[1:]...).
				WithStdout(r.procWriter).WithStderr(r.procWriter).WithEnvVarsMap(envVars)
			_ = runCmd(ctx, cmd, r.id, cmdArgs, retryCount, start, r.logMetrics, r.addonLogger)
		}()
	} else {
		cmd := r.cmdContextFactory.CmdContextWithSleep(ctx, cmdExitWaitTime, cmdArgs[0], cmdArgs[1:]...).
			WithStdout(r.procWriter).WithStderr(r.procWriter).WithEnvVarsMap(envVars)
		err = runCmd(ctx, cmd, r.id, cmdArgs, retryCount, start, r.logMetrics, r.addonLogger)
	}

	if err != nil {
		return nil, err
	}

	stepOutput := make(map[string]string)
	if len(r.envVarOutputs) != 0 {
		var err error
		outputVars, err := fetchOutputVariablesFromDotEnv(outputFile, r.log)
		if err != nil {
			logCommandExecErr(r.log, "error encountered while fetching output of run step", r.id, cmdToExecute, retryCount, start, err)
			return nil, err
		}

		stepOutput = outputVars
	}

	r.addonLogger.Infow(
		"Successfully executed run step",
		"arguments", cmdArgs,
		"output", stepOutput,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return stepOutput, nil
}

func (r *runTask) getScript(ctx context.Context, outputVarFile string) (string, error) {
	if r.detach && len(r.command) == 0 {
		return "", nil
	}

	outputVarCmd := r.getOutputVarCmd(r.envVarOutputs, outputVarFile)
	resolvedCmd, err := resolveExprInCmd(r.command)
	if err != nil {
		return "", err
	}

	earlyExitCmd, err := r.getEarlyExitCommand()
	if err != nil {
		return "", err
	}

	// Using set -xe instead of printing command via utils.GetLoggableCmd(command) since if ' is present in a command,
	// echo on the command fails with an error.
	command := ""
	if r.detach {
		command = fmt.Sprintf("%s%s", earlyExitCmd, resolvedCmd)
	} else {
		command = fmt.Sprintf("%s%s %s", earlyExitCmd, resolvedCmd, outputVarCmd)
	}
	return command, nil
}

func (r *runTask) getEntrypoint(ctx context.Context, cmdToExecute string) ([]string, error) {
	// give priority to entrypoint
	if len(r.entrypoint) != 0 {
		return r.entrypoint, nil
	} else if len(cmdToExecute) != 0 {
		shell, ep, err := r.getShell(ctx)
		if err != nil {
			return nil, err
		}
		return []string{shell, ep, cmdToExecute}, nil
	}

	// fetch default ep
	imageSecret, _ := os.LookupEnv(imageSecretEnv)
	ep, args, err := getImgMetadata(ctx, r.id, r.image, imageSecret, r.log)
	if err != nil {
		return nil, err
	}
	return images.CombinedEntrypoint(ep, args), nil
}

func (r *runTask) getShell(ctx context.Context) (string, string, error) {
	if r.shellType == pb.ShellType_BASH {
		return "bash", "-c", nil
	} else if r.shellType == pb.ShellType_SH {
		return "sh", "-c", nil
	} else if r.shellType == pb.ShellType_POWERSHELL {
		return "powershell", "-Command", nil
	} else if r.shellType == pb.ShellType_PWSH {
		return "pwsh", "-Command", nil
	} else if r.shellType == pb.ShellType_PYTHON {
		if runtime.GOOS == "windows" {
			return "python", "-c", nil
		}
		return "python3", "-c", nil
	}
	return "", "", fmt.Errorf("Unknown shell type: %s", r.shellType)
}

func (r *runTask) getEarlyExitCommand() (string, error) {
	if r.shellType == pb.ShellType_BASH || r.shellType == pb.ShellType_SH {
		return "set -xe\n", nil
	} else if r.shellType == pb.ShellType_POWERSHELL || r.shellType == pb.ShellType_PWSH {
		return "$ErrorActionPreference = 'Stop' \n", nil
	} else if r.shellType == pb.ShellType_PYTHON {
		return "", nil
	}
	return "", fmt.Errorf("Unknown shell type: %s", r.shellType)
}

func (r *runTask) getOutputVarCmd(outputVars []string, outputFile string) string {
	isPsh := r.isPowershell()
	isPython := r.isPython()

	cmd := ""
	if isPsh {
		cmd += fmt.Sprintf("\nNew-Item %s", outputFile)
	} else if isPython {
		cmd += "\nimport os\n"
	}

	for _, o := range outputVars {
		if isPsh {
			cmd += fmt.Sprintf("\n$val = \"%s=$Env:%s\" \nAdd-Content -Path %s -Value $val", o, o, outputFile)
		} else if isPython {
			cmd += fmt.Sprintf("with open('%s', 'a') as out_file:\n\tout_file.write('%s=' + os.getenv('%s') + '\\n')\n", outputFile, o, o)
		} else {
			cmd += fmt.Sprintf("\necho \"%s=$%s\" >> %s", o, o, outputFile)
		}
	}

	return cmd
}

func (r *runTask) isPowershell() bool {
	if r.shellType == pb.ShellType_POWERSHELL || r.shellType == pb.ShellType_PWSH {
		return true
	}
	return false
}

func (r *runTask) isPython() bool {
	if r.shellType == pb.ShellType_PYTHON {
		return true
	}
	return false
}

func logCommandExecErr(log *zap.SugaredLogger, errMsg, stepID, args string, retryCount int32, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"arguments", args,
		"retry_count", retryCount,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
