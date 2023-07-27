/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsBlueGreenCreateServiceDataOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenRollbackOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenSwapTargetGroupsStartOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest.EcsBlueGreenRollbackRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsBlueGreenRollbackStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_BLUE_GREEN_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String ECS_BLUE_GREEN_ROLLBACK_COMMAND_NAME = "EcsBlueGreenRollback";
  public static final String ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING =
      "Blue Green Create Service step is not configured.";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private AccountService accountService;
  @Inject private StepHelper stepHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse =
          (EcsBlueGreenRollbackResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
          ecsBlueGreenRollbackResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, ecsBlueGreenRollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing ecs blue green rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(Ambiance ambiance,
      EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (ecsBlueGreenRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder()
                               .setErrorMessage(ecsStepCommonHelper.getErrorMessage(ecsBlueGreenRollbackResponse))
                               .build())
              .build();
    } else {
      EcsBlueGreenRollbackResult ecsBlueGreenRollbackResult =
          ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult();

      EcsBlueGreenRollbackOutcome ecsBlueGreenRollbackOutcome =
          EcsBlueGreenRollbackOutcome.builder()
              .isFirstDeployment(ecsBlueGreenRollbackResult.isFirstDeployment())
              .loadBalancer(ecsBlueGreenRollbackResult.getLoadBalancer())
              .prodListenerArn(ecsBlueGreenRollbackResult.getProdListenerArn())
              .prodListenerRuleArn(ecsBlueGreenRollbackResult.getProdListenerRuleArn())
              .prodTargetGroupArn(ecsBlueGreenRollbackResult.getProdTargetGroupArn())
              .stageListenerArn(ecsBlueGreenRollbackResult.getStageListenerArn())
              .stageListenerRuleArn(ecsBlueGreenRollbackResult.getStageListenerRuleArn())
              .stageTargetGroupArn(ecsBlueGreenRollbackResult.getStageTargetGroupArn())
              .build();

      List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
          ecsBlueGreenRollbackResponse, ecsBlueGreenRollbackResult.getInfrastructureKey());

      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_BLUE_GREEN_ROLLBACK_OUTCOME,
          ecsBlueGreenRollbackOutcome, StepOutcomeGroup.STEP.name());

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(ecsBlueGreenRollbackOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBlueGreenRollbackStepParameters ecsBlueGreenRollbackStepParameters =
        (EcsBlueGreenRollbackStepParameters) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(ecsBlueGreenRollbackStepParameters.ecsBlueGreenCreateServiceFnq)) {
      return skipTaskRequest(ambiance, ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING);
    }

    OptionalSweepingOutput ecsBlueGreenPrepareRollbackDataOptional = executionSweepingOutputService.resolveOptional(
        ambiance,
        RefObjectUtils.getSweepingOutputRefObject(ecsBlueGreenRollbackStepParameters.getEcsBlueGreenCreateServiceFnq()
            + "." + OutcomeExpressionConstants.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME));

    OptionalSweepingOutput ecsBlueGreenCreateServiceDataOptional = executionSweepingOutputService.resolveOptional(
        ambiance,
        RefObjectUtils.getSweepingOutputRefObject(ecsBlueGreenRollbackStepParameters.getEcsBlueGreenCreateServiceFnq()
            + "." + OutcomeExpressionConstants.ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME));

    if (!ecsBlueGreenPrepareRollbackDataOptional.isFound() || !ecsBlueGreenCreateServiceDataOptional.isFound()) {
      return skipTaskRequest(ambiance, ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING);
    }
    EcsBlueGreenPrepareRollbackDataOutcome ecsBlueGreenPrepareRollbackDataOutcome =
        (EcsBlueGreenPrepareRollbackDataOutcome) ecsBlueGreenPrepareRollbackDataOptional.getOutput();

    EcsBlueGreenCreateServiceDataOutcome ecsBlueGreenCreateServiceDataOutcome =
        (EcsBlueGreenCreateServiceDataOutcome) ecsBlueGreenCreateServiceDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenPrepareRollbackDataOutcome.getLoadBalancer())
            .prodListenerArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdListenerArn())
            .prodListenerRuleArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdListenerRuleArn())
            .prodTargetGroupArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdTargetGroupArn())
            .stageListenerArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageListenerArn())
            .stageListenerRuleArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageListenerRuleArn())
            .stageTargetGroupArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageTargetGroupArn())
            .build();

    EcsBlueGreenRollbackRequestBuilder ecsBlueGreenRollbackRequestBuilder =
        EcsBlueGreenRollbackRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_ROLLBACK)
            .commandName(ECS_BLUE_GREEN_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .oldServiceName(ecsBlueGreenPrepareRollbackDataOutcome.getServiceName())
            .newServiceName(ecsBlueGreenCreateServiceDataOutcome.getServiceName())
            .isFirstDeployment(ecsBlueGreenPrepareRollbackDataOutcome.isFirstDeployment())
            .isNewServiceCreated(ecsBlueGreenCreateServiceDataOutcome.isNewServiceCreated())
            .oldServiceCreateRequestBuilderString(
                ecsBlueGreenPrepareRollbackDataOutcome.getCreateServiceRequestBuilderString())
            .oldServiceScalableTargetManifestContentList(
                ecsBlueGreenPrepareRollbackDataOutcome.getRegisterScalableTargetRequestBuilderStrings())
            .oldServiceScalingPolicyManifestContentList(
                ecsBlueGreenPrepareRollbackDataOutcome.getRegisterScalingPolicyRequestBuilderStrings())
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig);

    if (EmptyPredicate.isEmpty(ecsBlueGreenRollbackStepParameters.ecsBlueGreenSwapTargetGroupsFnq)) {
      ecsBlueGreenRollbackRequestBuilder.isTargetShiftStarted(false);
      return ecsStepCommonHelper
          .queueEcsTask(stepParameters, ecsBlueGreenRollbackRequestBuilder.build(), ambiance,
              EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
              TaskType.ECS_COMMAND_TASK_NG)
          .getTaskRequest();
    }
    OptionalSweepingOutput ecsBlueGreenSwapTargetGroupsStartDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                ecsBlueGreenRollbackStepParameters.getEcsBlueGreenSwapTargetGroupsFnq() + "."
                + OutcomeExpressionConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_START_OUTCOME));
    if (!ecsBlueGreenSwapTargetGroupsStartDataOptional.isFound()) {
      ecsBlueGreenRollbackRequestBuilder.isTargetShiftStarted(false);
      return ecsStepCommonHelper
          .queueEcsTask(stepParameters, ecsBlueGreenRollbackRequestBuilder.build(), ambiance,
              EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
              TaskType.ECS_COMMAND_TASK_NG)
          .getTaskRequest();
    }
    EcsBlueGreenSwapTargetGroupsStartOutcome ecsBlueGreenSwapTargetGroupsStartOutcome =
        (EcsBlueGreenSwapTargetGroupsStartOutcome) ecsBlueGreenSwapTargetGroupsStartDataOptional.getOutput();
    ecsBlueGreenRollbackRequestBuilder.isTargetShiftStarted(
        ecsBlueGreenSwapTargetGroupsStartOutcome.isTrafficShiftStarted());
    return ecsStepCommonHelper
        .queueEcsTask(stepParameters, ecsBlueGreenRollbackRequestBuilder.build(), ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_COMMAND_TASK_NG)
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequest(Ambiance ambiance, String message) {
    return TaskRequest.newBuilder()
        .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(message).build())
        .build();
  }
}
