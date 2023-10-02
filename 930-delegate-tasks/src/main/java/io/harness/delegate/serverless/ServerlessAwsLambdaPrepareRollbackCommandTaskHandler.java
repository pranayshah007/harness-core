/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.awscli.AwsCliClient;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult.ServerlessAwsLambdaPrepareRollbackDataResultBuilder;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse.ServerlessPrepareRollbackDataResponseBuilder;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ServerlessAwsLambdaPrepareRollbackCommandTaskHandler extends ServerlessCommandTaskHandler {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  @Inject private AwsCliClient awsCliClient;

  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private long timeoutInMillis;
  private String previousDeployTimeStamp;
  private String serverlessAwsLambdaCredentialType;
  private boolean crossAccountAccessFlag;
  private Map<String, String> environmentVariables;

  @Override
  protected ServerlessCommandResponse executeTaskInternal(ServerlessCommandRequest serverlessCommandRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(serverlessCommandRequest instanceof ServerlessPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessPrepareRollbackDataRequest"));
    }
    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        (ServerlessPrepareRollbackDataRequest) serverlessCommandRequest;
    if (!(serverlessPrepareRollbackDataRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessPrepareRollbackDataRequest.getServerlessManifestConfig()
                instanceof ServerlessAwsLambdaManifestConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessManifestConfig", "Must be instance of ServerlessAwsLambdaManifestConfig"));
    }

    timeoutInMillis = serverlessPrepareRollbackDataRequest.getTimeoutIntervalInMin() * 60000;
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    serverlessAwsLambdaCredentialType =
        serverlessInfraConfigHelper.getServerlessAwsLambdaCredentialType(serverlessAwsLambdaInfraConfig);

    crossAccountAccessFlag = serverlessInfraConfigHelper.getAwsCrossAccountFlag(serverlessAwsLambdaInfraConfig);

    environmentVariables =
        serverlessAwsCommandTaskHelper.getAwsCredentialsEnvironmentVariables(serverlessDelegateTaskParams);

    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
        serverlessPrepareRollbackDataRequest.getServerlessInfraConfig());

    LogCallback executionLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true, commandUnitsProgress);
    try {
      setupDirectory(serverlessPrepareRollbackDataRequest, executionLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
          color(format("%n setup directory failed with error: %s", serverlessTaskHelperBase.getExceptionMessage(ex)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
    serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    try {
      serverlessAwsCommandTaskHelper.setUpConfigureCredential(serverlessAwsLambdaConfig, executionLogCallback,
          serverlessDelegateTaskParams, serverlessAwsLambdaCredentialType, serverlessClient, timeoutInMillis,
          crossAccountAccessFlag, environmentVariables, awsCliClient, serverlessAwsLambdaInfraConfig);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(color(format("%n configure credentials failed with error: %s",
                                                      serverlessTaskHelperBase.getExceptionMessage(ex)),
                                                LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    try {
      serverlessAwsCommandTaskHelper.installPlugins(serverlessManifestSchema, serverlessDelegateTaskParams,
          executionLogCallback, serverlessClient, timeoutInMillis, serverlessManifestConfig);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
          color(format("%n installing plugin failed with error: %s", serverlessTaskHelperBase.getExceptionMessage(ex)),
              LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    try {
      return prepareRollbackData(
          serverlessPrepareRollbackDataRequest, executionLogCallback, serverlessDelegateTaskParams);

    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(color(format("%n prepare rollback data failed with error: %s",
                                                      serverlessTaskHelperBase.getExceptionMessage(ex)),
                                                LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  private void setupDirectory(ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("setting up serverless directory..%n%n"));
    serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessPrepareRollbackDataRequest.getServerlessManifestConfig();
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
        serverlessPrepareRollbackDataRequest.getAccountId(), executionLogCallback, serverlessDelegateTaskParams);
    serverlessManifestSchema = serverlessAwsCommandTaskHelper.parseServerlessManifest(
        executionLogCallback, serverlessPrepareRollbackDataRequest.getManifestContent());
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams, serverlessManifestConfig,
        serverlessPrepareRollbackDataRequest.getManifestContent(), serverlessManifestSchema);
  }

  private ServerlessPrepareRollbackDataResponse prepareRollbackData(
      ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"));
    ServerlessPrepareRollbackDataResponseBuilder serverlessPrepareRollbackDataResponseBuilder =
        ServerlessPrepareRollbackDataResponse.builder();
    ServerlessAwsLambdaPrepareRollbackDataResultBuilder serverlessAwsLambdaPrepareRollbackDataResultBuilder =
        ServerlessAwsLambdaPrepareRollbackDataResult.builder();
    String cloudFormationStackName = serverlessAwsCommandTaskHelper.getCloudFormationStackName(serverlessManifestSchema,
        serverlessClient, serverlessDelegateTaskParams, serverlessAwsLambdaInfraConfig, timeoutInMillis,
        serverlessManifestConfig, environmentVariables, executionLogCallback);
    if (!serverlessAwsCommandTaskHelper.cloudFormationStackExists(
            cloudFormationStackName, serverlessAwsLambdaInfraConfig)) {
      executionLogCallback.saveExecutionLog(format("Skipping as there are no previous Deployments..%n"), LogLevel.INFO);
      serverlessAwsLambdaPrepareRollbackDataResultBuilder.previousVersionTimeStamp(null);
      serverlessAwsLambdaPrepareRollbackDataResultBuilder.isFirstDeployment(true);
      serverlessPrepareRollbackDataResponseBuilder.serverlessPrepareRollbackDataResult(
          serverlessAwsLambdaPrepareRollbackDataResultBuilder.build());
      serverlessPrepareRollbackDataResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
      executionLogCallback.saveExecutionLog(
          color(format("%n Done..."), LogColor.White, LogWeight.Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      return serverlessPrepareRollbackDataResponseBuilder.build();
    }
    ServerlessCliResponse response =
        serverlessAwsCommandTaskHelper.deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, timeoutInMillis, serverlessManifestConfig, environmentVariables);
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      executionLogCallback.saveExecutionLog(
          color(format("%nDeploy List command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      List<String> timeStamps = serverlessAwsCommandTaskHelper.getDeployListTimeStamps(response.getOutput());
      Optional<String> previousVersionTimeStamp = serverlessAwsCommandTaskHelper.getLastDeployedTimestamp(
          timeStamps, serverlessPrepareRollbackDataRequest, cloudFormationStackName);
      previousDeployTimeStamp = previousVersionTimeStamp.orElse(null);
      if (previousVersionTimeStamp.isPresent()) {
        serverlessAwsLambdaPrepareRollbackDataResultBuilder.previousVersionTimeStamp(previousDeployTimeStamp);
        executionLogCallback.saveExecutionLog(
            color(format("Active successful deployment version timestamp:%s %n", previousVersionTimeStamp.get()),
                LogColor.White, LogWeight.Bold),
            INFO);
      } else {
        serverlessAwsLambdaPrepareRollbackDataResultBuilder.previousVersionTimeStamp(null);
        executionLogCallback.saveExecutionLog(
            color(format("Found no active successful deployment version %n"), LogColor.White, LogWeight.Bold), INFO);
      }
      serverlessPrepareRollbackDataResponseBuilder.serverlessPrepareRollbackDataResult(
          serverlessAwsLambdaPrepareRollbackDataResultBuilder.build());
      serverlessPrepareRollbackDataResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
      executionLogCallback.saveExecutionLog(
          color(format("%n Done..."), LogColor.White, LogWeight.Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      executionLogCallback.saveExecutionLog(
          color(serverlessAwsCommandTaskHelper.serverlessCommandFailureMessage("Deploy List Command", response),
              LogColor.Red, LogWeight.Bold),
          ERROR);
      serverlessPrepareRollbackDataResponseBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE);
      serverlessAwsCommandTaskHelper.handleCommandExecutionFailure(response, serverlessClient.deployList());
    }
    return serverlessPrepareRollbackDataResponseBuilder.build();
  }
}
