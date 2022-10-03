/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_PROJECT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsRollingRollbackOutcome;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsRollingRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsRollingDeployStepParameters ecsSpecParameters = EcsRollingDeployStepParameters.infoBuilder().build();

  @Mock private EcsStepHelperImpl ecsStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private AccountService accountService;
  @Mock private AccountClient accountClient;
  @Mock private StepHelper stepHelper;
  @Mock private OutcomeService outcomeService;
  @Spy private EcsStepCommonHelper ecsStepCommonHelper;

  @Spy @InjectMocks private EcsRollingRollbackStep ecsRollingDeployStep;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
    EcsRollingRollbackResult ecsRollingRollbackResult =
        EcsRollingRollbackResult.builder().firstDeployment(true).infrastructureKey("infraKey").build();
    EcsRollingRollbackResponse responseData = EcsRollingRollbackResponse.builder()
                                                  .unitProgressData(UnitProgressData.builder().build())
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .ecsRollingRollbackResult(ecsRollingRollbackResult)
                                                  .build();
    ThrowingSupplier<EcsCommandResponse> responseDataSupplier = () -> responseData;
    EcsRollingRollbackResponse ecsRollingRollbackResponse = (EcsRollingRollbackResponse) responseDataSupplier.get();

    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    EcsRollingRollbackOutcome ecsRollingRollbackOutcome =
        EcsRollingRollbackOutcome.builder().firstDeployment(true).build();
    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().outcome(ecsRollingRollbackOutcome).name("response").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos(ecsRollingRollbackResponse, ecsRollingRollbackResult.getInfrastructureKey());
    doReturn("output")
        .when(executionSweepingOutputService)
        .consume(ambiance, OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME, ecsRollingRollbackOutcome,
            StepOutcomeGroup.STEP.name());

    AccountDTO accountDTO = AccountDTO.builder().name("acc").build();
    doReturn(accountDTO).when(accountService).getAccount(AmbianceUtils.getAccountId(ambiance));

    Map<String, Object> properties = new HashMap<>();
    properties.put(TELEMETRY_ROLLBACK_PROP_PROJECT_ID, AmbianceUtils.getProjectIdentifier(ambiance));
    doReturn(properties).when(stepHelper).sendRollbackTelemetryEvent(any(), any(), any());

    StepResponse stepResponse =
        ecsRollingDeployStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(ecsRollingRollbackOutcome);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextFailedTest() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
    EcsRollingRollbackResult ecsRollingRollbackResult =
        EcsRollingRollbackResult.builder().firstDeployment(true).infrastructureKey("infraKey").build();
    EcsRollingRollbackResponse responseData = EcsRollingRollbackResponse.builder()
                                                  .unitProgressData(UnitProgressData.builder().build())
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .ecsRollingRollbackResult(ecsRollingRollbackResult)
                                                  .build();

    AccountDTO accountDTO = AccountDTO.builder().name("acc").build();
    doReturn(accountDTO).when(accountService).getAccount(AmbianceUtils.getAccountId(ambiance));
    StepResponse stepResponse =
        ecsRollingDeployStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacEmptyPredicateIsEmptyTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsRollingRollbackStepParameters ecsRollingRollbackStepParameters =
        EcsRollingRollbackStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsRollingRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TaskRequest taskRequest = ecsRollingDeployStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                    .setMessage("Ecs Rolling Deploy Step was not executed. Skipping Rollback.")
                                    .build())
            .build();
    assertThat(taskRequest).isEqualTo(taskRequestAssert);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacRollingRollbackDataOptionalOutputIsFoundTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsRollingRollbackStepParameters ecsRollingRollbackStepParameters =
        EcsRollingRollbackStepParameters.infoBuilder().ecsRollingRollbackFnq("fnq").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsRollingRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    OptionalSweepingOutput ecsRollingRollbackDataOptionalOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(ecsRollingRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsRollingRollbackStepParameters.getEcsRollingRollbackFnq() + "."
                + OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME));

    TaskRequest taskRequest = ecsRollingDeployStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                    .setMessage("Ecs Rolling Deploy Step was not executed. Skipping Rollback.")
                                    .build())
            .build();
    assertThat(taskRequest).isEqualTo(taskRequestAssert);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbac() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsRollingRollbackStepParameters ecsRollingRollbackStepParameters =
        EcsRollingRollbackStepParameters.infoBuilder().ecsRollingRollbackFnq("fnq").build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsRollingRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    EcsRollingRollbackDataOutcome ecsRollingRollbackDataOutcome = EcsRollingRollbackDataOutcome.builder().build();
    OptionalSweepingOutput ecsRollingRollbackDataOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(ecsRollingRollbackDataOutcome).build();
    doReturn(ecsRollingRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsRollingRollbackStepParameters.getEcsRollingRollbackFnq() + "."
                + OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME));

    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    doReturn(EcsInfraConfig.builder().build())
        .when(ecsStepCommonHelper)
        .getEcsInfraConfig(infrastructureOutcome, ambiance);

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(false)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(ecsExecutionPassThroughData)
                                               .build();
    doReturn(taskChainResponse1).when(ecsStepCommonHelper).queueEcsTask(any(), any(), any(), any(), anyBoolean());

    TaskRequest taskRequest = ecsRollingDeployStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    assertThat(taskRequest).isEqualTo(TaskRequest.newBuilder().build());
  }
}
