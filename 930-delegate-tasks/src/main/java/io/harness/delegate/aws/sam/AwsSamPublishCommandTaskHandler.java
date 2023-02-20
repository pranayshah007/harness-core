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
      throw new InvalidArgumentsException(Pair.of("(awsSamCommandRequest", "Must be instance of AwsSamPublishRequest"));
    }
    AwsSamPublishRequest awsSamPublishRequest = (AwsSamPublishRequest) awsSamCommandRequest;
    if (!(awsSamPublishRequest.getAwsSamInfraConfig() instanceof AwsSamInfraConfig)) {
      throw new InvalidArgumentsException(Pair.of("AwsSamInfraConfig", "Must be instance of AwsSamInfraConfig"));
    }

    if (isEmpty(awsSamPublishRequest.getTemplateFileContent())) {
      throw new InvalidArgumentsException(Pair.of("AwsSamPublishRequest", "Template File Content shouldn't be empty"));
    }

    AwsSamInfraConfig awsSamInfraConfig = awsSamPublishRequest.getAwsSamInfraConfig();
    AwsSamPublishConfig awsSamPublishConfig = awsSamPublishRequest.getAwsSamPublishConfig();

    timeoutInMillis = awsSamPublishRequest.getTimeoutIntervalInMin() * 60000;
    awsSamClient = new AwsSamClient();
    envVariables =
        awsSamCommandTaskHelper.getAwsCredentialsEnvironmentVariables(awsSamDelegateTaskParams.getWorkingDirectory());

    String awsSamCredentialType = awsSamInfraConfigHelper.getAwsSamCredentialType(awsSamInfraConfig);
    AwsCliConfig awsCliConfig = awsSamCommandTaskHelper.getAwsCliConfigFromAwsSamInfra(awsSamInfraConfig);

    LogCallback setupDirectoryLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, AwsSamCommandUnitConstants.setupDirectory.toString(), true, commandUnitsProgress);
    try {
      // Setup Directory
      setupDirectoryLogCallback.saveExecutionLog(format("Setting up AWS SAM directory..%n%n"), LogLevel.INFO);
      awsSamCommandTaskHelper.saveTemplateAndConfigFileToDirectory(awsSamDelegateTaskParams.getWorkingDirectory(),
          awsSamPublishRequest.getTemplateFileContent(), awsSamPublishRequest.getConfigFileContent(),
          setupDirectoryLogCallback);
      setupDirectoryLogCallback.saveExecutionLog("Done ..", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(
          ex, setupDirectoryLogCallback, "Failed to setup AWS SAM directory ");
    }

    LogCallback configureCredLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, AwsSamCommandUnitConstants.configureCred.toString(), true, commandUnitsProgress);
    try {
      // Configure Credentials
      awsSamCommandTaskHelper.setUpConfigureCredential(awsSamDelegateTaskParams.getWorkingDirectory(),
          awsSamCredentialType, timeoutInMillis, envVariables, awsCliConfig, configureCredLogCallback);
      configureCredLogCallback.saveExecutionLog(format("Done...%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(
          ex, configureCredLogCallback, "Configure Credentials failed");
    }

    LogCallback publishLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, AwsSamCommandUnitConstants.publish.toString(), true, commandUnitsProgress);
    try {
      // Publish
      awsSamCommandTaskHelper.printPublishFilesContent(awsSamPublishRequest, publishLogCallback);
      awsSamCommandTaskHelper.publish(awsSamClient, awsSamPublishConfig.getPublishCommandOptions(),
          awsSamDelegateTaskParams, awsSamInfraConfig, timeoutInMillis, envVariables, publishLogCallback);
      publishLogCallback.saveExecutionLog(format("Done...%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(ex, publishLogCallback, "Publish Failed");
    }

    return AwsSamPublishResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .build();
  }
}
