/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.sam;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.sam.AwsSamClient;
import io.harness.aws.sam.command.AwsSamCommandUnitConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.AwsCliConfig;
import io.harness.delegate.task.aws.sam.AwsSamDelegateTaskParams;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamPublishConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamPublishRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamCommandResponse;
import io.harness.delegate.task.aws.sam.response.AwsSamPublishResponse;
import io.harness.delegate.task.sam.AwsSamCommandTaskHelper;
import io.harness.delegate.task.sam.AwsSamInfraConfigHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class AwsSamPublishCommandTaskHandler extends AwsSamCommandTaskHandler {
  private AwsSamClient awsSamClient;
  private long timeoutInMillis;
  private Map<String, String> envVariables;

  @Inject private AwsSamCommandTaskHelper awsSamCommandTaskHelper;
  @Inject private AwsSamInfraConfigHelper awsSamInfraConfigHelper;
  @Override
  protected AwsSamCommandResponse executeTaskInternal(AwsSamCommandRequest awsSamCommandRequest,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(awsSamCommandRequest instanceof AwsSamPublishRequest)) {
      throw new InvalidArgumentsException(Pair.of("(awsSamCommandRequest",
          "Must be instance of "
              + "AwsSamPublishRequest"));
    }
    AwsSamPublishRequest awsSamPublishRequest = (AwsSamPublishRequest) awsSamCommandRequest;
    if (!(awsSamPublishRequest.getAwsSamInfraConfig() instanceof AwsSamInfraConfig)) {
      throw new InvalidArgumentsException(Pair.of("AwsSamInfraConfig", "Must be instance of AwsSamInfraConfig"));
    }

    if (isEmpty(awsSamPublishRequest.getTemplateFileContent())) {
      throw new InvalidArgumentsException("Invalid AwsSamPublishRequest");
    }

    AwsSamInfraConfig awsSamInfraConfig = awsSamPublishRequest.getAwsSamInfraConfig();
    AwsSamPublishConfig awsSamPublishConfig = awsSamPublishRequest.getAwsSamPublishConfig();
    LogCallback executionLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, AwsSamCommandUnitConstants.publish.toString(), true, commandUnitsProgress);

    executionLogCallback.saveExecutionLog(format("Deploying..%n%n"), LogLevel.INFO);
    timeoutInMillis = awsSamPublishRequest.getTimeoutIntervalInMin() * 60000;
    awsSamClient = new AwsSamClient();
    envVariables =
        awsSamCommandTaskHelper.getAwsCredentialsEnvironmentVariables(awsSamDelegateTaskParams.getWorkingDirectory());

    String awsSamCredentialType = awsSamInfraConfigHelper.getAwsSamCredentialType(awsSamInfraConfig);
    AwsCliConfig awsCliConfig = awsSamCommandTaskHelper.getAwsCliConfigFromAwsSamInfra(awsSamInfraConfig);

    try {
      try {
        awsSamCommandTaskHelper.saveTemplateAndConfigFileToDirectory(awsSamDelegateTaskParams.getWorkingDirectory(),
            awsSamPublishRequest.getTemplateFileContent(), awsSamPublishRequest.getConfigFileContent());
      } catch (Exception ex) {
        awsSamCommandTaskHelper.errorHandling(ex, executionLogCallback, "Saving files failed with error");
      }

      try {
        awsSamCommandTaskHelper.setUpConfigureCredential(awsSamDelegateTaskParams.getWorkingDirectory(),
            awsSamCredentialType, timeoutInMillis, envVariables, awsCliConfig, executionLogCallback);
        executionLogCallback.saveExecutionLog(format("Done...%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      } catch (Exception ex) {
        awsSamCommandTaskHelper.errorHandling(ex, executionLogCallback, "Configure credentials failed with error");
      }

      awsSamCommandTaskHelper.printPublishFilesContent(awsSamPublishRequest, executionLogCallback);

      try {
        awsSamCommandTaskHelper.publish(awsSamClient, awsSamPublishConfig.getPublishCommandOptions(),
            awsSamDelegateTaskParams, awsSamInfraConfig, timeoutInMillis, envVariables, executionLogCallback);
      } catch (Exception ex) {
        awsSamCommandTaskHelper.errorHandling(ex, executionLogCallback, "Publish failed with error");
      }

      return AwsSamPublishResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog("Publish Failed .", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }
}
