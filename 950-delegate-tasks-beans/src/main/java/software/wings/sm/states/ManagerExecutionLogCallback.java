/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.Log;
import software.wings.beans.Log.Builder;
import software.wings.service.intfc.LogService;

import com.google.common.base.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@TargetModule(_957_CG_BEANS)
public class ManagerExecutionLogCallback implements LogCallback {
  private transient LogService logService;
  private Builder logBuilder;
  private String activityId;

  public ManagerExecutionLogCallback() {}

  public ManagerExecutionLogCallback(LogService logService, Builder logBuilder, String activityId) {
    this.logService = logService;
    this.logBuilder = logBuilder;
    this.activityId = activityId;
  }

  @Override
  public void saveExecutionLog(String line) {
    saveExecutionLog(line, CommandExecutionStatus.RUNNING, LogLevel.INFO);
  }

  public void saveExecutionLog(String line, CommandExecutionStatus commandExecutionStatus) {
    saveExecutionLog(line, commandExecutionStatus, LogLevel.INFO);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel) {
    saveExecutionLog(line, CommandExecutionStatus.RUNNING, logLevel);
  }

  private void saveExecutionLog(String line, CommandExecutionStatus status, LogLevel logLevel) {
    saveExecutionLog(line, logLevel, status);
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    if (logService != null) {
      Log logObject = logBuilder.but().logLevel(logLevel).executionResult(commandExecutionStatus).logLine(line).build();
      logService.batchedSaveCommandUnitLogs(activityId, logObject.getCommandUnitName(), logObject);
    } else {
      log.warn("No logService injected. Couldn't save log [{}]", line);
    }
  }

  public LogService getLogService() {
    return logService;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ManagerExecutionLogCallback that = (ManagerExecutionLogCallback) o;
    return Objects.equal(activityId, that.activityId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(activityId);
  }
}
