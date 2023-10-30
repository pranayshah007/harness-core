// Copyright 2023 Harness Inc. All rights reserved.
 // Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 // that can be found in the licenses directory at the root of this repository, also available at
 // https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 package tasks

 import (
     "context"
          "io/ioutil"
          "os"
     "io"
"fmt"
"time"
     pb "github.com/harness/harness-core/product/ci/engine/proto"
     "github.com/harness/harness-core/commons/go/lib/exec"
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
     cmdContextFactory exec.CmdContextFactory
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
         cmdContextFactory: exec.OsCommandContextGracefulWithLog(log),
         addonLogger:  addonLogger,
         procWriter:   w,
     }
 }

 // Run method
 func (e *executeTask) Run(ctx context.Context) (bool, error) {
    start := time.Now()
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
     fmt.Println("Command that we want to execute is")
     fmt.Println(e.command[0])

     //cmdTo := "echo checkLogLines"
     cmdArgs := []string{"bash", "-c", e.command[0]}

     cmd := e.cmdContextFactory.CmdContextWithSleep(ctx, time.Duration(0), cmdArgs[0], cmdArgs[1:]...).
			WithStdout(e.procWriter).WithStderr(e.procWriter)
		err = runCmd(ctx, cmd, "someStepID", cmdArgs, 1, start, e.logMetrics, e.addonLogger)

        if err != nil {
            return false, err
        }


     // We need to make sure, we dont send command here as "echo hello world". it should be echo, hello world.for
     // https://pkg.go.dev/os/exec#Command

     return true, nil
 }