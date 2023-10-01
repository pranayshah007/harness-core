// Copyright 2023 Harness Inc. All rights reserved.
 // Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 // that can be found in the licenses directory at the root of this repository, also available at
 // https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 package tasks

 import (
     "context"
     "io"
     "io/ioutil"
     "os"
     "os/exec"

     pb "github.com/harness/harness-core/product/ci/engine/proto"
     "go.uber.org/zap"
 )

const (
    taskFileEnv = "TASK_PARAMETERS_FILE"
)

 // ExecuteTask represents interface to execute a CD task
 type ExecuteTask interface {
     Run(ctx context.Context) (bool, error)
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
 func (e *executeTask) Run(ctx context.Context) (bool, error) {
     // 1. Write the task parameters to the path where it is expected.
     e.addonLogger.Infow(" Run method for execute task")
     taskfile, ok := os.LookupEnv(taskFileEnv)
     if !ok {
        return false, nil
     }

     err := ioutil.WriteFile(taskfile, e.taskParams, 0644)
     if err != nil {
         e.log.Errorw("unable to write task parameters to file")
         return false, err
     }

     // 2. Run the task script
     e.addonLogger.Infow("Task params written, executing task command now", e.command[0])
     cmd := exec.Command(e.command[0])
     _, err = cmd.CombinedOutput()
     if err != nil {
         e.log.Errorw("unable to run the task script", zap.Error(err))
         return false, err
     }

     return true, nil
 }