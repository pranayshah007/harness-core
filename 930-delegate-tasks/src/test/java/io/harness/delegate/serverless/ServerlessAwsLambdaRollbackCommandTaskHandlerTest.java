/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
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

import java.util.ArrayList;
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

    ServerlessClient serverlessClient = ServerlessClient.client(serverlessDelegateTaskParams.getServerlessClientPath());

    doReturn(intiServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .configCredential(serverlessClient, serverlessAwsLambdaConfig, serverlessDelegateTaskParams,
            configureCredsLogCallback, true, timeout * 60000);

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getPreviousVersionTimeStamp(any(), any(), any());

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

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
            configureCredsLogCallback, true, timeout * 60000);

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getPreviousVersionTimeStamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (ServerlessAwsLambdaRollbackConfig) serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

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

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (ServerlessAwsLambdaRollbackConfig) serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);
    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenFirstDeploymentAndCloudFormationTemplateDoesNotExist() throws Exception {
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
            configureCredsLogCallback, true, timeout * 60000);

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getPreviousVersionTimeStamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .rollback(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (ServerlessAwsLambdaRollbackConfig) serverlessRollbackConfig,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    doReturn(false)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(rollbackLogCallback, serverlessCommandRequest, manifestContent);

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder().service(service).region(region).stage(stage).build();

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .cloudFormationStackExists(rollbackLogCallback, serverlessCommandRequest, manifestContent);
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
            configureCredsLogCallback, true, timeout * 60000);

    doReturn(Optional.of(previousVersionTimeStamp))
        .when(serverlessAwsCommandTaskHelper)
        .getPreviousVersionTimeStamp(any(), any(), any());

    doReturn(rollbackServerlessCliResponse)
        .when(serverlessAwsCommandTaskHelper)
        .remove(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);

    doReturn(serverlessAwsLambdaFunctionsList)
        .when(serverlessAwsCommandTaskHelper)
        .fetchFunctionOutputFromCloudFormationTemplate(any());

    doReturn(true)
        .when(serverlessAwsCommandTaskHelper)
        .cloudFormationStackExists(rollbackLogCallback, serverlessCommandRequest, manifestContent);

    ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
        ServerlessAwsLambdaRollbackResult.builder().service(service).region(region).stage(stage).build();

    ServerlessRollbackResponse serverlessRollbackResponse =
        (ServerlessRollbackResponse) serverlessAwsLambdaRollbackCommandTaskHandler.executeTaskInternal(
            serverlessCommandRequest, serverlessDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);

    verify(serverlessAwsCommandTaskHelper, times(1))
        .cloudFormationStackExists(rollbackLogCallback, serverlessCommandRequest, manifestContent);
    verify(serverlessAwsCommandTaskHelper, times(1))
        .remove(serverlessClient, serverlessDelegateTaskParams, rollbackLogCallback,
            (long) (serverlessCommandRequest.getTimeoutIntervalInMin() * 60000),
            (ServerlessAwsLambdaManifestConfig) serverlessManifestConfig,
            (ServerlessAwsLambdaInfraConfig) serverlessInfraConfig);
    assertThat(serverlessRollbackResponse.getServerlessRollbackResult()).isEqualTo(serverlessAwsLambdaRollbackResult);
    assertThat(serverlessRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }
}
