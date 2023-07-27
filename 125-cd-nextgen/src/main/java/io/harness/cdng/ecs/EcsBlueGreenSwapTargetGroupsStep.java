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
import io.harness.cdng.ecs.beans.EcsBlueGreenSwapTargetGroupsOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenSwapTargetGroupsStartOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsBlueGreenSwapTargetGroupsResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenSwapTargetGroupsRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenSwapTargetGroupsResponse;
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
public class EcsBlueGreenSwapTargetGroupsStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder()
          .setType(ExecutionNodeType.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS.getYamlType())
          .setStepCategory(StepCategory.STEP)
          .build();
  public static final String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_COMMAND_NAME = "EcsBlueGreenSwapTargetGroups";
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
      EcsBlueGreenSwapTargetGroupsResponse ecsBlueGreenSwapTargetGroupsResponse =
          (EcsBlueGreenSwapTargetGroupsResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
          ecsBlueGreenSwapTargetGroupsResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, ecsBlueGreenSwapTargetGroupsResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error(
          "Error while processing ecs blue green swap target groups response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(Ambiance ambiance,
      EcsBlueGreenSwapTargetGroupsResponse ecsBlueGreenSwapTargetGroupsResponse,
      StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (ecsBlueGreenSwapTargetGroupsResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(
                                              ecsStepCommonHelper.getErrorMessage(ecsBlueGreenSwapTargetGroupsResponse))
                                          .build())
                         .build();
    } else {
      EcsBlueGreenSwapTargetGroupsResult ecsBlueGreenSwapTargetGroupsResult =
          ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult();

      EcsBlueGreenSwapTargetGroupsOutcome ecsBlueGreenSwapTargetGroupsOutcome =
          EcsBlueGreenSwapTargetGroupsOutcome.builder()
              .trafficShifted(ecsBlueGreenSwapTargetGroupsResult.isTrafficShifted())
              .loadBalancer(ecsBlueGreenSwapTargetGroupsResult.getLoadBalancer())
              .prodListenerArn(ecsBlueGreenSwapTargetGroupsResult.getProdListenerArn())
              .prodListenerRuleArn(ecsBlueGreenSwapTargetGroupsResult.getProdListenerRuleArn())
              .prodTargetGroupArn(ecsBlueGreenSwapTargetGroupsResult.getProdTargetGroupArn())
              .stageListenerArn(ecsBlueGreenSwapTargetGroupsResult.getStageListenerArn())
              .stageListenerRuleArn(ecsBlueGreenSwapTargetGroupsResult.getStageListenerRuleArn())
              .stageTargetGroupArn(ecsBlueGreenSwapTargetGroupsResult.getStageTargetGroupArn())
              .build();

      List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
          ecsBlueGreenSwapTargetGroupsResponse, ecsBlueGreenSwapTargetGroupsResult.getInfrastructureKey());

      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_OUTCOME, ecsBlueGreenSwapTargetGroupsOutcome,
          StepOutcomeGroup.STEP.name());

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(ecsBlueGreenSwapTargetGroupsOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsBlueGreenSwapTargetGroupsStepParameters ecsBlueGreenSwapTargetGroupsStepParameters =
        (EcsBlueGreenSwapTargetGroupsStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(ecsBlueGreenSwapTargetGroupsStepParameters.ecsBlueGreenCreateServiceFnq)) {
      return skipTaskRequest(ambiance, ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING);
    }

    OptionalSweepingOutput ecsBlueGreenPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                ecsBlueGreenSwapTargetGroupsStepParameters.getEcsBlueGreenCreateServiceFnq() + "."
                + OutcomeExpressionConstants.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME));

    OptionalSweepingOutput ecsBlueGreenCreateServiceDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                ecsBlueGreenSwapTargetGroupsStepParameters.getEcsBlueGreenCreateServiceFnq() + "."
                + OutcomeExpressionConstants.ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME));

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

    EcsBlueGreenSwapTargetGroupsRequest ecsBlueGreenSwapTargetGroupsRequest =
        EcsBlueGreenSwapTargetGroupsRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
            .commandName(ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .oldServiceName(ecsBlueGreenPrepareRollbackDataOutcome.getServiceName())
            .newServiceName(ecsBlueGreenCreateServiceDataOutcome.getServiceName())
            .isFirstDeployment(ecsBlueGreenPrepareRollbackDataOutcome.isFirstDeployment())
            .doNotDownsizeOldService(
                ecsBlueGreenSwapTargetGroupsStepParameters.getDoNotDownsizeOldService().getValue() != null
                && ecsBlueGreenSwapTargetGroupsStepParameters.getDoNotDownsizeOldService().getValue())
            .downsizeOldServiceDelayInSecs(ParameterFieldHelper.getIntegerParameterFieldValue(
                ecsBlueGreenSwapTargetGroupsStepParameters.getDownsizeOldServiceDelayInSecs()))
            .build();

    EcsBlueGreenSwapTargetGroupsStartOutcome ecsBlueGreenSwapTargetGroupsStartOutcome =
        EcsBlueGreenSwapTargetGroupsStartOutcome.builder().isTrafficShiftStarted(true).build();

    executionSweepingOutputService.consume(ambiance,
        OutcomeExpressionConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_START_OUTCOME,
        ecsBlueGreenSwapTargetGroupsStartOutcome, StepOutcomeGroup.STEP.name());

    return ecsStepCommonHelper
        .queueEcsTask(stepParameters, ecsBlueGreenSwapTargetGroupsRequest, ambiance,
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
