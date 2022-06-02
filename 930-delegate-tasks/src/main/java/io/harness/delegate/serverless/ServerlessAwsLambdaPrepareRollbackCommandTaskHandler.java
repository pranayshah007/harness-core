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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult.ServerlessAwsLambdaPrepareRollbackDataResultBuilder;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeployConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
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

import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ServerlessAwsLambdaPrepareRollbackCommandTaskHandler extends ServerlessCommandTaskHandler {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;

  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private long timeoutInMillis;
  private String previousDeployTimeStamp;

  @Override
  protected ServerlessCommandResponse executeTaskInternal(ServerlessCommandRequest serverlessCommandRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(serverlessCommandRequest instanceof ServerlessPrepareRollbackDataRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessPrepareRollbackDataRequest"));
    }
    ServerlessDeployRequest serverlessDeployRequest = (ServerlessDeployRequest) serverlessCommandRequest;
    if (!(serverlessDeployRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessDeployRequest.getServerlessManifestConfig() instanceof ServerlessAwsLambdaManifestConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessManifestConfig", "Must be instance of ServerlessAwsLambdaManifestConfig"));
    }

    if (!(serverlessDeployRequest.getServerlessDeployConfig() instanceof ServerlessAwsLambdaDeployConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessDeployConfig", "Must be instance of ServerlessAwsLambdaDeployConfig"));
    }

    timeoutInMillis = serverlessDeployRequest.getTimeoutIntervalInMin() * 60000;
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessDeployRequest.getServerlessInfraConfig();
    LogCallback executionLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true, commandUnitsProgress);
    try {
      setupDirectory(serverlessDeployRequest, executionLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(color(format("%n setup directory failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    try {
      configureCredential(serverlessDeployRequest, executionLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
          color(format("%n configure credential failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }

    try {
      serverlessAwsCommandTaskHelper.installPlugins(serverlessManifestSchema, serverlessDelegateTaskParams,
          executionLogCallback, serverlessClient, timeoutInMillis, serverlessManifestConfig);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(color(format("%n installing plugin failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    try {
      return prepareRollbackData(serverlessDeployRequest, executionLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog(
          color(format("%n prepare rollback data failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  private void setupDirectory(ServerlessDeployRequest serverlessDeployRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("setting up serverless directory..%n%n"));
    serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessDeployRequest.getServerlessManifestConfig();
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
        serverlessDeployRequest.getAccountId(), executionLogCallback, serverlessDelegateTaskParams);
    serverlessManifestSchema = serverlessAwsCommandTaskHelper.parseServerlessManifest(
        executionLogCallback, serverlessDeployRequest.getManifestContent());
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams, serverlessManifestConfig,
        serverlessDeployRequest.getManifestContent(), serverlessManifestSchema);
  }

  private void configureCredential(ServerlessDeployRequest serverlessDeployRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
        serverlessDeployRequest.getServerlessInfraConfig());
    serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    ServerlessCliResponse response = serverlessAwsCommandTaskHelper.configCredential(serverlessClient,
        serverlessAwsLambdaConfig, serverlessDelegateTaskParams, executionLogCallback, true, timeoutInMillis);

    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      executionLogCallback.saveExecutionLog(
          color(format("%nConfig Credential command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
    } else {
      executionLogCallback.saveExecutionLog(
          color(format("%nConfig Credential command failed..%n"), LogColor.Red, LogWeight.Bold), ERROR,
          CommandExecutionStatus.FAILURE);
      serverlessAwsCommandTaskHelper.handleCommandExecutionFailure(response, serverlessClient.configCredential());
    }
  }

  private ServerlessPrepareRollbackDataResponse prepareRollbackData(ServerlessDeployRequest serverlessDeployRequest,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("Preparing Rollback Data..%n%n"));
    ServerlessCliResponse response =
        serverlessAwsCommandTaskHelper.deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, timeoutInMillis, serverlessManifestConfig);
    ServerlessPrepareRollbackDataResponseBuilder serverlessPrepareRollbackDataResponseBuilder =
        ServerlessPrepareRollbackDataResponse.builder();
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      executionLogCallback.saveExecutionLog(
          color(format("%nDeploy List command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      List<String> timeStamps = serverlessAwsCommandTaskHelper.getDeployListTimeStamps(response.getOutput());
      Optional<String> previousVersionTimeStamp = serverlessAwsCommandTaskHelper.getPreviousVersionTimeStamp(
          timeStamps, executionLogCallback, serverlessDeployRequest);
      previousDeployTimeStamp = previousVersionTimeStamp.orElse(null);
      ServerlessAwsLambdaPrepareRollbackDataResultBuilder serverlessAwsLambdaPrepareRollbackDataResultBuilder =
          ServerlessAwsLambdaPrepareRollbackDataResult.builder();
      if (previousVersionTimeStamp.isPresent()) {
        serverlessAwsLambdaPrepareRollbackDataResultBuilder.previousVersionTimeStamp(previousDeployTimeStamp);
        executionLogCallback.saveExecutionLog(
            color(format("Active successful deployment version timestamp:%s %n", previousVersionTimeStamp.get()),
                LogColor.White, LogWeight.Bold),
            INFO);
      } else {
        serverlessAwsLambdaPrepareRollbackDataResultBuilder.previousVersionTimeStamp(null);
        executionLogCallback.saveExecutionLog(
            color(format("Found no active successful deployment version %n", previousVersionTimeStamp), LogColor.White,
                LogWeight.Bold),
            INFO);
      }
      serverlessPrepareRollbackDataResponseBuilder.serverlessAwsLambdaPrepareRollbackDataResult(
          serverlessAwsLambdaPrepareRollbackDataResultBuilder.build());
      serverlessPrepareRollbackDataResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } else {
      executionLogCallback.saveExecutionLog(
          color(format("%nDeploy List command failed..%n"), LogColor.Red, LogWeight.Bold), ERROR);
      serverlessPrepareRollbackDataResponseBuilder.commandExecutionStatus(CommandExecutionStatus.FAILURE);
    }
    return serverlessPrepareRollbackDataResponseBuilder.build();
  }
}
