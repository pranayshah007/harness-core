// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package services

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"testing"

	"github.com/golang/mock/gomock"
	mexec "github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/logs"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestIntegrationSvcSuccess(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	var buf bytes.Buffer
	entrypoint := []string{"git"}
	args := []string{"status"}
	svcID := "git-clone"
	image := "alpine/git"

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	cmdFactory := mexec.NewMockCommandFactory(ctrl)
	pstate := mexec.NewMockProcessState(ctrl)
	svc := &integrationSvc{
		id:          svcID,
		image:       image,
		entrypoint:  entrypoint,
		args:        args,
		log:         log.Sugar(),
		addonLogger: log.Sugar(),
		procWriter:  &buf,
		cmdFactory:  cmdFactory,
	}

	cmd := mexec.NewMockCommand(ctrl)
	cmdFactory.EXPECT().Command(entrypoint[0], args[0]).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(nil).Return(cmd)
	cmd.EXPECT().Start().Return(nil)
	// cmd.EXPECT().Pid().Return(int(1))
	cmd.EXPECT().ProcessState().Return(pstate)
	pstate.EXPECT().MaxRss().Return(int64(100), nil)
	cmd.EXPECT().Wait().Return(nil)

	err := svc.Run()
	assert.Nil(t, err)
}

func TestIntegrationSvcNonZeroStatus(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	var buf bytes.Buffer
	entrypoint := []string{"git"}
	args := []string{"status"}
	svcID := "git-clone"
	image := "alpine/git"

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	cmdFactory := mexec.NewMockCommandFactory(ctrl)
	pstate := mexec.NewMockProcessState(ctrl)
	svc := &integrationSvc{
		id:          svcID,
		image:       image,
		entrypoint:  entrypoint,
		args:        args,
		log:         log.Sugar(),
		addonLogger: log.Sugar(),
		procWriter:  &buf,
		cmdFactory:  cmdFactory,
	}

	cmd := mexec.NewMockCommand(ctrl)
	cmdFactory.EXPECT().Command(entrypoint[0], args[0]).Return(cmd)
	cmd.EXPECT().WithStdout(&buf).Return(cmd)
	cmd.EXPECT().WithStderr(&buf).Return(cmd)
	cmd.EXPECT().WithEnvVarsMap(nil).Return(cmd)
	cmd.EXPECT().Start().Return(nil)
	cmd.EXPECT().ProcessState().Return(pstate)
	pstate.EXPECT().MaxRss().Return(int64(100), nil)
	cmd.EXPECT().Wait().Return(&exec.ExitError{})

	err := svc.Run()
	assert.NotNil(t, err)
	if _, ok := err.(*exec.ExitError); !ok {
		t.Fatalf("Expected err of type exec.ExitError")
	}
}

func TestServiceCreate(t *testing.T) {
	var buf bytes.Buffer
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	entrypoint := []string{"git"}
	args := []string{"status"}
	svcID := "git-clone"
	image := "alpine/git"

	executor := NewIntegrationSvc(svcID, image, entrypoint, args, log.Sugar(), &buf, false, log.Sugar())
	assert.NotNil(t, executor)
}

func TestServiceEntrypointErr(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	entrypoint := []string{}
	args := []string{}
	svcID := "git-clone"
	image := "alpine-git"

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return nil, nil, fmt.Errorf("entrypoint not found")
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	var buf bytes.Buffer
	executor := NewIntegrationSvc(svcID, image, entrypoint, args, log.Sugar(), &buf, false, log.Sugar())
	err := executor.Run()
	assert.NotNil(t, err)
}

func TestServiceEmptyEntrypointErr(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	entrypoint := []string{}
	args := []string{}
	svcID := "git-clone"
	image := "alpine-git"

	oldImgMetadata := getImgMetadata
	getImgMetadata = func(ctx context.Context, id, image, secret string, log *zap.SugaredLogger) ([]string, []string, error) {
		return nil, nil, nil
	}
	defer func() { getImgMetadata = oldImgMetadata }()

	var buf bytes.Buffer
	executor := NewIntegrationSvc(svcID, image, entrypoint, args, log.Sugar(), &buf, false, log.Sugar())
	err := executor.Run()
	assert.NotNil(t, err)
}
