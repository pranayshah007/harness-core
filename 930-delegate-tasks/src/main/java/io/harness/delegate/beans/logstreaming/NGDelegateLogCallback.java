/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NGDelegateLogCallback implements LogCallback {
  private final ILogStreamingTaskClient iLogStreamingTaskClient;
  private final String commandUnitName;
  private final CommandUnitsProgress commandUnitsProgress;

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
  public void saveExecutionLog(String line, LogLevel logLevel, boolean skipColoringLog) {
    saveExecutionLogInternal(line, logLevel, CommandExecutionStatus.RUNNING, false, skipColoringLog);
  }

  @Override
  public void saveExecutionLog(
      String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus, boolean closeLogStream) {
    saveExecutionLogInternal(line, logLevel, commandExecutionStatus, closeLogStream, false);
  }

  private void saveExecutionLogInternal(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus,
      boolean closeLogStream, boolean skipColoringLog) {
    if (this.iLogStreamingTaskClient == null) {
      return;
    }
    Instant now = Instant.now();
    LogLine logLine =
        LogLine.builder().message(line).level(logLevel).timestamp(now).skipColoring(skipColoringLog).build();
    iLogStreamingTaskClient.writeLogLine(logLine, commandUnitName);

    if (closeLogStream) {
      iLogStreamingTaskClient.closeStream(commandUnitName);
    }

    if (commandUnitsProgress == null) {
      // When no units
      return;
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

  // This method is to close log stream without any message.
  @Override
  public void close(CommandExecutionStatus commandExecutionStatus) {
    Instant now = Instant.now();
    iLogStreamingTaskClient.closeStream(commandUnitName);
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap =
        commandUnitsProgress.getCommandUnitProgressMap();

    boolean change = updateCommandUnitProgressMap(commandExecutionStatus, now, commandUnitProgressMap);

    if (change) {
      ITaskProgressClient taskProgressClient = iLogStreamingTaskClient.obtainTaskProgressClient();

      ExecutorService taskProgressExecutor = iLogStreamingTaskClient.obtainTaskProgressExecutor();
      taskProgressExecutor.submit(() -> sendTaskProgressUpdate(taskProgressClient));
    }
  }

  @Override
  public void saveExecutionLog(String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    boolean terminalStatus = CommandExecutionStatus.isTerminalStatus(commandExecutionStatus);
    saveExecutionLog(line, logLevel, commandExecutionStatus, terminalStatus);
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
      if (CommandExecutionStatus.isTerminalStatus(commandUnitProgress.getStatus())) {
        log.warn("Skipped updating command unit status as the unit: {} has already reached terminal status: {}",
            commandUnitName, commandUnitProgress.getStatus());
        return false;
      }
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
        log.info("Send task progress for unit: {}", commandUnitName);
        if (commandUnitsProgress == null) {
          // Not sure how valid is this logic, keeping it for backward compatibility
          taskProgressClient.sendTaskProgressUpdate(UnitProgressDataMapper.toUnitProgressData(null));
        } else {
          // We want to ensure that only one thread is owning commandUnitsProgress and will not send task progress for
          // same commandUnitsProgress instance in parallel otherwise it could lead to a race condition:
          // 1. t1 send progress with { u1, u2 } and t2 send progress with { u1, u2, u3 } in a small range of time
          // 2. t2 progress is acknowledged before t1
          // 3. t1 overrides t2 because it was acknowledged later
          // This sync expects that executor service will use a scheduler based on FIFO priority and update of
          // commandUnitsProgress is happening in the same thread
          synchronized (commandUnitsProgress) {
            taskProgressClient.sendTaskProgressUpdate(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
          }
        }
        log.info("Task progress sent for unit: {}", commandUnitsProgress);
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
