// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"context"
	"fmt"
	"io"
	"path/filepath"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"go.uber.org/zap"
)

//go:generate mockgen -source run.go -package=tasks -destination mocks/run_mock.go RunTask

const (
	defaultTimeoutSecs int64         = 14400 // 4 hour
	defaultNumRetries  int32         = 1
	outputEnvSuffix    string        = ".out"
	cmdExitWaitTime    time.Duration = time.Duration(0)
	batchSize                        = 100
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
	}
}

// Executes customer provided run step command with retries and timeout handling
func (r *runTask) Run(ctx context.Context) (map[string]string, int32, error) {
	var err error
	var o map[string]string
	for i := int32(1); i <= r.numRetries; i++ {
		if o, err = r.execute(ctx, i); err == nil {
			st := time.Now()
			err = collectTestReports(ctx, r.reports, r.id, r.log)
			if err != nil {
				// If there's an error in collecting reports, we won't retry but
				// the step will be marked as an error
				r.log.Errorw("unable to collect test reports", zap.Error(err))
				return nil, r.numRetries, err
			}
			if len(r.reports) > 0 {
				r.log.Infow(fmt.Sprintf("collected test reports in %s time", time.Since(st)))
			}
			return o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect reports, ignore any errors during report collection itself
		errc := collectTestReports(ctx, r.reports, r.id, r.log)
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

	outputFile := filepath.Join(r.tmpFilePath, fmt.Sprintf("%s%s", r.id, outputEnvSuffix))
	cmdToExecute, err := r.getScript(ctx, outputFile)
	if err != nil {
		return nil, err
	}

	envVars, err := resolveExprInEnv(r.environment)
	if err != nil {
		return nil, err
	}

	shell, entrypointArg, err := r.getEntrypoint()
	if err != nil {
		return nil, err
	}
	cmdArgs := []string{entrypointArg, cmdToExecute}

	cmd := r.cmdContextFactory.CmdContextWithSleep(ctx, cmdExitWaitTime, shell, cmdArgs...).
		WithStdout(r.procWriter).WithStderr(r.procWriter).WithEnvVarsMap(envVars)
	err = runCmd(ctx, cmd, r.id, cmdArgs, retryCount, start, r.logMetrics, r.addonLogger)
	if err != nil {
		return nil, err
	}

	stepOutput := make(map[string]string)
	if len(r.envVarOutputs) != 0 {
		var err error
		outputVars, err := fetchOutputVariables(outputFile, r.fs, r.log)
		if err != nil {
			logCommandExecErr(r.log, "error encountered while fetching output of run step", r.id, cmdToExecute, retryCount, start, err)
			return nil, err
		}

		stepOutput = outputVars
	}

	r.addonLogger.Infow(
		"Successfully executed run step",
		"arguments", cmdToExecute,
		"output", stepOutput,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return stepOutput, nil
}

func (r *runTask) getScript(ctx context.Context, outputVarFile string) (string, error) {
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
	command := fmt.Sprintf("%s%s %s", earlyExitCmd, resolvedCmd, outputVarCmd)
	return command, nil
}

func (r *runTask) getEntrypoint() (string, string, error) {
	if r.shellType == pb.ShellType_BASH {
		return "bash", "-c", nil
	} else if r.shellType == pb.ShellType_SH {
		return "sh", "-c", nil
	} else if r.shellType == pb.ShellType_POWERSHELL {
		return "powershell", "-Command", nil
	} else if r.shellType == pb.ShellType_PWSH {
		return "pwsh", "-Command", nil
	}
	return "", "", fmt.Errorf("Unknown shell type: %s", r.shellType)
}

func (r *runTask) getEarlyExitCommand() (string, error) {
	if r.shellType == pb.ShellType_BASH || r.shellType == pb.ShellType_SH {
		return "set -xe\n", nil
	} else if r.shellType == pb.ShellType_POWERSHELL || r.shellType == pb.ShellType_PWSH {
		return "$ErrorActionPreference = 'Stop' \n", nil
	}
	return "", fmt.Errorf("Unknown shell type: %s", r.shellType)
}

func (r *runTask) getOutputVarCmd(outputVars []string, outputFile string) string {
	isPsh := r.isPowershell()

	cmd := ""
	if isPsh {
		cmd += fmt.Sprintf("\nNew-Item %s", outputFile)
	}
	for _, o := range outputVars {
		if isPsh {
			cmd += fmt.Sprintf("\n$val = \"%s $Env:%s\" \nAdd-Content -Path %s -Value $val", o, o, outputFile)
		} else {
			cmd += fmt.Sprintf("\necho \"%s $%s\" >> %s", o, o, outputFile)
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

func logCommandExecErr(log *zap.SugaredLogger, errMsg, stepID, args string, retryCount int32, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"arguments", args,
		"retry_count", retryCount,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
