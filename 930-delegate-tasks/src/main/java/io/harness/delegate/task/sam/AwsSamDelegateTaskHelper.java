/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.sam;

import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.aws.sam.AwsSamCommandTaskHandler;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.sam.AwsSamDelegateTaskParams;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamCommandResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamDelegateTaskHelper {
  private static final String WORKING_DIR_BASE = "./repository/sam/";

  @Inject private Map<String, AwsSamCommandTaskHandler> commandTaskTypeToTaskHandlerMap;
  @Inject private AwsSamInfraConfigHelper awsSamInfraConfigHelper;
  @Inject private AwsSamCommandTaskHelper awsSamCommandTaskHelper;
  public AwsSamCommandResponse getCommandResponse(
      AwsSamCommandRequest awsSamCommandRequest, ILogStreamingTaskClient iLogStreamingTaskClient) {
    CommandUnitsProgress commandUnitsProgress = awsSamCommandRequest.getCommandUnitsProgress() != null
        ? awsSamCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", awsSamCommandRequest.getAwsSamCommandType().name());
    decryptRequestDTOs(awsSamCommandRequest);
    String workingDirectory = Paths.get(WORKING_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                  .normalize()
                                  .toAbsolutePath()
                                  .toString();

    AwsSamCommandTaskHandler commandTaskHandler =
        commandTaskTypeToTaskHandlerMap.get(awsSamCommandRequest.getAwsSamCommandType().name());
    try {
      createDirectoryIfDoesNotExist(workingDirectory);
      waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);
      AwsSamDelegateTaskParams awsSamDelegateTaskParams =
          AwsSamDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
      AwsSamCommandResponse awsSamCommandResponse = commandTaskHandler.executeTask(
          awsSamCommandRequest, awsSamDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
      awsSamCommandResponse.setCommandUnitsProgress(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return awsSamCommandResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing aws sam task [{}]",
          awsSamCommandRequest.getCommandName() + ":" + awsSamCommandRequest.getAwsSamCommandType(),
          sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      awsSamCommandTaskHelper.cleanup(workingDirectory);
    }
  }

  private void decryptRequestDTOs(AwsSamCommandRequest awsSamCommandRequest) {
    awsSamInfraConfigHelper.decryptAwsSamInfraConfig(awsSamCommandRequest.getAwsSamInfraConfig());
  }
}
