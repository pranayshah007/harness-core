/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsPrepareRollbackDataRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest.EcsRollingDeployRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class EcsRollingDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsRollingDeployStepParameters ecsSpecParameters =
      EcsRollingDeployStepParameters.infoBuilder()
          .sameAsAlreadyRunningInstances(ParameterField.<Boolean>builder().value(true).build())
          .forceNewDeployment(ParameterField.<Boolean>builder().value(true).build())
          .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  @Mock private EcsStepHelperImpl ecsStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Spy private EcsStepCommonHelper ecsStepCommonHelper;

  @Spy @InjectMocks private EcsRollingDeployStep ecsRollingDeployStep;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsGitFetchPassThroughData ecsGitFetchPassThroughData = EcsGitFetchPassThroughData.builder().build();
    GitFile gitFile = GitFile.builder().filePath("harness/path").fileContent("content").build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().accountId("abc").files(Arrays.asList(gitFile)).build();
    ResponseData responseData = EcsGitFetchResponse.builder()
                                    .ecsTaskDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsServiceDefinitionFetchFilesResult(fetchFilesResult)
                                    .ecsScalableTargetFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .ecsScalingPolicyFetchFilesResults(Arrays.asList(fetchFilesResult))
                                    .taskStatus(TaskStatus.SUCCESS)
                                    .build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsGitFetchPassThroughData)
                                               .build();
    doReturn(taskChainResponse1)
        .when(ecsStepCommonHelper)
        .executeNextLinkRolling(any(), any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = ecsRollingDeployStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, ecsGitFetchPassThroughData, () -> responseData);
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsGitFetchPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsGitFetchPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextEcsGitFetchFailurePassThroughDataTest() throws Exception {
    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        EcsGitFetchFailurePassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMsg("error")
            .build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();

    StepResponse stepResponse = ecsRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsGitFetchFailurePassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder().setErrorMessage(ecsGitFetchFailurePassThroughData.getErrorMsg()).build());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextEcsStepExceptionPassThroughDataTest() throws Exception {
    EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
        EcsStepExceptionPassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();

    StepResponse stepResponse = ecsRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsStepExceptionPassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();
    EcsTask ecsTask = EcsTask.builder().serviceName("ecs-service").build();
    ResponseData responseData =
        EcsRollingDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsRollingDeployResult(EcsRollingDeployResult.builder().ecsTasks(Arrays.asList(ecsTask)).build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ThrowingSupplier<ResponseData> responseDataSupplier = () -> responseData;
    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos((EcsRollingDeployResponse) responseDataSupplier.get(), "infraKey");
    StepResponse stepResponse = ecsRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();
    ResponseData responseData =
        EcsRollingDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .ecsRollingDeployResult(EcsRollingDeployResult.builder().build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ThrowingSupplier<ResponseData> responseDataSupplier = () -> responseData;
    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos((EcsRollingDeployResponse) responseDataSupplier.get(), "infraKey");
    StepResponse stepResponse = ecsRollingDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo(((EcsRollingDeployResponse) responseData).getErrorMessage());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeEcsTaskTest() {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsStepExecutorParams ecsStepExecutorParams = EcsStepExecutorParams.builder()
                                                      .ecsTaskDefinitionManifestContent("taskDef")
                                                      .ecsServiceDefinitionManifestContent("service")
                                                      .build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_COMMAND_TASK_NG));
    ecsRollingDeployStep.executeEcsTask(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String ECS_ROLLING_DEPLOY_COMMAND_NAME = "EcsRollingDeploy";
    EcsRollingDeployRequestBuilder ecsRollingDeployRequestBuilder =
        EcsRollingDeployRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_ROLLING_DEPLOY)
            .commandName(ECS_ROLLING_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsInfraConfig)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList());
    EcsRollingDeployStepParameters ecsRollingDeployStepParameters =
        (EcsRollingDeployStepParameters) stepElementParameters.getSpec();
    ecsRollingDeployRequestBuilder.sameAsAlreadyRunningInstances(
        ecsRollingDeployStepParameters.getSameAsAlreadyRunningInstances().getValue().booleanValue());
    ecsRollingDeployRequestBuilder.forceNewDeployment(
        ecsRollingDeployStepParameters.getForceNewDeployment().getValue().booleanValue());
    EcsRollingDeployRequest ecsRollingDeployRequest = ecsRollingDeployRequestBuilder.build();
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsRollingDeployRequest, ambiance, ecsExecutionPassThroughData, true,
            TaskType.ECS_COMMAND_TASK_NG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeEcsPrepareRollbackTaskTest() {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_COMMAND_TASK_NG));
    ecsRollingDeployStep.executeEcsPrepareRollbackTask(
        ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String ECS_PREPARE_ROLLBACK_COMMAND_NAME = "EcsPrepareRollback";

    EcsPrepareRollbackDataRequest ecsPrepareRollbackDataRequest =
        EcsPrepareRollbackDataRequest.builder()
            .commandName(ECS_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_PREPARE_ROLLBACK_DATA)
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .ecsServiceDefinitionManifestContent(
                ecsPrepareRollbackDataPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsPrepareRollbackDataRequest, ambiance,
            ecsPrepareRollbackDataPassThroughData, false, TaskType.ECS_COMMAND_TASK_NG);
  }
}
