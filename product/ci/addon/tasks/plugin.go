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
	"time"

	"github.com/harness/harness-core/commons/go/lib/filesystem"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/images"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/addon/artifact"
	"github.com/harness/harness-core/product/ci/addon/remote"
	"github.com/harness/harness-core/product/ci/addon/resolver"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"go.uber.org/zap"
)

//go:generate mockgen -source plugin.go -package=tasks -destination mocks/plugin_mock.go PluginTask

const (
	defaultPluginTimeout    int64         = 14400 // 4 hour
	defaultPluginNumRetries int32         = 1
	pluginCmdExitWaitTime   time.Duration = time.Duration(0)
	imageSecretEnv                        = "HARNESS_IMAGE_SECRET" // Docker image secret for plugin image
	settingEnvPrefix                      = "PLUGIN_"
	pluginArtifactFileEnv                 = "PLUGIN_ARTIFACT_FILE"
)

var (
	getImgMetadata = remote.GetImageEntrypoint
	evaluateJEXL   = remote.EvaluateJEXL
)

// PluginTask represents interface to execute a plugin step
type PluginTask interface {
	Run(ctx context.Context) (map[string]string, *pb.Artifact, int32, error)
}

type pluginTask struct {
	id                string
	displayName       string
	timeoutSecs       int64
	numRetries        int32
	envVarOutputs     []string
	tmpFilePath       string
	image             string
	entrypoint        []string
	environment       map[string]string
	prevStepOutputs   map[string]*pb.StepOutput
	logMetrics        bool
	log               *zap.SugaredLogger
	addonLogger       *zap.SugaredLogger
	procWriter        io.Writer
	cmdContextFactory exec.CmdContextFactory
	artifactFilePath  string
	reports           []*pb.Report
	fs                filesystem.FileSystem
}

// NewPluginTask creates a plugin step executor
func NewPluginTask(step *pb.UnitStep, prevStepOutputs map[string]*pb.StepOutput, tmpFilePath string,
	log *zap.SugaredLogger, w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) PluginTask {
	r := step.GetPlugin()
	timeoutSecs := r.GetContext().GetExecutionTimeoutSecs()
	fs := filesystem.NewOSFileSystem(log)
	if timeoutSecs == 0 {
		timeoutSecs = defaultPluginTimeout
	}

	numRetries := r.GetContext().GetNumRetries()
	if numRetries == 0 {
		numRetries = defaultPluginNumRetries
	}
	return &pluginTask{
		id:                step.GetId(),
		displayName:       step.GetDisplayName(),
		image:             r.GetImage(),
		entrypoint:        r.GetEntrypoint(),
		tmpFilePath:       tmpFilePath,
		envVarOutputs:     r.GetEnvVarOutputs(),
		environment:       r.GetEnvironment(),
		reports:           r.GetReports(),
		timeoutSecs:       timeoutSecs,
		numRetries:        numRetries,
		prevStepOutputs:   prevStepOutputs,
		cmdContextFactory: exec.OsCommandContextGracefulWithLog(log),
		logMetrics:        logMetrics,
		log:               log,
		fs:                fs,
		procWriter:        w,
		addonLogger:       addonLogger,
		artifactFilePath:  r.GetArtifactFilePath(),
	}
}

