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
import io.harness.cdng.ecs.beans.EcsBasicPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.ecs.EcsBasicRollbackData;
import io.harness.delegate.beans.ecs.EcsServiceData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsBasicRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBasicRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

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
public class EcsBasicRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final EcsBasicRollbackStepParameters ecsSpecParameters = EcsBasicRollbackStepParameters.infoBuilder().build();

  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  @Mock private EcsStepCommonHelper ecsStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private InstanceInfoService instanceInfoService;

  @Spy @InjectMocks private EcsBasicRollbackStep ecsBasicRollbackStep;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbac() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    OptionalSweepingOutput prepareRollbackDataOptionalOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(EcsBasicPrepareRollbackDataOutcome.builder().build())
            .build();

    doReturn(prepareRollbackDataOptionalOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(EcsInfraConfig.builder().build()).when(ecsStepCommonHelper).getEcsInfraConfig(any(), any());
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    TaskChainResponse taskChainResponseMock = TaskChainResponse.builder()
                                                  .chainEnd(false)
                                                  .taskRequest(TaskRequest.newBuilder().build())
                                                  .passThroughData(ecsExecutionPassThroughData)
                                                  .build();
    doReturn(taskChainResponseMock)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_BASIC_ROLLBACK_TASK_NG));

    EcsBasicRollbackStepParameters ecsSpecParameters =
        EcsBasicRollbackStepParameters.infoBuilder().ecsServiceSetupFqn("abcd").ecsUpgradeContainerFqn("abcde").build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();

    ecsBasicRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBasicRollbackRequest basicRollbackRequest =
        EcsBasicRollbackRequest.builder()
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_BASIC_ROLLBACK)
            .commandName("EcsBasicRollback")
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .infraConfig(EcsInfraConfig.builder().build())
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepElementParameters))
            .build();

    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, basicRollbackRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_BASIC_ROLLBACK_TASK_NG);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBasicRollbackResponse responseData = EcsBasicRollbackResponse.builder()
                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                .rollbackData(EcsBasicRollbackData.builder()
                                                                  .oldServiceData(EcsServiceData.builder().build())
                                                                  .newServiceData(EcsServiceData.builder().build())
                                                                  .build())
                                                .unitProgressData(unitProgressData)
                                                .build();

    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos).when(ecsStepCommonHelper).getServerInstanceInfos(any(), any());

    doReturn(StepResponse.StepOutcome.builder().name("").build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse =
        ecsBasicRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextFailedTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBasicRollbackResponse responseData = EcsBasicRollbackResponse.builder()
                                                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                .errorMessage("error")
                                                .unitProgressData(unitProgressData)
                                                .build();

    StepResponse stepResponse =
        ecsBasicRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);

    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(responseData.getErrorMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacPrepareRollbackDataNotFoundTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    TaskRequest taskRequest = ecsBasicRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                    .setMessage("Ecs Service Setup Step was not executed. Skipping Rollback.")
                                    .build())
            .build();
    assertThat(taskRequest.getSkipTaskRequest()).isEqualTo(taskRequestAssert.getSkipTaskRequest());
  }
}
