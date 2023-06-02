/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult;
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
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandUnitConstants;
import io.harness.serverless.model.ServerlessAwsLambdaConfig;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ServerlessAwsLambdaRollbackCommandTaskHandlerTest extends CategoryTest {
  @Mock private ServerlessTaskHelperBase serverlessTaskHelperBase;
  @Mock private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock private ServerlessAwsCommandTaskHelper serverlessAwsCommandTaskHelper;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback initLogCallback;
  @Mock private LogCallback rollbackLogCallback;
  @Mock private LogCallback setupDirectoryLogCallback;
  @Mock private LogCallback configureCredsLogCallback;
  @Mock private LogCallback pluginLogCallback;

  @InjectMocks private ServerlessAwsLambdaRollbackCommandTaskHandler serverlessAwsLambdaRollbackCommandTaskHandler;

  private Integer timeout = 10;
  private String accountId = "account";
  private String output = "output";
  private String previousVersionTimeStamp = "123";
  private String service = "serv";
  private String region = "regi";
  private String stage = "stag";
  private String manifestContent = "manifest";
  private String workingDir = "/asdf/";
  private ServerlessInfraConfig serverlessInfraConfig =
      ServerlessAwsLambdaInfraConfig.builder().region(region).stage(stage).build();
  private ServerlessManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder().build();
  private ServerlessDelegateTaskParams serverlessDelegateTaskParams =
      ServerlessDelegateTaskParams.builder().serverlessClientPath("/qwer").workingDirectory(workingDir).build();
  private CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private ServerlessAwsLambdaConfig serverlessAwsLambdaConfig = ServerlessAwsLambdaConfig.builder().build();
  private ServerlessAwsLambdaManifestSchema serverlessAwsLambdaManifestSchema =
      ServerlessAwsLambdaManifestSchema.builder().service(service).build();
  private ServerlessCliResponse intiServerlessCliResponse =
      ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).output(output).build();
  private ServerlessCliResponse rollbackServerlessCliResponse =
      ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  private List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctionsList = new ArrayList<>();
  ServerlessRollbackConfig serverlessRollbackConfig =
      ServerlessAwsLambdaRollbackConfig.builder().isFirstDeployment(false).build();
  private ServerlessCommandRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                                  .timeoutIntervalInMin(timeout)
                                                                  .serverlessInfraConfig(serverlessInfraConfig)
                                                                  .serverlessManifestConfig(serverlessManifestConfig)
                                                                  .serverlessRollbackConfig(serverlessRollbackConfig)
                                                                  .manifestContent(manifestContent)
                                                                  .accountId(accountId)
                                                                  .build();
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentIsFalseAndNoPreviousTimestamp() throws Exception {
    ServerlessRollbackConfig serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder().isFirstDeployment(false).build();
    ServerlessCommandRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .serverlessRollbackConfig(serverlessRollbackConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();
    doReturn(initLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(
            setupDirectoryLogCallback, ((ServerlessRollbackRequest) serverlessCommandRequest).getManifestContent());

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder().service(service).region(region).stage(stage).build();

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentIsFalseAndPreviousTimestampExists() throws Exception {
    ServerlessAwsLambdaRollbackConfig serverlessRollbackConfig = ServerlessAwsLambdaRollbackConfig.builder()
                                                                     .isFirstDeployment(false)
                                                                     .previousVersionTimeStamp(previousVersionTimeStamp)
                                                                     .build();
    ServerlessRollbackRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                             .timeoutIntervalInMin(timeout)
                                                             .serverlessInfraConfig(serverlessInfraConfig)
                                                             .serverlessManifestConfig(serverlessManifestConfig)
                                                             .serverlessRollbackConfig(serverlessRollbackConfig)
                                                             .manifestContent(manifestContent)
                                                             .accountId(accountId)
                                                             .build();
    doReturn(initLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(setupDirectoryLogCallback, serverlessCommandRequest.getManifestContent());

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback, serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder()
            .service(service)
            .region(region)
            .stage(stage)
            .rollbackTimeStamp(previousVersionTimeStamp)
            .build();

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback, serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());
    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentAndCloudFormationTemplateDoesNotExist() throws Exception {
    ServerlessAwsLambdaRollbackConfig serverlessRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder().isFirstDeployment(true).build();
    ServerlessRollbackRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                             .timeoutIntervalInMin(timeout)
                                                             .serverlessInfraConfig(serverlessInfraConfig)
                                                             .serverlessManifestConfig(serverlessManifestConfig)
                                                             .serverlessRollbackConfig(serverlessRollbackConfig)
                                                             .manifestContent(manifestContent)
                                                             .accountId(accountId)
                                                             .build();
    doReturn(initLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(setupDirectoryLogCallback, serverlessCommandRequest.getManifestContent());

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(intiServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            configureCredsLogCallback, true, timeout * 60000, null);

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getLastDeployedTimestamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback, serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, null);

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    doReturn(false)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists("abc", (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    doReturn("abc")
        .when(serverlessAwsCommandTaskHelper)
        .getCloudFormationStackName(any(), any(), any(), any(), anyLong(), any(), any(), any());

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder().service(service).region(region).stage(stage).build();

    doReturn(MANUAL_CREDENTIALS.name())
        .when(serverlessInfraConfigHelper)
        .getServerlessAwsLambdaCredentialType((ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .cloudFormationStackExists("abc", (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);
    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentAndCloudFormationStackExists() throws Exception {
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

    doReturn(initLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(
            setupDirectoryLogCallback, ((ServerlessRollbackRequest) serverlessCommandRequest).getManifestContent());

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(intiServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            configureCredsLogCallback, true, timeout * 60000, new HashMap<>());

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getLastDeployedTimestamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .remove(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists("abc", (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder().service(service).region(region).stage(stage).build();

    doReturn(MANUAL_CREDENTIALS.name())
        .when(serverlessInfraConfigHelper)
        .getServerlessAwsLambdaCredentialType((ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    doReturn("abc")
        .when(serverlessAwsCommandTaskHelper)
        .getCloudFormationStackName(any(), any(), any(), any(), anyLong(), any(), any(), any());

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .cloudFormationStackExists("abc", (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);
    verify(serverlessAwsCommandTaskHelper, times(1))
        .remove(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());
    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = IOException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void setupDirectoryExceptionTest() throws Exception {
    ServerlessRollbackRequest serverlessRollbackRequest = (ServerlessRollbackRequest) serverlessCommandRequest;
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig =
        (ServerlessAwsLambdaManifestConfig) serverlessRollbackRequest.getServerlessManifestConfig();
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doThrow(IOException.class)
        .when(serverlessTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig, serverlessRollbackRequest.getAccountId(),
            setupDirectoryLogCallback, serverlessDelegateTaskParams);
    serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
        serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void exceptionTest() throws Exception {
    ServerlessRollbackRequest serverlessRollbackRequest = (ServerlessRollbackRequest) serverlessCommandRequest;
    ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig =
        (ServerlessAwsLambdaRollbackConfig) serverlessRollbackRequest.getServerlessRollbackConfig();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder().build();

    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(intiServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            configureCredsLogCallback, true, timeout * 60000, new HashMap<>());

    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = ServerlessAwsLambdaInfraConfig.builder().build();

    doThrow(IOException.class)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            serverlessAwsLambdaRollbackConfig, timeout * 60000, serverlessManifestConfig,
            serverlessAwsLambdaInfraConfig, new HashMap<>());

    serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
        serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalCommandExecutionStatusFailureTest() throws Exception {
    ServerlessCliResponse intiServerlessCliResponse =
        ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).output(output).build();
    ServerlessCliResponse rollbackServerlessCliResponse =
        ServerlessCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    doReturn(initLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.init.toString(), true, commandUnitsProgress);
    doReturn(setupDirectoryLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.setupDirectory.toString(), true,
            commandUnitsProgress);
    doReturn(configureCredsLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, ServerlessCommandUnitConstants.configureCred.toString(), true,
            commandUnitsProgress);
    doReturn(pluginLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.plugin.toString(), true, commandUnitsProgress);
    doReturn(rollbackLogCallback)
        .when(serverlessTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, ServerlessCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(
            setupDirectoryLogCallback, ((ServerlessRollbackRequest) serverlessCommandRequest).getManifestContent());

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(intiServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            configureCredsLogCallback, true, timeout * 60000, new HashMap<>());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (ServerlessAwsLambdaRollbackConfig) serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
        serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalWithIrsaRoleAwsConnector() throws Exception {
    ServerlessRollbackConfig serverlessRollbackConfig = ServerlessAwsLambdaRollbackConfig.builder()
                                                            .isFirstDeployment(false)
                                                            .previousVersionTimeStamp(previousVersionTimeStamp)
                                                            .build();
    ServerlessCommandRequest serverlessCommandRequest = ServerlessRollbackRequest.builder()
                                                            .timeoutIntervalInMin(timeout)
                                                            .serverlessInfraConfig(serverlessInfraConfig)
                                                            .serverlessManifestConfig(serverlessManifestConfig)
                                                            .serverlessRollbackConfig(serverlessRollbackConfig)
                                                            .manifestContent(manifestContent)
                                                            .accountId(accountId)
                                                            .build();

    doReturn(initLogCallback).when(serverlessTaskHelperBase).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn(serverlessAwsLambdaConfig).when(serverlessInfraConfigHelper).createServerlessConfig(serverlessInfraConfig);
    doReturn(serverlessAwsLambdaManifestSchema)
        .when(serverlessAwsCommandTaskHelper)
        .parseServerlessManifest(
            initLogCallback, ((ServerlessRollbackRequest) serverlessCommandRequest).getManifestContent());

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());
    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getLastDeployedTimestamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, initLogCallback,
            (ServerlessAwsLambdaRollbackConfig) serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig, new HashMap<>());

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder()
            .service(service)
            .region(region)
            .stage(stage)
            .rollbackTimeStamp(previousVersionTimeStamp)
            .build();

    doReturn(IRSA.name())
        .when(serverlessInfraConfigHelper)
        .getServerlessAwsLambdaCredentialType((ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);
    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}