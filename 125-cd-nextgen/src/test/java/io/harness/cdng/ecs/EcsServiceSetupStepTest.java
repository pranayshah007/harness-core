/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.rule.OwnerRule.PRAGYESH;

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
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.ecs.EcsBasicDeployData;
import io.harness.delegate.beans.ecs.EcsServiceData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsResizeStrategy;
import io.harness.delegate.task.ecs.request.EcsBasicPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest.EcsServiceSetupRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsServiceSetupResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
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
public class EcsServiceSetupStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final EcsServiceSetupStepParameters ecsSpecParameters =
      EcsServiceSetupStepParameters.infoBuilder()
          .sameAsAlreadyRunningInstances(ParameterField.<Boolean>builder().value(true).build())
          .resizeStrategy(EcsResizeStrategy.RESIZE_NEW_FIRST)
          .build();

  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  @Spy @InjectMocks private EcsServiceSetupStep ecsServiceSetupStep;
  @Spy private EcsStepCommonHelper ecsStepCommonHelper;
  @Mock private InstanceInfoService instanceInfoService;

  @Test
  @Owner(developers = PRAGYESH)
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
    doReturn(taskChainResponse1).when(ecsStepCommonHelper).executeNextLinkBasic(any(), any(), any(), any(), any());
    TaskChainResponse taskChainResponse = ecsServiceSetupStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, ecsGitFetchPassThroughData, () -> responseData);
    assertThat(taskChainResponse.isChainEnd()).isEqualTo(false);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(EcsGitFetchPassThroughData.class);
    assertThat(taskChainResponse.getPassThroughData()).isEqualTo(ecsGitFetchPassThroughData);
    assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
  }

  @Test
  @Owner(developers = PRAGYESH)
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
                                                      .newServiceName("abc_2")
                                                      .oldServiceName("abc_1")
                                                      .build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_SERVICE_SETUP_TASK_NG));
    ecsServiceSetupStep.executeEcsTask(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String ECS_SERVICE_SETUP_DEPLOY_COMMAND_NAME = "EcsServiceSetup";
    EcsServiceSetupRequestBuilder ecsServiceSetupRequestBuilder =
        EcsServiceSetupRequest.builder()
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_SERVICE_SETUP)
            .commandName(ECS_SERVICE_SETUP_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .infraConfig(ecsInfraConfig)
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .taskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .serviceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .scalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .scalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .oldServiceName("abc_1")
            .newServiceName("abc_2")
            .resizeStrategy(EcsResizeStrategy.RESIZE_NEW_FIRST);
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsServiceSetupRequestBuilder.build(), ambiance,
            ecsExecutionPassThroughData, true, TaskType.ECS_SERVICE_SETUP_TASK_NG);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeEcsPrepareRollbackTaskTest() {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(ecsPrepareRollbackDataPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_BASIC_PREPARE_ROLLBACK_TASK_NG));
    ecsServiceSetupStep.executeEcsPrepareRollbackTask(
        ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String ECS_PREPARE_ROLLBACK_COMMAND_NAME = "EcsBasicPrepareRollback";

    EcsBasicPrepareRollbackRequest ecsBasicPrepareRollbackRequest =
        EcsBasicPrepareRollbackRequest.builder()
            .commandName(ECS_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_BASIC_PREPARE_ROLLBACK)
            .infraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .serviceDefinitionManifestContent(
                ecsPrepareRollbackDataPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .build();
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsBasicPrepareRollbackRequest, ambiance,
            ecsPrepareRollbackDataPassThroughData, false, TaskType.ECS_BASIC_PREPARE_ROLLBACK_TASK_NG);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();
    ResponseData responseData =
        EcsServiceSetupResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .deployData(EcsBasicDeployData.builder().build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ThrowingSupplier<ResponseData> responseDataSupplier = () -> responseData;
    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos((EcsServiceSetupResponse) responseDataSupplier.get(), "infraKey");
    StepResponse stepResponse = ecsServiceSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo(((EcsServiceSetupResponse) responseData).getErrorMessage());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build())
            .build();
    ResponseData responseData =
        EcsServiceSetupResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .deployData(EcsBasicDeployData.builder()
                            .newServiceData(EcsServiceData.builder().build())
                            .oldServiceData(EcsServiceData.builder().build())
                            .build())
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .build();
    ThrowingSupplier<ResponseData> responseDataSupplier = () -> responseData;
    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos((EcsServiceSetupResponse) responseDataSupplier.get(), "infraKey");
    doReturn(StepResponse.StepOutcome.builder().name("").build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());
    StepResponse stepResponse = ecsServiceSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}
