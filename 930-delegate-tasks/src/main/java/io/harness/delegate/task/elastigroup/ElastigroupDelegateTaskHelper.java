/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.elastigroup.ElastigroupCommandTaskHandler;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupDelegateTaskHelper {
  public ElastigroupCommandResponse getElastigroupCommandResponse(ElastigroupCommandTaskHandler commandTaskHandler,
      ElastigroupCommandRequest elastigroupCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = elastigroupCommandRequest.getCommandUnitsProgress() != null
        ? elastigroupCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", elastigroupCommandRequest.getCommandName());

    try {
      ElastigroupCommandResponse elastigroupCommandResponse =
          commandTaskHandler.executeTask(elastigroupCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
      elastigroupCommandResponse.setCommandUnitsProgress(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return elastigroupCommandResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing elastigroup task [{}]", elastigroupCommandRequest.getCommandName(),
          sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }
}
