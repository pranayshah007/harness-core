/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.shell.ExecuteCommandResponse;

import java.util.Map;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@OwnedBy(HarnessTeam.CDP)
public interface CommandHandler {
  String RESOLVED_ENV_VARIABLES_KEY = "RESOLVED_ENV_VARIABLES";
  default String getExecutionStagingDir(CommandTaskParameters taskParameters) {
    return String.format("/tmp/%s", taskParameters.getExecutionId());
  }

  ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext);

  default void closeLogStream(LogCallback logCallback, CommandExecutionStatus commandExecutionStatus, String msg) {
    LogLevel logLevel = LogLevel.ERROR;
    CommandExecutionStatus status = CommandExecutionStatus.FAILURE;
    if (CommandExecutionStatus.SUCCESS.equals(commandExecutionStatus)) {
      logLevel = LogLevel.INFO;
      status = CommandExecutionStatus.SUCCESS;
    }

    logCallback.saveExecutionLog(msg, logLevel, status);
  }

  default void closeLogStream(LogCallback logCallback, CommandExecutionStatus commandExecutionStatus) {
    String msg = "Command finished with status " + commandExecutionStatus;
    closeLogStream(logCallback, commandExecutionStatus, msg);
  }

  default void closeLogStreamWithSuccess(LogCallback logCallback) {
    closeLogStream(logCallback, CommandExecutionStatus.SUCCESS);
  }

  default void closeLogStreamWithError(LogCallback logCallback) {
    closeLogStream(logCallback, CommandExecutionStatus.FAILURE);
  }

  default void closeLogStreamEmptyMsg(LogCallback logCallback, CommandExecutionStatus commandExecutionStatus) {
    closeLogStream(logCallback, commandExecutionStatus, "");
  }

  default void closeLogStreamWithSuccessEmptyMsg(LogCallback logCallback) {
    closeLogStreamEmptyMsg(logCallback, CommandExecutionStatus.SUCCESS);
  }
}
