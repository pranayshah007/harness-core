/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.awscli.AwsCliClient;
import io.harness.cli.CliResponse;
import io.harness.delegate.beans.aws.AwsCliStsAssumeRoleCommandOutputSchema;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.serializer.YamlUtils;

import lombok.extern.slf4j.Slf4j;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsCliDelegateTaskHelper {
  private final YamlUtils yamlUtils = new YamlUtils();

  public void configureAwsCredentials(AwsCliClient awsCliClient, LogCallback executionLogCallback,
                                      AwsCliCredentialConfig awsCliCredentialConfig, long timeoutInMillis, Map<String, String> envVariables,
                                      String workingDirectory, AwsCliConfig awsCliConfig) throws InterruptedException, IOException, TimeoutException {
    configureAwsCredentialCommand(awsCliClient, executionLogCallback, "aws_access_key_id",
            awsCliCredentialConfig.getAccessKey(), timeoutInMillis, envVariables, workingDirectory,
            "aws configure set aws_access_key_id ***");

    configureAwsCredentialCommand(awsCliClient, executionLogCallback, "aws_secret_access_key",
            awsCliCredentialConfig.getSecretKey(), timeoutInMillis, envVariables, workingDirectory,
            "aws configure set aws_secret_access_key ***");

    configureAwsCredentialCommand(awsCliClient, executionLogCallback, "region", awsCliConfig.getRegion(),
            timeoutInMillis, envVariables, workingDirectory, "");
  }

  public void configureAwsCredentialCommand(AwsCliClient awsCliClient, LogCallback executionLogCallback,
                                            String configureOption, String configureValue, long timeoutInMillis, Map<String, String> envVariables,
                                            String workingDirectory, String loggingCommand) throws InterruptedException, IOException, TimeoutException {
    CliResponse response = awsCliClient.setConfigure(configureOption, configureValue, envVariables, workingDirectory,
            executionLogCallback, timeoutInMillis, loggingCommand);
    if (response.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      awsCommandFailure(executionLogCallback, loggingCommand, response);
    }
  }

  private void awsCommandFailure(LogCallback executionLogCallback, String command, CliResponse cliResponse) {
    StringBuilder message = new StringBuilder(1024);
    message.append("\n following command failed");
    if (isNotEmpty(command)) {
      message.append(command);
    } else {
      message.append(" aws command");
    }
    if (isNotEmpty(cliResponse.getOutput())) {
      message.append(" with output: \n ").append(cliResponse.getOutput());
    }
    if (isNotEmpty(cliResponse.getError())) {
      message.append(" with error: \n ").append(cliResponse.getError());
    }
    executionLogCallback.saveExecutionLog(color(message.toString(), LogColor.Red, LogWeight.Bold), ERROR);
    handleAwsCommandExecutionFailure(command);
  }

  public void handleAwsCommandExecutionFailure(String printCommand) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(
            new InvalidRequestException(format("%s command failed.", printCommand)));
    throw NestedExceptionUtils.hintWithExplanationException(format("Please check and fix %s command. ", printCommand),
            format("%s command failed. Please check Pipeline Execution"
                            + " logs for more details.",
                    printCommand),
            sanitizedException);
  }

  public void awsStsAssumeRole(AwsCliClient awsCliClient, LogCallback executionLogCallback, long timeoutInMillis,
                               Map<String, String> envVariables, String workingDirectory, AwsCliConfig awsCliConfig)
          throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("Setting up AWS Cross-account access..\n");
    CrossAccountAccessDTO crossAccountAccess = awsCliConfig.getCredential().getCrossAccountAccess();
    String command = "aws sts assume-role --role-arn ***";
    CliResponse response = awsCliClient.stsAssumeRole(crossAccountAccess.getCrossAccountRoleArn(),
            convertBase64UuidToCanonicalForm(generateUuid()), crossAccountAccess.getExternalId(), envVariables,
            workingDirectory, executionLogCallback, timeoutInMillis, "");
    if (response.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      awsCommandFailure(executionLogCallback, command, response);
    }

    AwsCliStsAssumeRoleCommandOutputSchema responseOutput =
            yamlUtils.read(response.getOutput(), AwsCliStsAssumeRoleCommandOutputSchema.class);
    AwsCliCredentialConfig awsSamCredentialConfig = AwsCliCredentialConfig.builder()
            .accessKey(responseOutput.getCredentials().getAccessKeyId())
            .secretKey(responseOutput.getCredentials().getSecretAccessKey())
            .build();

    executionLogCallback.saveExecutionLog("Setting up temporary AWS cross account credentials..\n");
    configureAwsCredentials(awsCliClient, executionLogCallback, awsSamCredentialConfig, timeoutInMillis, envVariables,
            workingDirectory, awsCliConfig);

    command = "aws configure set aws_session_token *** ";
    response = awsCliClient.setConfigure("aws_session_token", responseOutput.getCredentials().getSessionToken(),
            envVariables, workingDirectory, executionLogCallback, timeoutInMillis, "");
    if (response.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      awsCommandFailure(executionLogCallback, command, response);
    }
  }

  public AwsCliCredentialConfig getAwsCliConfigFromManualCreds(AwsManualConfigSpecDTO awsManualConfigSpecDTO) {
    AwsCliCredentialConfig.AwsCliCredentialConfigBuilder awsCliCredentialConfigBuilder =
            AwsCliCredentialConfig.builder();
    awsCliCredentialConfigBuilder.accessKey(getSecretAsStringFromPlainTextOrSecretRef(
            awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef()));
    awsCliCredentialConfigBuilder.secretKey(
            String.valueOf(awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue()));
    return awsCliCredentialConfigBuilder.build();
  }

  public boolean getAwsCrossAccountFlag(AwsCredentialDTO awsCredentialDTO) {
    CrossAccountAccessDTO crossAccountAccess = awsCredentialDTO.getCrossAccountAccess();
    if (crossAccountAccess != null && crossAccountAccess.getCrossAccountRoleArn() != null) {
      return true;
    }
    return false;
  }
}