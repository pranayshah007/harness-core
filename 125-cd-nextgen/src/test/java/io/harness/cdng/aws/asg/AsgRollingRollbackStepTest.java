/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.steps.TelemetryRollbackConstants.TELEMETRY_ROLLBACK_PROP_PROJECT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.info.AsgServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackResponse;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackResult;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;

import software.wings.beans.TaskType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgRollingRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Spy private OutcomeService outcomeService;
  @Mock private AccountService accountService;
  @Mock private StepHelper stepHelper;
  @Spy private InstanceInfoService instanceInfoService;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  AsgRollingRollbackStepParameters asgRollingRollbackStepParameters =
      AsgRollingRollbackStepParameters.infoBuilder().asgRollingDeployFqn("fqn").build();
  StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                    .spec(asgRollingRollbackStepParameters)
                                                    .timeout(ParameterField.createValueField("10m"))
                                                    .build();

  StepInputPackage inputPackage = StepInputPackage.builder().build();

  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgRollingRollbackStep asgRollingRollbackStep;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    AsgRollingRollbackResult asgRollingRollbackResult =
        AsgRollingRollbackResult.builder()
            .autoScalingGroupContainer(AutoScalingGroupContainer.builder().autoScalingGroupName("asg").build())
            .build();

    AsgRollingRollbackResponse responseData = AsgRollingRollbackResponse.builder()
                                                  .unitProgressData(UnitProgressData.builder().build())
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .asgRollingRollbackResult(asgRollingRollbackResult)
                                                  .build();

    AccountDTO accountDTO = AccountDTO.builder().name("acc").build();
    doReturn(accountDTO).when(accountService).getAccount(AmbianceUtils.getAccountId(ambiance));

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    Map<String, Object> properties = new HashMap<>();
    properties.put(TELEMETRY_ROLLBACK_PROP_PROJECT_ID, AmbianceUtils.getProjectIdentifier(ambiance));
    doReturn(properties).when(stepHelper).sendRollbackTelemetryEvent(any(), any(), any());

    AsgInfrastructureOutcome asgInfrastructureOutcome =
        AsgInfrastructureOutcome.builder().infrastructureKey("abcd").build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(asgInfrastructureOutcome, ambiance);
    AsgServerInstanceInfo asgServerInstanceInfo = AsgServerInstanceInfo.builder().build();
    doReturn(List.of(asgServerInstanceInfo)).when(asgStepCommonHelper).getServerInstanceInfos(any(), any(), any());

    doReturn(asgInfrastructureOutcome).when(outcomeService).resolve(any(), any());
    StepResponse stepResponse =
        asgRollingRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgRollingRollbackOutcome.class);
    assertThat(outcomeMap.get(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME))
        .isInstanceOf(DeploymentInfoOutcome.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextFailedTest() throws Exception {
    AsgRollingRollbackResult asgRollingRollbackResult = AsgRollingRollbackResult.builder().build();
    AsgRollingRollbackResponse responseData = AsgRollingRollbackResponse.builder()
                                                  .unitProgressData(UnitProgressData.builder().build())
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .asgRollingRollbackResult(asgRollingRollbackResult)
                                                  .build();

    AccountDTO accountDTO = AccountDTO.builder().name("acc").build();
    doReturn(accountDTO).when(accountService).getAccount(AmbianceUtils.getAccountId(ambiance));
    StepResponse stepResponse =
        asgRollingRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacEmptyPredicateIsEmptyTest() {
    AsgRollingRollbackStepParameters asgRollingRollbackStepParameters =
        AsgRollingRollbackStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(asgRollingRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TaskRequest taskRequest = asgRollingRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                    .setMessage("Asg Rolling Deploy Step was not executed. Skipping Rollback.")
                                    .build())
            .build();
    assertThat(taskRequest).isEqualTo(taskRequestAssert);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacRollingRollbackDataOptionalOutputIsNotFoundTest() {
    OptionalSweepingOutput asgRollingRollbackDataOptionalOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(asgRollingRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(asgRollingRollbackStepParameters.getAsgRollingDeployFqn() + "."
                + OutcomeExpressionConstants.ASG_ROLLING_PREPARE_ROLLBACK_DATA_OUTCOME));

    TaskRequest taskRequest = asgRollingRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                    .setMessage("Asg Rolling Deploy Step was not executed. Skipping Rollback.")
                                    .build())
            .build();
    assertThat(taskRequest).isEqualTo(taskRequestAssert);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbac() {
    AsgRollingPrepareRollbackDataOutcome asgRollingPrepareRollbackDataOutcome =
        AsgRollingPrepareRollbackDataOutcome.builder().build();

    OptionalSweepingOutput asgRollingPrepareRollbackDataOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(asgRollingPrepareRollbackDataOutcome).build();

    doReturn(asgRollingPrepareRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(asgRollingRollbackStepParameters.getAsgRollingDeployFqn() + "."
                + OutcomeExpressionConstants.ASG_ROLLING_PREPARE_ROLLBACK_DATA_OUTCOME));

    AsgInfrastructureOutcome infrastructureOutcome = AsgInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn(AsgInfraConfig.builder().build())
        .when(asgStepCommonHelper)
        .getAsgInfraConfig(infrastructureOutcome, ambiance);

    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(true)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgExecutionPassThroughData)
                                               .build();

    doReturn(taskChainResponse1)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    TaskRequest taskRequest = asgRollingRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
    assertThat(taskRequest).isEqualTo(TaskRequest.newBuilder().build());
  }
}