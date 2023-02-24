/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.sam;

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
import io.harness.delegate.task.aws.sam.AwsSamManifestConfig;
import io.harness.delegate.task.aws.sam.AwsSamValidateBuildPackageConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamPublishRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamValidateBuildPackageRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamCommandResponse;
import io.harness.delegate.task.aws.sam.response.AwsSamValidateBuildPackageResponse;
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
public class AwsSamValidateBuildPackageTaskHandler extends AwsSamCommandTaskHandler {
  private AwsSamClient awsSamClient;
  private long timeoutInMillis;
  private Map<String, String> envVariables;

  @Inject private AwsSamCommandTaskHelper awsSamCommandTaskHelper;
  @Inject private AwsSamInfraConfigHelper awsSamInfraConfigHelper;

  @Override
  protected AwsSamCommandResponse executeTaskInternal(AwsSamCommandRequest awsSamCommandRequest,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(awsSamCommandRequest instanceof AwsSamValidateBuildPackageRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("(awsSamCommandRequest", "Must be instance of AwsSamValidateBuildPackageRequest"));
    }
    AwsSamValidateBuildPackageRequest awsSamValidateBuildPackageRequest =
        (AwsSamValidateBuildPackageRequest) awsSamCommandRequest;
    if (!(awsSamValidateBuildPackageRequest.getAwsSamInfraConfig() instanceof AwsSamInfraConfig)) {
      throw new InvalidArgumentsException(Pair.of("AwsSamInfraConfig", "Must be instance of AwsSamInfraConfig"));
    }

    if (!(awsSamValidateBuildPackageRequest.getAwsSamManifestConfig() instanceof AwsSamManifestConfig)) {
      throw new InvalidArgumentsException(Pair.of("AwsSamManifestConfig", "Must be instance of AwsSamManifestConfig"));
    }

    AwsSamInfraConfig awsSamInfraConfig = awsSamValidateBuildPackageRequest.getAwsSamInfraConfig();
    AwsSamValidateBuildPackageConfig awsSamValidateBuildPackageConfig =
        awsSamValidateBuildPackageRequest.getAwsSamValidateBuildPackageConfig();
    AwsSamManifestConfig awsSamManifestConfig = awsSamValidateBuildPackageRequest.getAwsSamManifestConfig();

    timeoutInMillis = awsSamValidateBuildPackageRequest.getTimeoutIntervalInMin() * 60000;
    awsSamClient = new AwsSamClient();
    envVariables =
        awsSamCommandTaskHelper.getAwsCredentialsEnvironmentVariables(awsSamDelegateTaskParams.getWorkingDirectory());

    String awsSamCredentialType = awsSamInfraConfigHelper.getAwsSamCredentialType(awsSamInfraConfig);
    AwsCliConfig awsCliConfig = awsSamCommandTaskHelper.getAwsCliConfigFromAwsSamInfra(awsSamInfraConfig);

    LogCallback setupDirectoryLogCallback = new NGDelegateLogCallback(
        iLogStreamingTaskClient, AwsSamCommandUnitConstants.setupDirectory.toString(), true, commandUnitsProgress);
    awsSamCommandTaskHelper.printCommandRequestFilesContent(
        awsSamValidateBuildPackageRequest, setupDirectoryLogCallback);
    try {
      awsSamCommandTaskHelper.setupDirectory(
          awsSamValidateBuildPackageRequest, setupDirectoryLogCallback, awsSamDelegateTaskParams);
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

    LogCallback validateBuildPackageLogCallback = new NGDelegateLogCallback(iLogStreamingTaskClient,
        AwsSamCommandUnitConstants.validateBuildPackage.toString(), true, commandUnitsProgress);
    try {
      awsSamCommandTaskHelper.validate(awsSamClient, awsSamValidateBuildPackageConfig.getValidateCommandOptions(),
          awsSamDelegateTaskParams, awsSamInfraConfig, timeoutInMillis, awsSamManifestConfig, envVariables,
          validateBuildPackageLogCallback);
      validateBuildPackageLogCallback.saveExecutionLog(format("Validate command successful..%n"), LogLevel.INFO);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(
          ex, validateBuildPackageLogCallback, "Validate Command Failed");
    }

    try {
      awsSamCommandTaskHelper.build(awsSamClient, awsSamValidateBuildPackageConfig.getBuildCommandOptions(),
          awsSamDelegateTaskParams, awsSamInfraConfig, timeoutInMillis, awsSamManifestConfig, envVariables,
          validateBuildPackageLogCallback);
      validateBuildPackageLogCallback.saveExecutionLog(format("Build command successful...%n"), LogLevel.INFO);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(
          ex, validateBuildPackageLogCallback, "Build Command Failed");
    }

    String packageTemplateContent = null;
    try {
      packageTemplateContent = awsSamCommandTaskHelper.packagee(awsSamClient,
          awsSamValidateBuildPackageConfig.getPackageCommandOptions(), awsSamDelegateTaskParams, awsSamInfraConfig,
          timeoutInMillis, awsSamManifestConfig, envVariables, validateBuildPackageLogCallback);

      validateBuildPackageLogCallback.saveExecutionLog(
          format("Package command successful...%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      awsSamCommandTaskHelper.saveErrorLogAndCloseLogStream(
          ex, validateBuildPackageLogCallback, "Package Command Failed");
    }
    return AwsSamValidateBuildPackageResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
        .templateContent(packageTemplateContent)
        .build();
  }
}
