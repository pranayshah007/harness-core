/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AWSCloudformationClient;
import io.harness.awscli.AwsCliClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.StackDetails;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackV2Config;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessRollbackV2Request;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse.ServerlessRollbackResponseBuilder;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.RollbackTrigger;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackResult;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ServerlessAwsLambdaRollbackV2CommandTaskHandler {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;

  @Inject protected AWSCloudformationClient awsHelperService;
  @Inject private AwsCliClient awsCliClient;

  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private String serverlessAwsLambdaCredentialType;
  private boolean crossAccountAccessFlag;
  private Map<String, String> environmentVariables;

  public ServerlessCommandResponse executeTaskInternal(ServerlessRollbackV2Request serverlessRollbackRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    validateRollbackParams(serverlessRollbackRequest);
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessRollbackRequest.getServerlessInfraConfig();
    serverlessAwsLambdaCredentialType =
        serverlessInfraConfigHelper.getServerlessAwsLambdaCredentialType(serverlessAwsLambdaInfraConfig);

    crossAccountAccessFlag = serverlessInfraConfigHelper.getAwsCrossAccountFlag(serverlessAwsLambdaInfraConfig);

    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
        serverlessRollbackRequest.getServerlessInfraConfig());

    environmentVariables =
        serverlessAwsCommandTaskHelper.getAwsCredentialsEnvironmentVariables(serverlessDelegateTaskParams);

    LogCallback rollbackLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    try {
      return rollback(serverlessRollbackRequest, rollbackLogCallback);
    } catch (Exception ex) {
      rollbackLogCallback.saveExecutionLog(
          color(format("%n Rollback failed with error: %s", serverlessTaskHelperBase.getExceptionMessage(ex)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  private void validateRollbackParams(ServerlessRollbackV2Request serverlessRollbackRequest) {
    if (!(serverlessRollbackRequest instanceof ServerlessRollbackV2Request)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessRollbackV2Request"));
    }
    if (!(serverlessRollbackRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessRollbackRequest.getServerlessRollbackConfig() instanceof ServerlessAwsLambdaRollbackV2Config)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessRollbackConfig", "Must be instance of ServerlessAwsLambdaRollbackV2Config"));
    }
  }

  private ServerlessRollbackResponse rollback(
      ServerlessRollbackV2Request serverlessRollbackRequest, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(format("Starting Rollback..%n"));
    ServerlessAwsLambdaRollbackV2Config serverlessAwsLambdaRollbackConfig =
        serverlessRollbackRequest.getServerlessRollbackConfig();
    ServerlessRollbackResponseBuilder serverlessRollbackResponseBuilder = ServerlessRollbackResponse.builder();

    if (serverlessAwsLambdaRollbackConfig.isFirstDeployment()) {
      executionLogCallback.saveExecutionLog(
          format("Since its first deployment using harness and it fails, please handle the rollback manually. %n"),
          LogLevel.INFO);
    } else {
      updateStackDetails(serverlessRollbackRequest, executionLogCallback);
    }

    executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
    return serverlessRollbackResponseBuilder.build();
  }

  public void updateStackDetails(
      ServerlessRollbackV2Request serverlessRollbackV2Request, LogCallback executionLogCallback) {
    StackDetails stackDetails = serverlessRollbackV2Request.getServerlessRollbackConfig().getStackDetails();
    if (stackDetails == null) {
      throw new InvalidRequestException("Serverless Prepare Rollback Step did not complete successfully");
    }
    UpdateStackRequest updateStackRequest = new UpdateStackRequest();
    updateStackRequest.setStackName(stackDetails.getStackName());
    updateStackRequest.setTemplateBody(stackDetails.getTemplateBody());
    updateStackRequest.setCapabilities(stackDetails.getCapabilities());
    updateStackRequest.setDisableRollback(stackDetails.getDisableRollback());
    updateStackRequest.setRoleARN(stackDetails.getRoleARN());
    updateStackRequest.setStackPolicyBody(stackDetails.getStackPolicyBody());
    updateStackRequest.setNotificationARNs(stackDetails.getNotificationARNs());

    if (EmptyPredicate.isNotEmpty(stackDetails.getParameters())) {
      List<Parameter> parameterList = stackDetails.getParameters()
                                          .stream()
                                          .map(parameter
                                              -> new Parameter()
                                                     .withParameterKey(parameter.getParameterKey())
                                                     .withParameterValue(parameter.getParameterValue()))
                                          .collect(Collectors.toList());
      updateStackRequest.setParameters(parameterList);
    }

    if (EmptyPredicate.isNotEmpty(stackDetails.getTags())) {
      List<Tag> tagList = stackDetails.getTags()
                              .stream()
                              .map(tag -> new Tag().withKey(tag.getKey()).withValue(tag.getValue()))
                              .collect(Collectors.toList());
      updateStackRequest.setTags(tagList);
    }

    if (stackDetails.getRollbackConfiguration() != null) {
      RollbackConfiguration rollbackConfiguration = new RollbackConfiguration();
      rollbackConfiguration.setMonitoringTimeInMinutes(
          stackDetails.getRollbackConfiguration().getMonitoringTimeInMinutes());
      if (EmptyPredicate.isNotEmpty(rollbackConfiguration.getRollbackTriggers())) {
        rollbackConfiguration.setRollbackTriggers(
            rollbackConfiguration.getRollbackTriggers()
                .stream()
                .map(rollbackTrigger
                    -> new RollbackTrigger().withArn(rollbackTrigger.getArn()).withType(rollbackTrigger.getType()))
                .collect(Collectors.toList()));
      }
      updateStackRequest.setRollbackConfiguration(rollbackConfiguration);
    }

    executionLogCallback.saveExecutionLog(
        color(format("# Calling AWS API update to update stack: %s", stackDetails.getStackName()), LogColor.White,
            LogWeight.Bold),
        LogLevel.INFO);

    UpdateStackResult updateStackResult =
        awsHelperService.updateStack(serverlessAwsLambdaInfraConfig.getRegion(), updateStackRequest,
            awsNgConfigMapper.createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO()));

    if (updateStackResult == null || updateStackResult.getStackId() == null) {
      executionLogCallback.saveExecutionLog(
          format("# There is nothing to be updated in the stack with name: %s", stackDetails.getStackName()));
    } else {
      executionLogCallback.saveExecutionLog(
          format("# Update completed for stack with name: %s", stackDetails.getStackName()));
    }
  }
}
