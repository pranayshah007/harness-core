/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.sam;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.sam.AwsSamClient;
import io.harness.aws.sam.AwsSamCommandHelper;
import io.harness.aws.sam.AwsSamCommandUnitConstants;
import io.harness.aws.sam.DeployCommand;
import io.harness.awscli.AwsCliClient;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.aws.AwsCliConfig;
import io.harness.delegate.task.aws.AwsCliCredentialConfig;
import io.harness.delegate.task.aws.AwsCliDelegateTaskHelper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.sam.AwsSamDeployConfig;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamDeployRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamDeployResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.waitForDirectoryToBeAccessibleOutOfProcess;
import static io.harness.logging.LogLevel.INFO;
import static java.lang.String.format;
import static software.wings.beans.LogHelper.color;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamDelegateTaskHelper {

  @Inject private AwsSamInfraConfigHelper awsSamInfraConfigHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsSamArtifactHelper awsSamArtifactHelper;
  @Inject private AwsCliDelegateTaskHelper awsCliDelegateTaskHelper;
  @Inject private AwsCliClient awsCliClient;

  private static final String WORKING_DIR_BASE = "./repository/sam/";

  public AwsSamDeployResponse handleAwsSamDeployRequest(AwsSamDeployRequest awsSamDeployRequest,
                                                        ILogStreamingTaskClient iLogStreamingTaskClient) throws Exception {

    CommandUnitsProgress commandUnitsProgress = awsSamDeployRequest.getCommandUnitsProgress() != null
            ? awsSamDeployRequest.getCommandUnitsProgress()
            : CommandUnitsProgress.builder().build();

    log.info("Starting task execution for AWS SAM Deploy");

    decryptRequestDTOs(awsSamDeployRequest);

    String workingDirectory = Paths.get(WORKING_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
            .normalize()
            .toAbsolutePath()
            .toString();

    createDirectoryIfDoesNotExist(workingDirectory);

    waitForDirectoryToBeAccessibleOutOfProcess(workingDirectory, 10);

    long timeoutInMillis = awsSamDeployRequest.getTimeoutIntervalInMillis();


    AwsSamInfraConfig awsSamInfraConfig = awsSamDeployRequest.getAwsSamInfraConfig();

    String awsSamCredentialType =
            awsSamInfraConfigHelper.getAwsSamCredentialType(awsSamInfraConfig);

    LogCallback artifactLogCallback = new NGDelegateLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.artifact.toString(), true, commandUnitsProgress);
    try {
      awsSamArtifactHelper.fetchArtifact(awsSamDeployRequest.getAwsSamArtifactConfig(),
              artifactLogCallback,
              workingDirectory);
      artifactLogCallback.saveExecutionLog(format("Done..%n"), INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      artifactLogCallback.saveExecutionLog(
              color(format("%n Artifact download failed with error: %s", getExceptionMessage(ex)),
                      LogColor.Red, LogWeight.Bold),
              LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    AwsCliConfig awsCliConfig = getAwsCliConfigFromAwsSamInfra(awsSamInfraConfig);

    AwsSamClient awsSamClient = AwsSamClient.client(null);

    LogCallback configureCredsLogCallback = new NGDelegateLogCallback(
            iLogStreamingTaskClient, AwsSamCommandUnitConstants.configureCred.toString(), true, commandUnitsProgress);

    try {
      setUpConfigureCredential(workingDirectory, awsSamCredentialType,
              timeoutInMillis, null, awsCliConfig, configureCredsLogCallback);

      configureCredsLogCallback.saveExecutionLog(format("Done...%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } catch (Exception ex) {
      configureCredsLogCallback.saveExecutionLog(color(format("%n Configure credentials failed with error: %s",
                              getExceptionMessage(ex)),
                      LogColor.Red, LogWeight.Bold),
              LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback deployLogCallback = new NGDelegateLogCallback(
            iLogStreamingTaskClient, AwsSamCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    try {
      AwsSamDeployConfig awsSamDeployConfig = awsSamDeployRequest.getAwsSamDeployConfig();

      DeployCommand deployCommand = awsSamClient
              .deploy()
              .stackName(awsSamDeployConfig.getStackName())
              .region(awsSamInfraConfig.getRegion())
              .options(awsSamDeployConfig.getDeployCommandOptions());

      AwsSamCommandHelper.executeCommand(deployCommand, workingDirectory, deployLogCallback, true, timeoutInMillis, null);


    } catch (Exception ex) {
      deployLogCallback.saveExecutionLog(
              color(format("%n Deployment failed with error: %s", getExceptionMessage(ex)),
                      LogColor.Red, LogWeight.Bold),
              LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new ServerlessNGException(ex);
    }

    AwsSamDeployResponse awsSamDeployResponse = AwsSamDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();

    return awsSamDeployResponse;
  }


  public AwsCliConfig getAwsCliConfigFromAwsSamInfra(AwsSamInfraConfig awsSamInfraConfig) {
    return AwsCliConfig.builder()
            .credential(awsSamInfraConfig.getAwsConnectorDTO().getCredential())
            .region(awsSamInfraConfig.getRegion())
            .build();
  }

  private void decryptRequestDTOs(AwsSamCommandRequest awsSamCommandRequest) {
    awsSamInfraConfigHelper.decryptAwsSamInfraConfig(awsSamCommandRequest.getAwsSamInfraConfig());
  }

  public String getExceptionMessage(Exception e) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
    return ExceptionUtils.getMessage(sanitizedException);
  }

  public Map<String, String> getAwsCredentialsEnvironmentVariables(
          ServerlessDelegateTaskParams serverlessDelegateTaskParams) {
    String credentialsParentDirectory =
            Paths.get(serverlessDelegateTaskParams.getWorkingDirectory(), convertBase64UuidToCanonicalForm(generateUuid()))
                    .toString();
    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put(
            "AWS_SHARED_CREDENTIALS_FILE", Paths.get(credentialsParentDirectory, "credentials").toString());
    environmentVariables.put("AWS_CONFIG_FILE", Paths.get(credentialsParentDirectory, "config").toString());
    return environmentVariables;
  }

  public void setUpConfigureCredential(String workingDirectory, String awsSamCredentialType, long timeoutInMillis,
                                       Map<String, String> environmentVariables, AwsCliConfig awsCliConfig, LogCallback executionLogCallback)
          throws Exception {
    boolean crossAccountAccessFlag = awsCliDelegateTaskHelper.getAwsCrossAccountFlag(awsCliConfig.getCredential());
    try {
      executionLogCallback.saveExecutionLog("Setting up AWS config credentials..\n");
      if (awsSamCredentialType.equals(AwsCredentialType.MANUAL_CREDENTIALS.name())) {
        AwsCliCredentialConfig awsCliCredentialConfig = awsCliDelegateTaskHelper.getAwsCliConfigFromManualCreds(
                (AwsManualConfigSpecDTO) awsCliConfig.getCredential().getConfig());

        awsCliDelegateTaskHelper.configureAwsCredentials(awsCliClient, executionLogCallback, awsCliCredentialConfig,
                timeoutInMillis, environmentVariables, workingDirectory, awsCliConfig);
      }
      if (crossAccountAccessFlag) {
        awsCliDelegateTaskHelper.awsStsAssumeRole(
                awsCliClient, executionLogCallback, timeoutInMillis, environmentVariables, workingDirectory, awsCliConfig);
      }
      executionLogCallback.saveExecutionLog(
              color(format("%nConfig Credential command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
              color(format("%n configure credential failed with error: %s", ex.getMessage()), LogColor.Red, LogWeight.Bold),
              LogLevel.ERROR);
      throw ex;
    }
  }

}
