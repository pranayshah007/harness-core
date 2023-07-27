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
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsRollingRollbackOutcome;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsRollingRollbackConfig;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
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
public class EcsRollingRollbackStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_ROLLING_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String ECS_ROLLING_ROLLBACK_COMMAND_NAME = "EcsRollingRollback";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private AccountService accountService;
  @Inject private StepHelper stepHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      EcsRollingRollbackResponse ecsRollingRollbackResponse = (EcsRollingRollbackResponse) responseDataSupplier.get();
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(ecsRollingRollbackResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, ecsRollingRollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing ecs rolling rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(Ambiance ambiance, EcsRollingRollbackResponse ecsRollingRollbackResponse,
      StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (ecsRollingRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder()
                               .setErrorMessage(ecsStepCommonHelper.getErrorMessage(ecsRollingRollbackResponse))
                               .build())
              .build();
    } else {
      EcsRollingRollbackResult ecsRollingRollbackResult = ecsRollingRollbackResponse.getEcsRollingRollbackResult();

      EcsRollingRollbackOutcome ecsRollingRollbackOutcome =
          EcsRollingRollbackOutcome.builder().firstDeployment(ecsRollingRollbackResult.isFirstDeployment()).build();

      List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
          ecsRollingRollbackResponse, ecsRollingRollbackResult.getInfrastructureKey());

      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME,
          ecsRollingRollbackOutcome, StepOutcomeGroup.STEP.name());

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(ecsRollingRollbackOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    EcsRollingRollbackStepParameters ecsRollingRollbackStepParameters =
        (EcsRollingRollbackStepParameters) stepElementParameters.getSpec();

    if (EmptyPredicate.isEmpty(ecsRollingRollbackStepParameters.getEcsRollingRollbackFnq())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Ecs Rolling Deploy Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }

    OptionalSweepingOutput ecsRollingRollbackDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsRollingRollbackStepParameters.getEcsRollingRollbackFnq() + "."
                + OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME));

    if (!ecsRollingRollbackDataOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Ecs Rolling Deploy Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }

    EcsRollingRollbackDataOutcome ecsRollingRollbackDataOutcome =
        (EcsRollingRollbackDataOutcome) ecsRollingRollbackDataOptionalOutput.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    EcsRollingRollbackConfig ecsRollingRollbackConfig =
        EcsRollingRollbackConfig.builder()
            .serviceName(ecsRollingRollbackDataOutcome.getServiceName())
            .isFirstDeployment(ecsRollingRollbackDataOutcome.isFirstDeployment())
            .createServiceRequestBuilderString(ecsRollingRollbackDataOutcome.getCreateServiceRequestBuilderString())
            .registerScalableTargetRequestBuilderStrings(
                ecsRollingRollbackDataOutcome.getRegisterScalableTargetRequestBuilderStrings())
            .registerScalingPolicyRequestBuilderStrings(
                ecsRollingRollbackDataOutcome.getRegisterScalingPolicyRequestBuilderStrings())
            .build();

    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsRollingRollbackRequest ecsRollingRollbackRequest =
        EcsRollingRollbackRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_ROLLING_ROLLBACK)
            .ecsRollingRollbackConfig(ecsRollingRollbackConfig)
            .commandName(ECS_ROLLING_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .build();

    return ecsStepCommonHelper
        .queueEcsTask(stepElementParameters, ecsRollingRollbackRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_COMMAND_TASK_NG)
        .getTaskRequest();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
