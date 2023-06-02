/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.exception.WingsException.USER;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsCanaryDeleteDataOutcome;
import io.harness.cdng.ecs.beans.EcsCanaryDeleteOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ecs.EcsCanaryDeleteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.request.EcsCanaryDeleteRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
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
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsCanaryDeleteStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_CANARY_DELETE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String ECS_CANARY_DELETE_COMMAND_NAME = "EcsCanaryDelete";
  public static final String ECS_CANARY_DELETE_STEP_MISSING = "Canary Deploy step is not configured.";
  public static final String ECS_CANARY_DELETE_STEP_ALREADY_EXECUTED =
      "Canary Service has already been deleted. Skipping delete canary service in rollback";
  public static final String ECS_CANARY_DELETE_STEP_SKIPPED =
      "Ecs Canary Deploy Step was not executed. Skipping Canary Delete.";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private AccountService accountService;
  @Inject private StepHelper stepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      EcsCanaryDeleteResponse ecsCanaryDeleteResponse = (EcsCanaryDeleteResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(ecsCanaryDeleteResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, ecsCanaryDeleteResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing ecs canary delete response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, EcsCanaryDeleteResponse ecsCanaryDeleteResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (ecsCanaryDeleteResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(ecsStepCommonHelper.getErrorMessage(ecsCanaryDeleteResponse))
                                          .build())
                         .build();
    } else {
      EcsCanaryDeleteResult ecsCanaryDeleteResult = ecsCanaryDeleteResponse.getEcsCanaryDeleteResult();

      EcsCanaryDeleteOutcome ecsCanaryDeleteOutcome =
          EcsCanaryDeleteOutcome.builder()
              .canaryDeleted(ecsCanaryDeleteResult.isCanaryDeleted())
              .canaryServiceName(ecsCanaryDeleteResult.getCanaryServiceName())
              .build();

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_CANARY_DELETE_OUTCOME,
          ecsCanaryDeleteOutcome, StepOutcomeGroup.STEP.name());

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(ecsCanaryDeleteOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsCanaryDeleteStepParameters ecsCanaryDeleteStepParameters =
        (EcsCanaryDeleteStepParameters) stepElementParameters.getSpec();

    if (EmptyPredicate.isEmpty(ecsCanaryDeleteStepParameters.getEcsCanaryDeployFnq())) {
      throw new InvalidRequestException(ECS_CANARY_DELETE_STEP_MISSING, USER);
    }

    OptionalSweepingOutput ecsCanaryDeleteDataOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(ecsCanaryDeleteStepParameters.getEcsCanaryDeployFnq() + "."
            + OutcomeExpressionConstants.ECS_CANARY_DELETE_DATA_OUTCOME));

    if (!ecsCanaryDeleteDataOptionalOutput.isFound()) {
      return skipTaskRequestOrThrowException(ambiance);
    }

    EcsCanaryDeleteDataOutcome ecsCanaryDeleteDataOutcome =
        (EcsCanaryDeleteDataOutcome) ecsCanaryDeleteDataOptionalOutput.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

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

    return ecsStepCommonHelper
        .queueEcsTask(stepElementParameters, ecsCanaryDeleteRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_COMMAND_TASK_NG)
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequestOrThrowException(Ambiance ambiance) {
    if (StepUtils.isStepInRollbackSection(ambiance)) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(ECS_CANARY_DELETE_STEP_SKIPPED).build())
          .build();
    }

    throw new InvalidRequestException(ECS_CANARY_DELETE_STEP_MISSING, USER);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
