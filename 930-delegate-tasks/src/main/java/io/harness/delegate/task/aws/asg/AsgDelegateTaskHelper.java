/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.aws.asg.AsgCommandTaskNGHandler;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AsgDelegateTaskHelper {
  @Inject private AsgInfraConfigHelper asgInfraConfigHelper;

  public AsgCommandResponse getAsgCommandResponse(AsgCommandTaskNGHandler commandTaskHandler,
      AsgCommandRequest asgCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = asgCommandRequest.getCommandUnitsProgress() != null
        ? asgCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", asgCommandRequest.getCommandName());
    decryptRequestDTOs(asgCommandRequest);

    try {
      AsgCommandResponse asgCommandResponse =
          commandTaskHandler.executeTask(asgCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);
      asgCommandResponse.setCommandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return asgCommandResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing asg task [{}]", asgCommandRequest.getCommandName(), sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  private void decryptRequestDTOs(AsgCommandRequest asgCommandRequest) {
    asgInfraConfigHelper.decryptAsgInfraConfig(asgCommandRequest.getAsgInfraConfig());
  }
}
