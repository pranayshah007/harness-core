// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"bufio"
	"context"
	"errors"
	"os"
	"strings"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/metrics"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/addon/resolver"
	"github.com/joho/godotenv"
	"go.uber.org/zap"
)

var (
	mlog = metrics.Log
)

func runCmd(ctx context.Context, cmd exec.Command, stepID string, commands []string, retryCount int32, startTime time.Time,
	logMetrics bool, addonLogger *zap.SugaredLogger) error {
	err := cmd.Start()
	if err != nil {
		addonLogger.Errorw(
			"error encountered while executing the step",
			"step_id", stepID,
			"retry_count", retryCount,
			"commands", commands,
			"elapsed_time_ms", utils.TimeSince(startTime),
			zap.Error(err))
		return err
	}

	if logMetrics {
		pid := cmd.Pid()
		mlog(int32(pid), stepID, addonLogger)
	}

	err = cmd.Wait()
	if rss, e := cmd.ProcessState().MaxRss(); e == nil {
		addonLogger.Infow(
			"max RSS memory used by step",
			"step_id", stepID,
			"max_rss_memory_kb", rss)
	}

	if ctxErr := ctx.Err(); ctxErr == context.DeadlineExceeded {
		addonLogger.Errorw(
			"timeout while executing the step",
			"step_id", stepID,
			"commands", commands,
			"elapsed_time_ms", utils.TimeSince(startTime),
			zap.Error(ctxErr))
		return ctxErr
	}

	if err != nil {
		addonLogger.Errorw(
			"error encountered while executing the step",
			"step_id", stepID,
			"commands", commands,
			"elapsed_time_ms", utils.TimeSince(startTime),
			zap.Error(err))
		return err
	}
	return nil
}

// resolveExprInEnv resolves secrets present in environment variables
func resolveExprInEnv(env map[string]string) (
	map[string]string, error) {
	envVarMap := getEnvVars()
	for k, v := range env {
		envVarMap[k] = v
	}

	// Resolves secret in environment variables e.g. foo-${ngSecretManager.obtain("secret", 1234)}
	resolvedSecretMap, err := resolver.ResolveSecretInMapValues(envVarMap)
	if err != nil {
		return nil, err
	}

	return resolvedSecretMap, nil
}

// resolveExprInCmd resolves secret present in command
func resolveExprInCmd(cmd string) (string, error) {
	resolvedCmd, err := resolver.ResolveSecretInString(cmd)
	if err != nil {
		return "", err
	}

	return resolvedCmd, nil
}

// Returns environment variables as a map with key as environment variable name
// and value as environment variable value.
func getEnvVars() map[string]string {
	m := make(map[string]string)
	// os.Environ returns a copy of strings representing the environment in form
	// "key=value". Converting it into a map.
	for _, e := range os.Environ() {
		if i := strings.Index(e, "="); i >= 0 {
			m[e[:i]] = e[i+1:]
		}
	}
	return m
}

// Fetches map of env variable and value from OutputFile.
// OutputFile stores all env variable and value
func fetchOutputVariables(outputFile string, fs filesystem.FileSystem, log *zap.SugaredLogger) (
	map[string]string, error) {
	envVarMap := make(map[string]string)
	f, err := fs.Open(outputFile)
	if err != nil {
		log.Errorw("Failed to open output file", zap.Error(err))
		return nil, err
	}
	defer f.Close()

	s := bufio.NewScanner(f)
	for s.Scan() {
		line := s.Text()
		sa := strings.Split(line, " ")
		if len(sa) < 2 {
			log.Warnw(
				"output variable does not exist",
				"variable", sa[0],
			)
		} else {
			envVarMap[sa[0]] = line[len(sa[0])+1:]
		}
	}
	if err := s.Err(); err != nil {
		log.Errorw("Failed to create scanner from output file", zap.Error(err))
		return nil, err
	}
	return envVarMap, nil
}

// Fetches map of env variable and value from .env output File.
// OutputFile stores all env variable and value
func fetchOutputVariablesFromDotEnv(outputFile string, log *zap.SugaredLogger) (
	map[string]string, error) {
	envVarMap := make(map[string]string)
	if _, err := os.Stat(outputFile); errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	envVarMap, err := godotenv.Read(outputFile)
	if err != nil {
		content, ferr := os.ReadFile(outputFile)
		if ferr != nil {
			log.Errorw("Unable to read exported env file", zap.Error(ferr))
		}
		log.Errorw("failed to read exported env file", "content: ", content, zap.Error(err))
		return nil, err
	}
	return envVarMap, nil
}
