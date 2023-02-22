/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.sam;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsCliCredentialConfig;
import io.harness.aws.sam.AwsSamCliResponse;
import io.harness.aws.sam.AwsSamClient;
import io.harness.aws.sam.AwsSamCommandExecuteHelper;
import io.harness.aws.sam.AwsSamPublishCommand;
import io.harness.aws.sam.AwsSamValidateCommand;
import io.harness.awscli.AwsCliClient;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.AwsCliConfig;
import io.harness.delegate.task.aws.AwsCliDelegateTaskHelper;
import io.harness.delegate.task.aws.sam.AwsSamDelegateTaskParams;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamManifestConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamPublishRequest;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.serializer.YamlUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class AwsSamCommandTaskHelper {
  private final YamlUtils yamlUtils = new YamlUtils();

  @Inject private AwsCliDelegateTaskHelper awsCliDelegateTaskHelper;
  @Inject private AwsCliClient awsCliClient;

  public AwsSamCliResponse validate(AwsSamClient awsSamClient, String commandOptions,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, AwsSamInfraConfig awsSamInfraConfig, long timeoutInMillis,
      AwsSamManifestConfig awsSamManifestConfig, Map<String, String> envVariables, LogCallback executionLogCallback)
      throws IOException, InterruptedException, TimeoutException {
    executionLogCallback.saveExecutionLog("AWS SAM Validate ..\n");
    AwsSamValidateCommand command =
        awsSamClient.validate().options(commandOptions).region(awsSamInfraConfig.getRegion());
    if (isNotEmpty(awsSamManifestConfig.getSamTemplateFilePath())) {
      command.templatePath(awsSamManifestConfig.getSamTemplateFilePath());
    }
    if (isNotEmpty(awsSamManifestConfig.getSamConfigFilePath())) {
      command.configPath(awsSamManifestConfig.getSamConfigFilePath());
    }
    return AwsSamCommandExecuteHelper.executeCommand(command, awsSamDelegateTaskParams.getWorkingDirectory(),
        executionLogCallback, true, timeoutInMillis, envVariables);
  }

  public void setUpConfigureCredential(String workingDirectory, String awsSamCredentialType, long timeoutInMillis,
      Map<String, String> environmentVariables, AwsCliConfig awsCliConfig, LogCallback executionLogCallback)
      throws Exception {
    boolean crossAccountAccessFlag = awsCliDelegateTaskHelper.getAwsCrossAccountFlag(awsCliConfig.getCredential());

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
  }

  public AwsCliConfig getAwsCliConfigFromAwsSamInfra(AwsSamInfraConfig awsSamInfraConfig) {
    return AwsCliConfig.builder()
        .credential(awsSamInfraConfig.getAwsConnectorDTO().getCredential())
        .region(awsSamInfraConfig.getRegion())
        .build();
  }

  public Map<String, String> getAwsCredentialsEnvironmentVariables(String workingDirectory) {
    String credentialsParentDirectory =
        Paths.get(workingDirectory, convertBase64UuidToCanonicalForm(generateUuid())).toString();
    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put(
        "AWS_SHARED_CREDENTIALS_FILE", Paths.get(credentialsParentDirectory, "credentials").toString());
    environmentVariables.put("AWS_CONFIG_FILE", Paths.get(credentialsParentDirectory, "config").toString());
    return environmentVariables;
  }

  public void saveTemplateAndConfigFileToDirectory(String workingDirectory, String templateFileContent,
      String configFileContent, LogCallback logCallback) throws Exception {
    String templateFilePath = Paths.get(workingDirectory, "template.yaml").toString();
    String configFilePath = Paths.get(workingDirectory, "samconfig.toml").toString();
    FileIo.writeUtf8StringToFile(templateFilePath, templateFileContent);

    if (isNotEmpty(configFileContent)) {
      FileIo.writeUtf8StringToFile(configFilePath, configFileContent);
    }
  }

  public AwsSamCliResponse publish(AwsSamClient awsSamClient, String commandOptions,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, AwsSamInfraConfig awsSamInfraConfig, long timeoutInMillis,
      Map<String, String> envVariables, LogCallback executionLogCallback) throws Exception {
    AwsSamPublishCommand command = awsSamClient.publish().options(commandOptions).region(awsSamInfraConfig.getRegion());

    AwsSamCliResponse awsSamCliResponse = AwsSamCommandExecuteHelper.executeCommand(command,
        awsSamDelegateTaskParams.getWorkingDirectory(), executionLogCallback, true, timeoutInMillis, envVariables);

    if (awsSamCliResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      throw new InvalidRequestException(format(awsSamCliResponse.getOutput()));
    }
    return awsSamCliResponse;
  }

  public void printPublishFilesContent(AwsSamPublishRequest awsSamPublishRequest, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(color(format("%nPublish template file content:"), White, Bold));
    executionLogCallback.saveExecutionLog(awsSamPublishRequest.getTemplateFileContent());

    if (isNotEmpty(awsSamPublishRequest.getConfigFileContent())) {
      executionLogCallback.saveExecutionLog(color(format("%nPublish config file content:"), White, Bold));
      executionLogCallback.saveExecutionLog(awsSamPublishRequest.getConfigFileContent());
    }
  }

  public void saveErrorLogAndCloseLogStream(Exception ex, LogCallback executionLogCallback, String message)
      throws Exception {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
    String error = ExceptionUtils.getMessage(sanitizedException);
    executionLogCallback.saveExecutionLog(
        format("%n " + message + ": %s", error), ERROR, CommandExecutionStatus.FAILURE);
    log.error(format(message + "%n%s", ExceptionUtils.getMessage(sanitizedException)));
    throw ex;
  }
}
