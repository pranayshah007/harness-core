/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress.CommandUnitProgressBuilder;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogLine;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NGDelegateLogCallback implements LogCallback {
  private ILogStreamingTaskClient iLogStreamingTaskClient;
  private String commandUnitName;
  private CommandUnitsProgress commandUnitsProgress;

  public NGDelegateLogCallback(ILogStreamingTaskClient iLogStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    this.iLogStreamingTaskClient = iLogStreamingTaskClient;
    this.commandUnitName = commandUnitName;
    this.commandUnitsProgress = commandUnitsProgress;

    if (this.iLogStreamingTaskClient != null && shouldOpenStream) {
      iLogStreamingTaskClient.openStream(commandUnitName);
    }
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
    if (this.iLogStreamingTaskClient == null) {
      return;
    }
    Instant now = Instant.now();
    LogLine logLine = LogLine.builder().message(line).level(logLevel).timestamp(now).build();
    iLogStreamingTaskClient.writeLogLine(logLine, commandUnitName);

    boolean terminalStatus = CommandExecutionStatus.isTerminalStatus(commandExecutionStatus);
    if (terminalStatus) {
      iLogStreamingTaskClient.closeStream(commandUnitName);
    }

    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap =
        commandUnitsProgress.getCommandUnitProgressMap();

    boolean change = updateCommandUnitProgressMap(commandExecutionStatus, now, commandUnitProgressMap);

    if (change) {
      ITaskProgressClient taskProgressClient = iLogStreamingTaskClient.obtainTaskProgressClient();

      ExecutorService taskProgressExecutor = iLogStreamingTaskClient.obtainTaskProgressExecutor();
      taskProgressExecutor.submit(() -> sendTaskProgressUpdate(taskProgressClient));
    }
  }

  boolean updateCommandUnitProgressMap(CommandExecutionStatus commandExecutionStatus, Instant now,
      LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap) {
    CommandUnitProgressBuilder commandUnitProgressBuilder =
        CommandUnitProgress.builder().status(commandExecutionStatus);
    boolean change = false;

    if (!commandUnitProgressMap.containsKey(commandUnitName)) {
      commandUnitProgressBuilder.startTime(now.toEpochMilli());
      change = true;
    } else {
      CommandUnitProgress commandUnitProgress = commandUnitProgressMap.get(commandUnitName);
      commandUnitProgressBuilder.startTime(commandUnitProgress.getStartTime());
      if (commandUnitProgress.getStatus() != commandExecutionStatus) {
        change = true;
      }
    }
    if (CommandExecutionStatus.isTerminalStatus(commandExecutionStatus)) {
      commandUnitProgressBuilder.endTime(now.toEpochMilli());
      change = true;
    }
    commandUnitProgressMap.put(commandUnitName, commandUnitProgressBuilder.build());
    return change;
  }

  void sendTaskProgressUpdate(ITaskProgressClient taskProgressClient) {
    if (taskProgressClient != null) {
      try {
        taskProgressClient.sendTaskProgressUpdate(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      } catch (Exception exception) {
        log.error("Failed to send task progress update {}", commandUnitsProgress, exception);
      }
    }
  }

  @Override
  public void dispatchLogs() {
    if (this.iLogStreamingTaskClient != null) {
      this.iLogStreamingTaskClient.dispatchLogs();
    }
  }
}
