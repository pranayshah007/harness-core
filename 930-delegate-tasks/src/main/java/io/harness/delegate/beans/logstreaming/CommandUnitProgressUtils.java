/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;

import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.logging.CommandExecutionStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class CommandUnitProgressUtils {
  public boolean updateCommandUnitProgressMap(CommandExecutionStatus commandExecutionStatus, Instant now,
      LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap, String commandUnitName) {
    CommandUnitProgress.CommandUnitProgressBuilder commandUnitProgressBuilder =
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

  public void sendTaskProgressUpdate(
      ITaskProgressClient taskProgressClient, String commandUnitName, CommandUnitsProgress commandUnitsProgress) {
    if (taskProgressClient != null) {
      try {
        log.info("Send task progress for unit: {}", commandUnitName);
        if (commandUnitsProgress == null) {
          // Not sure how valid is this logic, keeping it for backward compatibility
          taskProgressClient.sendTaskProgressUpdate(UnitProgressDataMapper.toUnitProgressData(null));
        } else {
          taskProgressClient.sendTaskProgressUpdate(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
        }
        log.info("Task progress sent for unit: {}", commandUnitsProgress);
      } catch (Exception exception) {
        log.error("Failed to send task progress update {}", commandUnitsProgress, exception);
      }
    }
  }
  public void sendTaskProgressUpdateAsync(ILogStreamingTaskClient iLogStreamingTaskClient, String commandUnitName,
      CommandUnitsProgress commandUnitsProgress) {
    ITaskProgressClient taskProgressClient = iLogStreamingTaskClient.obtainTaskProgressClient();

    ExecutorService taskProgressExecutor = iLogStreamingTaskClient.obtainTaskProgressExecutor();
    taskProgressExecutor.submit(()
                                    -> CommandUnitProgressUtils.sendTaskProgressUpdate(
                                        taskProgressClient, commandUnitName, commandUnitsProgress));
  }
}
