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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult.ServerlessAwsLambdaRollbackResultBuilder;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse.ServerlessRollbackResponseBuilder;
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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class ServerlessAwsLambdaRollbackCommandTaskHandler extends ServerlessCommandTaskHandler {
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Inject private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Inject private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;

  private ServerlessClient serverlessClient;
  private ServerlessAwsLambdaManifestConfig serverlessManifestConfig;
  private ServerlessAwsLambdaManifestSchema serverlessManifestSchema;
  private ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig;
  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig;
  private long timeoutInMillis;

  @Override
  protected ServerlessCommandResponse executeTaskInternal(ServerlessCommandRequest serverlessCommandRequest,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(serverlessCommandRequest instanceof ServerlessRollbackRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("serverlessCommandRequest", "Must be instance of ServerlessRollbackRequest"));
    }
    ServerlessRollbackRequest serverlessRollbackRequest = (ServerlessRollbackRequest) serverlessCommandRequest;
    if (!(serverlessRollbackRequest.getServerlessInfraConfig() instanceof ServerlessAwsLambdaInfraConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessInfraConfig", "Must be instance of ServerlessAwsLambdaInfraConfig"));
    }
    if (!(serverlessRollbackRequest.getServerlessManifestConfig() instanceof ServerlessAwsLambdaManifestConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessManifestConfig", "Must be instance of ServerlessAwsLambdaManifestConfig"));
    }
    if (!(serverlessRollbackRequest.getServerlessRollbackConfig() instanceof ServerlessAwsLambdaRollbackConfig)) {
      throw new InvalidArgumentsException(
          Pair.of("ServerlessRollbackConfig", "Must be instance of ServerlessAwsLambdaRollbackConfig"));
    }
    timeoutInMillis = serverlessRollbackRequest.getTimeoutIntervalInMin() * 60000;
    serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessRollbackRequest.getServerlessInfraConfig();

    LogCallback setupDirectoryLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true, commandUnitsProgress);
    try {
      setupDirectory(serverlessRollbackRequest, setupDirectoryLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      setupDirectoryLogCallback.saveExecutionLog(
          color(format("%n setup directory failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback configureCredsLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true, commandUnitsProgress);
    try {
      configureCredential(serverlessRollbackRequest, configureCredsLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      configureCredsLogCallback.saveExecutionLog(
          color(format("%n configure credential failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback pluginLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    try {
      serverlessAwsCommandTaskHelper.installPlugins(serverlessManifestSchema, serverlessDelegateTaskParams,
          pluginLogCallback, serverlessClient, timeoutInMillis, serverlessManifestConfig);
      pluginLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    } catch (Exception ex) {
      pluginLogCallback.saveExecutionLog(color(format("%n installing plugin failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }

    LogCallback rollbackLogCallback = serverlessTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    try {
      return rollback(serverlessRollbackRequest, rollbackLogCallback, serverlessDelegateTaskParams);
    } catch (Exception ex) {
      rollbackLogCallback.saveExecutionLog(color(format("%n Rollback failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw ex;
    }
  }

  private void setupDirectory(ServerlessRollbackRequest serverlessRollbackRequest, LogCallback executionLogCallback,
      ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("setting up serverless directory..%n%n"));
    serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessRollbackRequest.getServerlessManifestConfig();
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
        serverlessRollbackRequest.getAccountId(), executionLogCallback, serverlessDelegateTaskParams);
    serverlessManifestSchema = serverlessAwsCommandTaskHelper.parseServerlessManifest(
        executionLogCallback, serverlessRollbackRequest.getManifestContent());
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams, serverlessManifestConfig,
        serverlessRollbackRequest.getManifestContent(), serverlessManifestSchema);
    executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  private void configureCredential(ServerlessRollbackRequest serverlessRollbackRequest,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    serverlessAwsLambdaConfig = (ServerlessAwsLambdaConfig) serverlessInfraConfigHelper.createServerlessConfig(
        serverlessRollbackRequest.getServerlessInfraConfig());
    serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    ServerlessCliResponse response = serverlessAwsCommandTaskHelper.configCredential(serverlessClient,
        serverlessAwsLambdaConfig, serverlessDelegateTaskParams, executionLogCallback, true, timeoutInMillis);

    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      executionLogCallback.saveExecutionLog(
          color(format("%nConfig Credential command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
      executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    } else {
      executionLogCallback.saveExecutionLog(
          color(format("%nConfig Credential command failed..%n"), LogColor.Red, LogWeight.Bold), ERROR,
          CommandExecutionStatus.FAILURE);
      serverlessAwsCommandTaskHelper.handleCommandExecutionFailure(response, serverlessClient.configCredential());
    }
  }

  private ServerlessRollbackResponse rollback(ServerlessRollbackRequest serverlessRollbackRequest,
      LogCallback executionLogCallback, ServerlessDelegateTaskParams serverlessDelegateTaskParams) throws Exception {
    executionLogCallback.saveExecutionLog(format("Rollback..%n%n"));
    ServerlessCliResponse response;
    ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig =
        (ServerlessAwsLambdaRollbackConfig) serverlessRollbackRequest.getServerlessRollbackConfig();
    ServerlessAwsLambdaRollbackResultBuilder serverlessAwsLambdaRollbackResultBuilder =
        ServerlessAwsLambdaRollbackResult.builder();
    serverlessAwsLambdaRollbackResultBuilder.service(serverlessManifestSchema.getService());
    serverlessAwsLambdaRollbackResultBuilder.region(serverlessAwsLambdaInfraConfig.getRegion());
    serverlessAwsLambdaRollbackResultBuilder.stage(serverlessAwsLambdaInfraConfig.getStage());
    ServerlessRollbackResponseBuilder serverlessRollbackResponseBuilder = ServerlessRollbackResponse.builder();

    if (serverlessAwsLambdaRollbackConfig.isFirstDeployment()) {
      if (!serverlessAwsCommandTaskHelper.cloudFormationStackExists(
              executionLogCallback, serverlessRollbackRequest, serverlessRollbackRequest.getManifestContent())) {
        executionLogCallback.saveExecutionLog(
            format("Nothing to Remove..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
        serverlessRollbackResponseBuilder.serverlessRollbackResult(serverlessAwsLambdaRollbackResultBuilder.build());
        return serverlessRollbackResponseBuilder.build();
      } else {
        response = serverlessAwsCommandTaskHelper.remove(serverlessClient, serverlessDelegateTaskParams,
            executionLogCallback, timeoutInMillis, serverlessManifestConfig, serverlessAwsLambdaInfraConfig);
      }
    } else {
      if (EmptyPredicate.isEmpty(serverlessAwsLambdaRollbackConfig.getPreviousVersionTimeStamp())) {
        executionLogCallback.saveExecutionLog(
            format("Nothing to Rollback..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
        serverlessRollbackResponseBuilder.serverlessRollbackResult(serverlessAwsLambdaRollbackResultBuilder.build());
        return serverlessRollbackResponseBuilder.build();
      } else {
        response = serverlessAwsCommandTaskHelper.rollback(serverlessClient, serverlessDelegateTaskParams,
            executionLogCallback, serverlessAwsLambdaRollbackConfig, timeoutInMillis, serverlessManifestConfig,
            serverlessAwsLambdaInfraConfig);
      }
    }

    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      if (EmptyPredicate.isNotEmpty(serverlessAwsLambdaRollbackConfig.getPreviousVersionTimeStamp())) {
        serverlessAwsLambdaRollbackResultBuilder.rollbackTimeStamp(
            serverlessAwsLambdaRollbackConfig.getPreviousVersionTimeStamp());
      }
      executionLogCallback.saveExecutionLog(
          color(format("%nRollback completed successfully..%n"), LogColor.White, LogWeight.Bold), LogLevel.INFO,
          CommandExecutionStatus.SUCCESS);
      executionLogCallback.saveExecutionLog(format("Done..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      serverlessRollbackResponseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } else {
      serverlessAwsCommandTaskHelper.handleCommandExecutionFailure(response, serverlessClient.rollback());
    }
    serverlessRollbackResponseBuilder.serverlessRollbackResult(serverlessAwsLambdaRollbackResultBuilder.build());
    return serverlessRollbackResponseBuilder.build();
  }
}
