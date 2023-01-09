/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static software.wings.beans.TaskType.AWS_ASG_ROLLING_ROLLBACK_TASK_NG;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgRollingRollbackResponse;
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
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgRollingRollbackStep extends CdTaskExecutable<AsgCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_ROLLING_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String ASG_ROLLING_ROLLBACK_COMMAND_NAME = "AsgRollingRollback";

  @Inject private AsgStepCommonHelper asgStepCommonHelper;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private AccountService accountService;
  @Inject private StepHelper stepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<AsgCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      AsgRollingRollbackResponse asgRollingRollbackResponse = (AsgRollingRollbackResponse) responseDataSupplier.get();
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(asgRollingRollbackResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(asgRollingRollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing asg rolling rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      AsgRollingRollbackResponse asgRollingRollbackResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (asgRollingRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder()
                               .setErrorMessage(asgStepCommonHelper.getErrorMessage(asgRollingRollbackResponse))
                               .build())
              .build();
    } else {
      stepResponse =
          stepResponseBuilder.status(Status.SUCCEEDED)
              .stepOutcome(StepResponse.StepOutcome.builder().name(OutcomeExpressionConstants.OUTPUT).build())
              .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    AsgRollingRollbackStepParameters asgRollingRollbackStepParameters =
        (AsgRollingRollbackStepParameters) stepElementParameters.getSpec();

    if (EmptyPredicate.isEmpty(asgRollingRollbackStepParameters.getAsgRollingDeployFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Asg Rolling Deploy Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }

    OptionalSweepingOutput asgRollingPrepareRollbackDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(asgRollingRollbackStepParameters.getAsgRollingDeployFqn() + "."
                + OutcomeExpressionConstants.ASG_ROLLING_PREPARE_ROLLBACK_DATA_OUTCOME));

    if (!asgRollingPrepareRollbackDataOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Asg Rolling Deploy Step was not executed. Skipping Rollback.")
                                  .build())
          .build();
    }

    AsgRollingPrepareRollbackDataOutcome asgRollingPrepareRollbackDataOutcome =
        (AsgRollingPrepareRollbackDataOutcome) asgRollingPrepareRollbackDataOptionalOutput.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AsgRollingRollbackRequest asgRollingRollbackRequest =
        AsgRollingRollbackRequest.builder()
            .accountId(accountId)
            .asgStoreManifestsContent(asgRollingPrepareRollbackDataOutcome.getAsgStoreManifestsContent())
            .commandName(ASG_ROLLING_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .build();

    return asgStepCommonHelper
        .queueAsgTask(stepElementParameters, asgRollingRollbackRequest, ambiance,
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            AWS_ASG_ROLLING_ROLLBACK_TASK_NG)
        .getTaskRequest();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
