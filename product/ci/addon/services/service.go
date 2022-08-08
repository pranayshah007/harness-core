// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package services

import (
	"context"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/images"
	"github.com/harness/harness-core/commons/go/lib/metrics"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/addon/remote"
	"go.uber.org/zap"
)

//go:generate mockgen -source service.go -package=services -destination mocks/service_mock.go IntegrationSvc

const (
	imageSecretEnv = "HARNESS_IMAGE_SECRET" // Docker image secret for integration service
)

var (
	getImgMetadata = remote.GetImageEntrypoint
)

// IntegrationSvc represents interface to execute an integration service
type IntegrationSvc interface {
	Run() error
}

type integrationSvc struct {
	id          string
	image       string
	entrypoint  []string
	args        []string
	logMetrics  bool
	log         *zap.SugaredLogger
	addonLogger *zap.SugaredLogger
	procWriter  io.Writer
	cmdFactory  exec.CommandFactory
}

// NewIntegrationSvc creates a integration service executor
func NewIntegrationSvc(svcID, image string, entrypoint, args []string, log *zap.SugaredLogger,
	w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) IntegrationSvc {
	return &integrationSvc{
		id:          svcID,
		image:       image,
		entrypoint:  entrypoint,
		args:        args,
		cmdFactory:  exec.OsCommand(),
		logMetrics:  logMetrics,
		log:         log,
		addonLogger: addonLogger,
		procWriter:  w,
	}
}

// Runs integration test service
func (s *integrationSvc) Run() error {
	start := time.Now()
	ctx := context.Background()
	commands, err := s.getEntrypoint(ctx)
	if err != nil {
		logErr(s.log, "failed to find entrypoint for service", s.id, commands, start, err)
		return err
	}

	if len(commands) == 0 {
		err := fmt.Errorf("service entrypoint is empty")
		logErr(s.log, "entrypoint fetched from remote for service is empty", s.id, commands, start, err)
		return err
	}

	cmd := s.cmdFactory.Command(commands[0], commands[1:]...).
		WithStdout(s.procWriter).WithStderr(s.procWriter).WithEnvVarsMap(nil)
	err = runCmd(cmd, s.id, commands, start, s.logMetrics, s.log, s.addonLogger)
	if err != nil {
		return err
	}

	s.log.Warnw(
		"Service execution stopped",
		"service_id", s.id,
		"arguments", commands,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return nil
}

func (s *integrationSvc) getEntrypoint(ctx context.Context) ([]string, error) {
	if len(s.entrypoint) != 0 {
		return images.CombinedEntrypoint(s.entrypoint, s.args), nil
	}

	imageSecret, _ := os.LookupEnv(imageSecretEnv)
	return s.combinedEntrypoint(getImgMetadata(ctx, s.id, s.image, imageSecret, s.log))
}

// combines the entrypoint & commands and returns the combined entrypoint for a docker image.
// It gives priority to user specified entrypoint & args over image entrypoint & commands.
func (s *integrationSvc) combinedEntrypoint(imgEndpoint, imgCmds []string, err error) ([]string, error) {
	if err != nil {
		return nil, err
	}

	ep := imgEndpoint
	if len(s.entrypoint) != 0 {
		ep = s.entrypoint
	}

	cmds := imgCmds
	if len(s.args) != 0 {
		cmds = s.args
	}
	return images.CombinedEntrypoint(ep, cmds), nil
}

func logErr(log *zap.SugaredLogger, errMsg, svcID string, cmds []string, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"commands", cmds,
		"id", svcID,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}

func runCmd(cmd exec.Command, svcID string, commands []string, startTime time.Time,
	logMetrics bool, log *zap.SugaredLogger, metricLog *zap.SugaredLogger) error {
	err := cmd.Start()
	if err != nil {
		log.Errorw(
			fmt.Sprintf("failed to start service with err: %s", err),
			"commands", commands,
			"service_id", svcID,
			"elapsed_time_ms", utils.TimeSince(startTime),
			zap.Error(err))
		return err
	}

	if logMetrics {
		pid := cmd.Pid()
		metrics.Log(int32(pid), svcID, metricLog)
	}

	err = cmd.Wait()
	if rss, e := cmd.ProcessState().MaxRss(); e == nil {
		metricLog.Infow(
			"max RSS memory used by service",
			"service_id", svcID,
			"max_rss_memory_kb", rss)
	}

	if err != nil {
		log.Errorw(
			fmt.Sprintf("service execution failed with error: %s", err),
			"commands", commands,
			"service_id", svcID,
			"elapsed_time_ms", utils.TimeSince(startTime),
			zap.Error(err))
		return err
	}
	return nil
}
