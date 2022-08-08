/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.serverless.ServerlessAwsCommandTaskHelper;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfigHelper;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.ServerlessRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessPrepareRollbackDataRequest;
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.IOException;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ServerlessAwsLambdaPrepareRollbackCommandTaskHandlerTest {
  @Mock private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Mock private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback executionLogCallback;

  @InjectMocks
  private ServerlessAwsLambdaPrepareRollbackCommandTaskHandler serverlessAwsLambdaPrepareRollbackCommandTaskHandler;

  private Integer timeout = 10;
  private String accountId = "accountId";
  private String output = "output";
  private String region = "us-east-2";
  private String stage = "stage";
  private String manifestContent = "manifestContent";
  private String workingDir = "dir";

  private ServerlessInfraConfig serverlessInfraConfig =
      ServerlessAwsLambdaInfraConfig.builder().region(region).stage(stage).build();
  private ServerlessManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder().build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams =
      ServerlessDelegateTaskParams.builder().serverlessClientPath("/qwer").workingDirectory(workingDir).build();
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder().build();
  private ServerlessCliResponse response =
      ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output(output).build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalPreviousVersionTimeStampCloudFormationStackExistsTest() throws Exception {
    ServerlessCommandRequest serverlessCommandRequest = ServerlessPrepareRollbackDataRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(executionLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true,
            commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            executionLogCallback, true, (long) timeout * 60000, new HashMap<>());

    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        (ServerlessPrepareRollbackDataRequest) serverlessCommandRequest;
    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(executionLogCallback, serverlessPrepareRollbackDataRequest,
            serverlessPrepareRollbackDataRequest.getManifestContent());

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessPrepareRollbackDataRequest.getServerlessManifestConfig();
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, (long) timeout * 60000, serverlessAwsLambdaManifestConfig, new HashMap<>());

    ServerlessCommandResponse serverlessCommandResponse =
        serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(executionLogCallback)
        .saveExecutionLog(
            color(format("%nDeploy List command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalCloudFormationStackNotExistsTest() throws Exception {
    ServerlessCommandRequest serverlessCommandRequest = ServerlessPrepareRollbackDataRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(executionLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true,
            commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            executionLogCallback, true, (long) timeout * 60000, new HashMap<>());

    ServerlessCommandResponse serverlessCommandResponse =
        serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(executionLogCallback)
        .saveExecutionLog(
            color(format("%n Done..."), LogColor.White, LogWeight.Bold), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    verify(executionLogCallback)
        .saveExecutionLog(
            format("Skipping as there are no previous Deployments..%n"), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNotInstanceOfServerlessPrepareRollbackDataRequestTest() throws Exception {
    ServerlessRollbackConfig serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder().isFirstDeployment(true).build();
    ServerlessCommandRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .serverlessRollbackConfig(serverlessRollbackConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
        serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNoPreviousVersionTimeStampCloudFormationStackExistsTest() throws Exception {
    ServerlessCommandRequest serverlessCommandRequest = ServerlessPrepareRollbackDataRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(executionLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true,
            commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            executionLogCallback, true, (long) timeout * 60000, new HashMap<>());

    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        (ServerlessPrepareRollbackDataRequest) serverlessCommandRequest;
    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(executionLogCallback, serverlessPrepareRollbackDataRequest,
            serverlessPrepareRollbackDataRequest.getManifestContent());

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessPrepareRollbackDataRequest.getServerlessManifestConfig();
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, (long) timeout * 60000, serverlessAwsLambdaManifestConfig, new HashMap<>());

    ServerlessCommandResponse serverlessCommandResponse =
        serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(executionLogCallback)
        .saveExecutionLog(
            color(format("%nDeploy List command executed successfully..%n"), LogColor.White, LogWeight.Bold), INFO);
    verify(executionLogCallback)
        .saveExecutionLog(
            color(format("Found no active successful deployment version %n"), LogColor.White, LogWeight.Bold), INFO);
  }

  @Test(expected = IOException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalPrepareRollbackDataExceptionTest() throws Exception {
    ServerlessRollbackConfig serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder().isFirstDeployment(true).build();
    ServerlessCommandRequest serverlessCommandRequest = ServerlessPrepareRollbackDataRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(executionLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true,
            commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            executionLogCallback, true, (long) timeout * 60000, new HashMap<>());

    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        (ServerlessPrepareRollbackDataRequest) serverlessCommandRequest;
    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(executionLogCallback, serverlessPrepareRollbackDataRequest,
            serverlessPrepareRollbackDataRequest.getManifestContent());

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessPrepareRollbackDataRequest.getServerlessManifestConfig();
    doThrow(IOException.class)
        .when(serverlessAwsCommandTaskHelper)
        .deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, (long) timeout * 60000, serverlessAwsLambdaManifestConfig, new HashMap<>());

    serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
        serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalCommandExecutionStatusFailureTest() throws Exception {
    ServerlessCliResponse response =
        ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).output(output).build();

    ServerlessCommandRequest serverlessCommandRequest = ServerlessPrepareRollbackDataRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(executionLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollbackData.toString(), true,
            commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            executionLogCallback, true, (long) timeout * 60000, new HashMap<>());

    ServerlessPrepareRollbackDataRequest serverlessPrepareRollbackDataRequest =
        (ServerlessPrepareRollbackDataRequest) serverlessCommandRequest;
    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(executionLogCallback, serverlessPrepareRollbackDataRequest,
            serverlessPrepareRollbackDataRequest.getManifestContent());

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) serverlessPrepareRollbackDataRequest.getServerlessInfraConfig();
    ServerlessAwsLambdaManifestConfig serverlessAwsLambdaManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessPrepareRollbackDataRequest.getServerlessManifestConfig();
    doReturn(response)
        .when(serverlessAwsCommandTaskHelper)
        .deployList(serverlessClient, serverlessDelegateTaskParams, executionLogCallback,
            serverlessAwsLambdaInfraConfig, (long) timeout * 60000, serverlessAwsLambdaManifestConfig, new HashMap<>());

    ServerlessCommandResponse serverlessCommandResponse =
        serverlessAwsLambdaPrepareRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(executionLogCallback)
        .saveExecutionLog(color(format("%nDeploy List command failed..%n"), LogColor.Red, LogWeight.Bold), ERROR);
  }
}