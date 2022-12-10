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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.account.AccountClient;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsCanaryDeleteDataOutcome;
import io.harness.cdng.ecs.beans.EcsCanaryDeleteOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.ecs.EcsCanaryDeleteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsCanaryDeleteRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
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

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsCanaryDeleteStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsCanaryDeleteStepParameters ecsSpecParameters = EcsCanaryDeleteStepParameters.infoBuilder().build();
  private final String ECS_CANARY_DELETE_COMMAND_NAME = "EcsCanaryDelete";

  @Mock private EcsStepHelperImpl ecsStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private AccountService accountService;
  @Mock private AccountClient accountClient;
  @Mock private StepHelper stepHelper;
  @Mock private OutcomeService outcomeService;
  @Spy private EcsStepCommonHelper ecsStepCommonHelper;

  @Spy @InjectMocks private EcsCanaryDeleteStep ecsCanaryDeleteStep;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
    EcsCanaryDeleteResult ecsCanaryDeleteResult =
        EcsCanaryDeleteResult.builder().canaryDeleted(true).canaryServiceName("ecsCanary").build();
    EcsCanaryDeleteResponse ecsCanaryDeleteResponse = EcsCanaryDeleteResponse.builder()
                                                          .unitProgressData(UnitProgressData.builder().build())
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .ecsCanaryDeleteResult(ecsCanaryDeleteResult)
                                                          .build();
    EcsCanaryDeleteOutcome ecsCanaryDeleteOutcome = EcsCanaryDeleteOutcome.builder()
                                                        .canaryDeleted(ecsCanaryDeleteResult.isCanaryDeleted())
                                                        .canaryServiceName(ecsCanaryDeleteResult.getCanaryServiceName())
                                                        .build();
    doReturn("output")
        .when(executionSweepingOutputService)
        .consume(ambiance, OutcomeExpressionConstants.ECS_CANARY_DELETE_OUTCOME, ecsCanaryDeleteOutcome,
            StepOutcomeGroup.STEP.name());

    StepResponse stepResponse = ecsCanaryDeleteStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> ecsCanaryDeleteResponse);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().stream().findFirst().get();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(ecsCanaryDeleteOutcome);
    assertThat(stepOutcome.getName()).isEqualTo(OutcomeExpressionConstants.OUTPUT);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
    EcsCanaryDeleteResult ecsCanaryDeleteResult =
        EcsCanaryDeleteResult.builder().canaryDeleted(false).canaryServiceName("ecsCanary").build();
    EcsCanaryDeleteResponse ecsCanaryDeleteResponse = EcsCanaryDeleteResponse.builder()
                                                          .unitProgressData(UnitProgressData.builder().build())
                                                          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                          .ecsCanaryDeleteResult(ecsCanaryDeleteResult)
                                                          .build();

    StepResponse stepResponse = ecsCanaryDeleteStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> ecsCanaryDeleteResponse);
    FailureInfo failureInfo = stepResponse.getFailureInfo();

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(failureInfo.getErrorMessage()).isEqualTo(ecsStepCommonHelper.getErrorMessage(ecsCanaryDeleteResponse));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacExceptionTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsCanaryDeleteStepParameters ecsCanaryDeleteStepParameters = EcsCanaryDeleteStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsCanaryDeleteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    ecsCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacEcsCanaryDeleteDataOptionalOutputNotFoundTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsCanaryDeleteStepParameters ecsCanaryDeleteStepParameters = EcsCanaryDeleteStepParameters.infoBuilder()
                                                                      .ecsCanaryDeployFnq("deployFnq")
                                                                      .ecsCanaryDeleteFnq("deleteFnq")
                                                                      .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsCanaryDeleteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    EcsCanaryDeleteDataOutcome ecsCanaryDeleteDataOutcome = EcsCanaryDeleteDataOutcome.builder()
                                                                .createServiceRequestBuilderString("service")
                                                                .ecsServiceNameSuffix("Canary")
                                                                .build();
    OptionalSweepingOutput ecsCanaryDeleteDataOptionalOutput =
        OptionalSweepingOutput.builder().found(false).output(ecsCanaryDeleteDataOutcome).build();
    doReturn(ecsCanaryDeleteDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsCanaryDeleteStepParameters.getEcsCanaryDeployFnq() + "."
                + OutcomeExpressionConstants.ECS_CANARY_DELETE_DATA_OUTCOME));

    ecsCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacNoStepInRollbackSectionTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsCanaryDeleteStepParameters ecsCanaryDeleteStepParameters = EcsCanaryDeleteStepParameters.infoBuilder()
                                                                      .ecsCanaryDeployFnq("deployFnq")
                                                                      .ecsCanaryDeleteFnq("deleteFnq")
                                                                      .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(ecsCanaryDeleteStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    EcsCanaryDeleteDataOutcome ecsCanaryDeleteDataOutcome = EcsCanaryDeleteDataOutcome.builder()
                                                                .createServiceRequestBuilderString("service")
                                                                .ecsServiceNameSuffix("Canary")
                                                                .build();
    OptionalSweepingOutput ecsCanaryDeleteDataOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(ecsCanaryDeleteDataOutcome).build();
    doReturn(ecsCanaryDeleteDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsCanaryDeleteStepParameters.getEcsCanaryDeployFnq() + "."
                + OutcomeExpressionConstants.ECS_CANARY_DELETE_DATA_OUTCOME));

    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn(EcsInfraConfig.builder().build())
        .when(ecsStepCommonHelper)
        .getEcsInfraConfig(infrastructureOutcome, ambiance);

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    TaskChainResponse taskChainResponseAssert = TaskChainResponse.builder()
                                                    .chainEnd(false)
                                                    .taskRequest(TaskRequest.newBuilder().build())
                                                    .passThroughData(ecsExecutionPassThroughData)
                                                    .build();
    doReturn(taskChainResponseAssert).when(ecsStepCommonHelper).queueEcsTask(any(), any(), any(), any(), anyBoolean());

    ecsCanaryDeleteStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsCanaryDeleteRequest ecsCanaryDeleteRequest =
        EcsCanaryDeleteRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_CANARY_DELETE)
            .commandName(ECS_CANARY_DELETE_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .ecsServiceDefinitionManifestContent(ecsCanaryDeleteDataOutcome.getCreateServiceRequestBuilderString())
            .ecsServiceNameSuffix(ecsCanaryDeleteDataOutcome.getEcsServiceNameSuffix())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();

    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsCanaryDeleteRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true);
  }
}