// Executes customer provided plugin with retries and timeout handling
func (t *pluginTask) Run(ctx context.Context) (map[string]string, *pb.Artifact, int32, error) {
	var err error
	var o *pb.Artifact
	var so map[string]string
	for i := int32(1); i <= t.numRetries; i++ {
		if so, o, err = t.execute(ctx, i); err == nil {
			st := time.Now()
			err = collectTestReports(ctx, t.reports, t.id, t.log, st)
			if err != nil {
				// If there's an error in collecting reports, we won't retry but
				// the step will be marked as an error
				t.log.Errorw("unable to collect test reports", zap.Error(err))
				return nil, nil, t.numRetries, err
			}
			return so, o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect reports, ignore any errors during report collection itself
		errc := collectTestReports(ctx, t.reports, t.id, t.log, time.Now())
		if errc != nil {
			t.log.Errorw("error while collecting test reports", zap.Error(errc))
		}
		return nil, nil, t.numRetries, err
	}
	return nil, nil, t.numRetries, err
}

// resolveExprInEnv resolves JEXL expressions & env var present in plugin settings environment variables
func (t *pluginTask) resolveExprInEnv(ctx context.Context) (map[string]string, error) {
	envVarMap := getEnvVars()
	for k, v := range t.environment {
		envVarMap[k] = v
	}

	// Resolves secret in environment variables e.g. foo-${ngSecretManager.obtain("secret", 1234)}
	resolvedSecretMap, err := resolver.ResolveSecretInMapValues(envVarMap)
	if err != nil {
		return nil, err
	}

	return resolvedSecretMap, nil
}

func (t *pluginTask) execute(ctx context.Context, retryCount int32) (map[string]string, *pb.Artifact, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(t.timeoutSecs))
	defer cancel()

	commands, err := t.getEntrypoint(ctx)
	if err != nil {
		logPluginErr(t.log, "failed to find entrypoint for plugin", t.id, commands, retryCount, start, err)
		return nil, nil, err
	}

	if len(commands) == 0 {
		err := fmt.Errorf("plugin entrypoint is empty")
		logPluginErr(t.log, "entrypoint fetched from remote for plugin is empty", t.id, commands, retryCount, start, err)
		return nil, nil, err
	}

	outputFile := filepath.Join(t.tmpFilePath, fmt.Sprintf("%s%s", t.id, outputEnvSuffix))

	envVarsMap, err := t.resolveExprInEnv(ctx)
	if err != nil {
		logPluginErr(t.log, "failed to evaluate JEXL expression for settings", t.id, commands, retryCount, start, err)
		return nil, nil, err
	}
	outputDotEnvFile := filepath.Join(t.tmpFilePath, fmt.Sprintf("%s%s", t.id, outputDotEnvSuffix))
	envVarsMap["DRONE_OUTPUT"] = outputDotEnvFile

	cmd := t.cmdContextFactory.CmdContextWithSleep(ctx, pluginCmdExitWaitTime, commands[0], commands[1:]...).
		WithStdout(t.procWriter).WithStderr(t.procWriter).WithEnvVarsMap(envVarsMap)
	cmdErr := runCmd(ctx, cmd, t.id, commands, retryCount, start, t.logMetrics, t.addonLogger)

	artifactFilePath := t.artifactFilePath
	if artifactFilePath == "" {
		artifactFilePath = envVarsMap[pluginArtifactFileEnv]
	}

	artifactProto, artifactErr := artifact.GetArtifactProtoFromFile(artifactFilePath)
	if artifactErr != nil {
		logPluginErr(t.addonLogger, "failed to retrieve artifacts from the plugin step", t.id, commands, retryCount, start, artifactErr)
	}

	stepOutput := make(map[string]string)
	_, err = t.fs.Stat(outputDotEnvFile)
	if err == nil {
		var err error
		outputVars, err := fetchOutputVariablesFromDotEnv(outputDotEnvFile, t.log)
		if err != nil {
			logCommandExecErr(t.log, "error encountered while fetching output of the plugin step from .env File", t.id, "", retryCount, start, err)
			// return nil, nil, err
		}

		stepOutput = outputVars
	} else {
		_, err = t.fs.Stat(outputFile)
		stepOutputExists := err == nil

		if len(t.envVarOutputs) != 0 && stepOutputExists {
			var err error
			outputVars, err := fetchOutputVariables(outputFile, t.fs, t.log)
			if err != nil {
				logCommandExecErr(t.log, "error encountered while fetching output of the plugin step", t.id, "", retryCount, start, err)
				return nil, nil, err
			}

			stepOutput = outputVars
		}
	}

	cmdExecutionStatus := "SUCCESS"
	if cmdErr != nil {
		cmdExecutionStatus = "FAILURE"
	}

	t.addonLogger.Infow(
		fmt.Sprintf("Plugin completed execution with status [%s]", cmdExecutionStatus),
		"arguments", commands,
		"artifact", artifactProto,
		"output", stepOutput,
		"elapsed_time_ms", utils.TimeSince(start),
	)

	return stepOutput, artifactProto, cmdErr
}

func (t *pluginTask) getEntrypoint(ctx context.Context) ([]string, error) {
	if len(t.entrypoint) != 0 {
		return t.entrypoint, nil
	}

	imageSecret, _ := os.LookupEnv(imageSecretEnv)
	return t.combinedEntrypoint(getImgMetadata(ctx, t.id, t.image, imageSecret, t.log))
}

func (t *pluginTask) combinedEntrypoint(ep, cmds []string, err error) ([]string, error) {
	if err != nil {
		return nil, err
	}
	return images.CombinedEntrypoint(ep, cmds), nil
}

func (t *pluginTask) readPluginOutput() (map[string]string, error) {
	return make(map[string]string), nil
}

func logPluginErr(log *zap.SugaredLogger, errMsg, stepID string, cmds []string, retryCount int32, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"retry_count", retryCount,
		"commands", cmds,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
