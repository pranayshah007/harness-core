/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.pcf.CfCommandTaskNGHandler;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CfDelegateTaskHelper {
  @Inject private Map<String, CfCommandTaskNGHandler> CfTaskTypeToTaskHandlerMap;
  public CfCommandResponseNG getCfCommandResponse(
      CfCommandRequestNG cfCommandRequestNG, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = cfCommandRequestNG.getCommandUnitsProgress() != null
        ? cfCommandRequestNG.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", cfCommandRequestNG.getCfCommandTypeNG().name());

    CfCommandTaskNGHandler commandTaskHandler =
        CfTaskTypeToTaskHandlerMap.get(cfCommandRequestNG.getCfCommandTypeNG().name());
    try {
      CfCommandResponseNG cfCommandResponseNG =
          commandTaskHandler.executeTask(cfCommandRequestNG, iLogStreamingTaskClient, commandUnitsProgress);
      cfCommandResponseNG.setCommandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return cfCommandResponseNG;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing ecs task [{}]",
          cfCommandRequestNG.getCommandName() + ":" + cfCommandRequestNG.getCfCommandTypeNG(), sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }
}
