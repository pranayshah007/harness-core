// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"testing"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestPluginSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	var buf bytes.Buffer
	numRetries := int32(1)
	commands := []string{"git"}
	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	cmd := mexec.NewMockCommand(ctrl)
	pstate := mexec.NewMockProcessState(ctrl)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	fs := filesystem.NewMockFileSystem(ctrl)

	e := pluginTask{
		id:                "step1",
		image:             "plugin/drone-git",
		timeoutSecs:       5,
		numRetries:        numRetries,
		log:               log.Sugar(),
		addonLogger:       log.Sugar(),
		cmdContextFactory: cmdFactory,
		procWriter:        &buf,
		fs:                fs,
	}

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return commands, nil, nil
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)
	cmd.EXPECT().Start().Return(nil)
	cmd.EXPECT().ProcessState().Return(pstate)
	pstate.EXPECT().MaxRss().Return(int64(100), nil)
	cmd.EXPECT().Wait().Return(nil)
	fs.EXPECT().Stat("step1-output.env").Return(nil, fmt.Errorf("file not found"))
	fs.EXPECT().Stat("step1.out").Return(nil, nil)

	_, _, retries, err := e.Run(ctx)
	assert.Nil(t, err)
	assert.Equal(t, retries, numRetries)
}

func TestPluginNonZeroStatus(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	numRetries := int32(1)
	var buf bytes.Buffer
	commands := []string{"git"}
	cmdFactory := mexec.NewMockCmdContextFactory(ctrl)
	fs := filesystem.NewMockFileSystem(ctrl)
	cmd := mexec.NewMockCommand(ctrl)
	pstate := mexec.NewMockProcessState(ctrl)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	e := pluginTask{
		id:                "step1",
		image:             "plugin/drone-git",
		timeoutSecs:       5,
		numRetries:        numRetries,
		log:               log.Sugar(),
		addonLogger:       log.Sugar(),
		cmdContextFactory: cmdFactory,
		procWriter:        &buf,
		fs:                fs,
	}

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return commands, nil, nil
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	cmdFactory.EXPECT().CmdContextWithSleep(gomock.Any(), cmdExitWaitTime, gomock.Any()).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(gomock.Any()).Return(cmd)
	cmd.EXPECT().Start().Return(nil)
	cmd.EXPECT().ProcessState().Return(pstate)
	pstate.EXPECT().MaxRss().Return(int64(100), nil)
	cmd.EXPECT().Wait().Return(&exec.ExitError{})
	fs.EXPECT().Stat("step1-output.env").Return(nil, fmt.Errorf("file not found"))
	fs.EXPECT().Stat("step1.out").Return(nil, nil)

	_, _, retries, err := e.Run(ctx)
	assert.NotNil(t, err)
	if _, ok := err.(*exec.ExitError); !ok {
		t.Fatalf("Expected err of type exec.ExitError")
	}
	assert.Equal(t, retries, numRetries)
}

func TestPluginTaskCreate(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	tmpPath := "/tmp/"
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Plugin{
			Plugin: &pb.PluginStep{
				Image: "plugin/drone-git",
			},
		},
	}

	var buf bytes.Buffer
	executor := NewPluginTask(step, nil, tmpPath, log.Sugar(), &buf, false, log.Sugar())
	assert.NotNil(t, executor)
}

func TestPluginEntrypointErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()
	tmpPath := "/tmp/"

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Plugin{
			Plugin: &pb.PluginStep{
				Image: "plugin/drone-git",
			},
		},
	}

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return nil, nil, fmt.Errorf("entrypoint not found")
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	var buf bytes.Buffer
	executor := NewPluginTask(step, nil, tmpPath, log.Sugar(), &buf, false, log.Sugar())
	_, _, _, err := executor.Run(ctx)
	assert.NotNil(t, err)
}

func TestPluginEmptyEntrypointErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()
	tmpPath := "/tmp/"

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Plugin{
			Plugin: &pb.PluginStep{
				Image: "plugin/drone-git",
			},
		},
	}

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return nil, nil, nil
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	var buf bytes.Buffer
	executor := NewPluginTask(step, nil, tmpPath, log.Sugar(), &buf, false, log.Sugar())
	_, _, _, err := executor.Run(ctx)
	assert.NotNil(t, err)
}
