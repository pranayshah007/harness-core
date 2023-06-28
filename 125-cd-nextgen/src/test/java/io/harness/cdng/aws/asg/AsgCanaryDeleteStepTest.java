/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static software.wings.beans.TaskType.AWS_ASG_CANARY_DELETE_TASK_NG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
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

import software.wings.beans.TaskType;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgCanaryDeleteStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final AsgCanaryDeleteStepParameters asgSpecParameters =
      AsgCanaryDeleteStepParameters.infoBuilder().asgCanaryDeleteFqn("delete").asgCanaryDeployFqn("deploy").build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgCanaryDeleteStep asgCanaryDeleteStep;

  private final String ASG_CANARY_DELETE_COMMAND_NAME = "AsgCanaryDelete";
  UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private AccountService accountService;
  @Mock private AccountClient accountClient;
  @Mock private StepHelper stepHelper;
  @Mock private OutcomeService outcomeService;
  @Spy private InstanceInfoService instanceInfoService;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    AsgCanaryDeleteResult asgCanaryDeleteResult =
        AsgCanaryDeleteResult.builder().canaryDeleted(true).canaryAsgName("asgCanary").build();
    AsgCanaryDeleteResponse asgCanaryDeleteResponse = AsgCanaryDeleteResponse.builder()
                                                          .unitProgressData(UnitProgressData.builder().build())
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .asgCanaryDeleteResult(asgCanaryDeleteResult)
                                                          .build();
    AsgCanaryDeleteOutcome asgCanaryDeleteOutcome = AsgCanaryDeleteOutcome.builder()
                                                        .canaryDeleted(asgCanaryDeleteResult.isCanaryDeleted())
                                                        .canaryAsgName(asgCanaryDeleteResult.getCanaryAsgName())
                                                        .build();
    doReturn("output")
        .when(executionSweepingOutputService)
        .consume(ambiance, OutcomeExpressionConstants.ASG_CANARY_DELETE_OUTCOME, asgCanaryDeleteOutcome,
            StepOutcomeGroup.STEP.name());

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse = asgCanaryDeleteStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> asgCanaryDeleteResponse);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().stream().findFirst().get();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(asgCanaryDeleteOutcome);
    assertThat(stepOutcome.getName()).isEqualTo(OutcomeExpressionConstants.OUTPUT);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    AsgCanaryDeleteResult asgCanaryDeleteResult =
        AsgCanaryDeleteResult.builder().canaryDeleted(false).canaryAsgName("asgCanary").build();
    AsgCanaryDeleteResponse asgCanaryDeleteResponse = AsgCanaryDeleteResponse.builder()
                                                          .unitProgressData(UnitProgressData.builder().build())
                                                          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                          .asgCanaryDeleteResult(asgCanaryDeleteResult)
                                                          .build();
    AsgCanaryDeleteOutcome asgCanaryDeleteOutcome = AsgCanaryDeleteOutcome.builder()
                                                        .canaryDeleted(asgCanaryDeleteResult.isCanaryDeleted())
                                                        .canaryAsgName(asgCanaryDeleteResult.getCanaryAsgName())
                                                        .build();

    StepResponse stepResponse = asgCanaryDeleteStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> asgCanaryDeleteResponse);
    FailureInfo failureInfo = stepResponse.getFailureInfo();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(failureInfo.getErrorMessage()).isEqualTo(asgStepCommonHelper.getErrorMessage(asgCanaryDeleteResponse));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacExceptionTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    AsgCanaryDeleteStepParameters asgCanaryDeleteStepParameters = AsgCanaryDeleteStepParameters.infoBuilder().build();

    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(asgCanaryDeleteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    asgCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacAsgCanaryDeleteDataOptionalOutputNotFoundTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

    AutoScalingGroupContainer autoScalingGroupContainer =
        AutoScalingGroupContainer.builder().autoScalingGroupName("asgCanary").build();
    AsgCanaryDeployOutcome asgCanaryDeployOutcome =
        AsgCanaryDeployOutcome.builder().asg(autoScalingGroupContainer).build();

    OptionalSweepingOutput asgCanaryDeployOptionalOutput =
        OptionalSweepingOutput.builder().found(false).output(asgCanaryDeployOutcome).build();

    doReturn(asgCanaryDeployOptionalOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    asgCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacNoStepInRollbackSectionTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

    AutoScalingGroupContainer autoScalingGroupContainer =
        AutoScalingGroupContainer.builder().autoScalingGroupName("asgCanary").build();
    AsgCanaryDeployOutcome asgCanaryDeployOutcome =
        AsgCanaryDeployOutcome.builder().asg(autoScalingGroupContainer).build();

    OptionalSweepingOutput asgCanaryDeployOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(asgCanaryDeployOutcome).build();

    doReturn(asgCanaryDeployOptionalOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) AsgInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn(AsgInfraConfig.builder().build())
        .when(asgStepCommonHelper)
        .getAsgInfraConfig(infrastructureOutcome, ambiance);

    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    TaskChainResponse taskChainResponseAssert = TaskChainResponse.builder()
                                                    .chainEnd(true)
                                                    .taskRequest(TaskRequest.newBuilder().build())
                                                    .passThroughData(asgExecutionPassThroughData)
                                                    .build();
    doReturn(taskChainResponseAssert)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    asgCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    AsgCanaryDeleteRequest asgCanaryDeleteRequest =
        AsgCanaryDeleteRequest.builder()
            .accountId(accountId)
            .commandName(ASG_CANARY_DELETE_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .asgInfraConfig(asgInfraConfig)
            .canaryAsgName(asgCanaryDeployOutcome.getAsg().getAutoScalingGroupName())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();

    Mockito.verify(asgStepCommonHelper, times(1))
        .queueAsgTask(eq(stepElementParameters), eq(asgCanaryDeleteRequest), eq(ambiance), any(), eq(true),
            eq(AWS_ASG_CANARY_DELETE_TASK_NG));
  }
}
