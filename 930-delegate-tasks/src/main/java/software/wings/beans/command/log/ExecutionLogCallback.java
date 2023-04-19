/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans.command;

import static software.wings.beans.dto.Log.Builder.aLog;

import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.delegatetasks.DelegateLogService;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by anubhaw on 2/14/17.
 */
@Slf4j
@Deprecated
public class ExecutionLogCallback implements LogCallback {
  private transient DelegateLogService logService;
  private String accountId;
  private String appId;
  private String activityId;
  private String commandName;

  /**
   * @deprecated Use the {@link ILogStreamingTaskClient#obtainLogCallback(String commandName)} instead.
   */
  @Deprecated
  public ExecutionLogCallback() {
    // do nothing callback
  }

  /**
   * @deprecated Use the {@link ILogStreamingTaskClient#obtainLogCallback(String commandName)} instead.
   */
  @Deprecated
  public ExecutionLogCallback(
      DelegateLogService logService, String accountId, String appId, String activityId, String commandName) {
    this.logService = logService;
    this.accountId = accountId;
    this.appId = appId;
    this.activityId = activityId;
    this.commandName = commandName;
  }

  @Override
  public void saveExecutionLog(String line) {
    saveExecutionLog(line, LogLevel.INFO);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel) {
    saveExecutionLog(line, logLevel, CommandExecutionStatus.RUNNING);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    if (logService != null) {
      logService.save(accountId,
          aLog()
              .appId(appId)
              .activityId(activityId)
              .logLevel(logLevel)
              .commandUnitName(commandName)
              .logLine(line)
              .executionResult(commandExecutionStatus)
              .build());
    } else {
      log.error("No logService injected. Couldn't save log [{}:{}]", logLevel, line);
    }
  }

  public void setLogService(DelegateLogService logService) {
    this.logService = logService;
  }
}
