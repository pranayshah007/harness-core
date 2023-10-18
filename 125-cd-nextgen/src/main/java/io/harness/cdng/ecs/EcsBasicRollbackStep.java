/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsBasicPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsBasicRollbackOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.request.EcsBasicRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBasicRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsBasicRollbackStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_BASIC_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private InstanceInfoService instanceInfoService;
  public static final String ECS_BASIC_ROLLBACK_COMMAND_NAME = "EcsBasicRollback";

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      EcsBasicRollbackResponse basicRollbackResponse = (EcsBasicRollbackResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(basicRollbackResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, basicRollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing ecs basic rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, EcsBasicRollbackResponse basicRollbackResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (basicRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(ecsStepCommonHelper.getErrorMessage(basicRollbackResponse))
                                          .build())
                         .build();
    } else {
      EcsBasicRollbackOutcome basicRollbackOutcome =
          EcsBasicRollbackOutcome.builder()
              .oldService(basicRollbackResponse.getRollbackData().getOldServiceData().getServiceName())
              .newService(basicRollbackResponse.getRollbackData().getNewServiceData().getServiceName())
              .isFirstTimeDeployment(basicRollbackResponse.getRollbackData().isFirstDeployment())
              .build();

      List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
          basicRollbackResponse, basicRollbackResponse.getRollbackData().getInfrastructureKey());

      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(basicRollbackOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBasicRollbackStepParameters basicRollbackStepParameters =
        (EcsBasicRollbackStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(basicRollbackStepParameters.getEcsServiceSetupFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Ecs Service Setup Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }
    OptionalSweepingOutput prepareRollbackDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(basicRollbackStepParameters.getEcsServiceSetupFqn() + "."
            + OutcomeExpressionConstants.ECS_BASIC_PREPARE_ROLLBACK_DATA_OUTCOME));

    if (!prepareRollbackDataOptional.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Ecs Service Setup Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }

    EcsBasicPrepareRollbackDataOutcome prepareRollbackData =
        (EcsBasicPrepareRollbackDataOutcome) prepareRollbackDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    EcsBasicRollbackRequest basicRollbackRequest =
        EcsBasicRollbackRequest.builder()
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_BASIC_ROLLBACK)
            .commandName(ECS_BASIC_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .infraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
            .registerScalableTargetRequestYaml(prepareRollbackData.getRegisterScalableTargetRequestYaml())
            .registerScalingPolicyRequestYaml(prepareRollbackData.getRegisterScalingPolicyRequestYaml())
            .createServiceRequestYaml(prepareRollbackData.getCreateServiceRequestYaml())
            .newServiceName(prepareRollbackData.getNewServiceName())
            .oldServiceName(prepareRollbackData.getOldServiceName())
            .isFirstDeployment(prepareRollbackData.isFirstDeployment())
            .build();
    return ecsStepCommonHelper
        .queueEcsTask(stepParameters, basicRollbackRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_BASIC_ROLLBACK_TASK_NG)
        .getTaskRequest();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
}
