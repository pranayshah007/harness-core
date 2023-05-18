/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.exception.ServerlessNGException;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaDeployStepTest extends AbstractServerlessStepExecutorTestBase {
  @Mock private InstanceInfoService instanceInfoService;

  @InjectMocks private ServerlessAwsLambdaDeployStep serverlessAwsLambdaDeployStep;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();

    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();

    ServerlessDeployRequest request = executeTask(stepElementParameters, ServerlessDeployRequest.class);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.getServerlessInfraConfig()).isEqualTo(serverlessInfraConfig);
    assertThat(request.getServerlessManifestConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getServerlessCommandType()).isEqualTo(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_DEPLOY);
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.getCommandName()).isEqualTo(SERVERLESS_AWS_LAMBDA_DEPLOY_COMMAND_NAME);
    assertThat(request.getServerlessDeployConfig()).isEqualTo(serverlessDeployConfig);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testOutcomesInResponseSuccessTest() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    ServerlessDeployResponse serverlessDeployResponse =
        ServerlessDeployResponse.builder()
            .serverlessDeployResult(ServerlessAwsLambdaDeployResult.builder()
                                        .service("aws")
                                        .region("us-east-01")
                                        .stage("stg")
                                        .functions(Arrays.asList())
                                        .build())
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .commandExecutionStatus(SUCCESS)
            .build();

    StepOutcome stepOutcome = StepOutcome.builder().name("a").build();
    List<ServerInstanceInfo> serverInstanceInfoList =
        Arrays.asList(ServerlessAwsLambdaServerInstanceInfo.builder().build());

    ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
        ServerlessAwsLambdaInfrastructureOutcome.builder().infrastructureKey("infrastructureKey").build();
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder().infrastructure(serverlessAwsLambdaInfrastructureOutcome).build();

    doReturn(serverInstanceInfoList)
        .when(serverlessStepHelper)
        .getFunctionInstanceInfo(serverlessDeployResponse, serverlessAwsLambdaStepHelper, "infrastructureKey");
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);

    StepResponse response = serverlessAwsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, serverlessExecutionPassThroughData, () -> serverlessDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getUnitProgressList()).isEqualTo(Arrays.asList());
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(outcome).isEqualTo(stepOutcome);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testOutcomesInResponseWhenGitFetchFailureTest() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    ServerlessDeployResponse serverlessDeployResponse =
        ServerlessDeployResponse.builder()
            .serverlessDeployResult(
                ServerlessAwsLambdaDeployResult.builder().service("aws").region("us-east-01").stage("stg").build())
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .commandExecutionStatus(SUCCESS)
            .build();

    PassThroughData passThroughData = ServerlessGitFetchFailurePassThroughData.builder().build();

    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();
    doReturn(stepResponse)
        .when(serverlessStepHelper)
        .handleGitTaskFailure((ServerlessGitFetchFailurePassThroughData) passThroughData);

    StepResponse response = serverlessAwsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> serverlessDeployResponse);
    verify(serverlessStepHelper, times(1))
        .handleGitTaskFailure((ServerlessGitFetchFailurePassThroughData) passThroughData);
    assertThat(response).isEqualTo(stepResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testOutcomesInResponseWhenStepExceptionTest() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    ServerlessDeployResponse serverlessDeployResponse =
        ServerlessDeployResponse.builder()
            .serverlessDeployResult(
                ServerlessAwsLambdaDeployResult.builder().service("aws").region("us-east-01").stage("stg").build())
            .unitProgressData(UnitProgressData.builder().unitProgresses(Arrays.asList()).build())
            .commandExecutionStatus(SUCCESS)
            .build();

    PassThroughData passThroughData = ServerlessStepExceptionPassThroughData.builder().build();

    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();
    doReturn(stepResponse)
        .when(serverlessStepHelper)
        .handleStepExceptionFailure((ServerlessStepExceptionPassThroughData) passThroughData);

    StepResponse response = serverlessAwsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> serverlessDeployResponse);
    verify(serverlessStepHelper, times(1))
        .handleStepExceptionFailure((ServerlessStepExceptionPassThroughData) passThroughData);
    assertThat(response).isEqualTo(stepResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testOutcomesInResponseWhenNoServerlessNGExceptionDuringResponseRetrievalTest() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();
    PassThroughData passThroughData = ServerlessExecutionPassThroughData.builder().build();

    Exception e = new Exception();

    doReturn(stepResponse)
        .when(serverlessStepHelper)
        .handleTaskException(ambiance, (ServerlessExecutionPassThroughData) passThroughData, e);
    StepResponse response = serverlessAwsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> { throw e; });
    verify(serverlessStepHelper, times(1))
        .handleTaskException(ambiance, (ServerlessExecutionPassThroughData) passThroughData, e);
    assertThat(response).isEqualTo(stepResponse);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testOutcomesInResponseWhenServerlessNGExceptionDuringResponseRetrievalTest() {
    ServerlessAwsLambdaDeployStepParameters stepParameters = new ServerlessAwsLambdaDeployStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();
    PassThroughData passThroughData = ServerlessExecutionPassThroughData.builder().build();

    Exception e = new ServerlessNGException(new Exception());

    doReturn(stepResponse)
        .when(serverlessStepHelper)
        .handleTaskException(ambiance, (ServerlessExecutionPassThroughData) passThroughData, e);
    StepResponse response = serverlessAwsLambdaDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> { throw e; });
    verify(serverlessStepHelper, times(1))
        .handleTaskException(ambiance, (ServerlessExecutionPassThroughData) passThroughData, e);
    assertThat(response).isEqualTo(stepResponse);
  }

  @Override
  protected ServerlessStepExecutor getServerlessAwsLambdaStepExecutor() {
    return serverlessAwsLambdaDeployStep;
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void validateResources() {
    // no code written
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeServerlessPrepareRollbackTaskTest() {
    ManifestOutcome manifestOutcome =
        ServerlessAwsLambdaManifestOutcome.builder().identifier("adsf").store(storeConfig).build();
    ServerlessAwsLambdaPrepareRollbackDataResult serverlessRollbackDataResult =
        ServerlessAwsLambdaPrepareRollbackDataResult.builder()
            .isFirstDeployment(true)
            .previousVersionTimeStamp("123")
            .build();
    ServerlessPrepareRollbackDataResponse responseData =
        ServerlessPrepareRollbackDataResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .errorMessage("error")
            .serverlessPrepareRollbackDataResult(serverlessRollbackDataResult)
            .unitProgressData(UnitProgressData.builder().build())
            .build();
    ServerlessAwsLambdaDeployStepParameters serverlessSpecParameters =
        ServerlessAwsLambdaDeployStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(serverlessSpecParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    ServerlessStepPassThroughData serverlessStepPassThroughData = ServerlessStepPassThroughData.builder()
                                                                      .serverlessManifestOutcome(manifestOutcome)
                                                                      .infrastructureOutcome(infrastructureOutcome)
                                                                      .build();
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder()
            .infrastructure(serverlessStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(responseData.getUnitProgressData())
            .build();
    TaskChainResponse expectedTaskChainResponse = TaskChainResponse.builder()
                                                      .chainEnd(false)
                                                      .passThroughData(serverlessExecutionPassThroughData)
                                                      .taskRequest(TaskRequest.newBuilder().build())
                                                      .build();

    doReturn(expectedTaskChainResponse)
        .when(serverlessStepHelper)
        .queueServerlessTask(any(), any(), any(), any(), anyBoolean());
    TaskChainResponse taskChainResponse =
        serverlessAwsLambdaDeployStep.executeServerlessPrepareRollbackTask(manifestOutcome, ambiance,
            stepElementParameters, serverlessStepPassThroughData, unitProgressData, serverlessStepExecutorParams);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse).isEqualTo(expectedTaskChainResponse);
  }
}