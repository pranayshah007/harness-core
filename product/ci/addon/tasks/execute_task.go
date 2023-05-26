// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
    "context"
    "io"
    "io/ioutil"
    "os/exec"

    pb "github.com/harness/harness-core/product/ci/engine/proto"
    "go.uber.org/zap"
)

// ExecuteTask represents interface to execute a CD task
type ExecuteTask interface {
    Run(ctx context.Context) bool
}

type executeTask struct {
    taskParams      []byte
    command         []string
    logMetrics      bool
    log             *zap.SugaredLogger
    addonLogger     *zap.SugaredLogger
    procWriter      io.Writer
}

// NewExecuteStep creates a execute step executor
func NewExecuteStep(step *pb.UnitStep, log *zap.SugaredLogger,
    w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) ExecuteTask {
    e := step.GetExecuteTask()

    return &executeTask{
        taskParams:   e.GetTaskParameters(),
        command:      e.GetExecuteCommand(),
        logMetrics:   logMetrics,
        log:          log,
        addonLogger:  addonLogger,
        procWriter:   w,
    }
}

// Run method
func (e *executeTask) Run(ctx context.Context) bool {
    // 1. Write the task parameters to the path where it is expected.
    err := ioutil.WriteFile("/etc/config/taskfile", e.taskParams, 0644)
    if err != nil {
        e.log.Errorw("unable to write task parameters to file")
        return false
    }

    // 2. Run the task script
    cmd := exec.Command("sh", "/opt/harness/start.sh")
    _, err = cmd.CombinedOutput()
    if err != nil {
        e.log.Errorw("unable to run the task script")
        return false
    }

    return true
}