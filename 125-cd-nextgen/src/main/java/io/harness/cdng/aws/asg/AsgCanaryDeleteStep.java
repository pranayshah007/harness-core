/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.AWS_ASG_CANARY_DELETE_TASK_NG;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
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

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgCanaryDeleteStep extends CdTaskExecutable<AsgCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_CANARY_DELETE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String ASG_CANARY_DELETE_COMMAND_NAME = "AsgCanaryDelete";
  public static final String ASG_CANARY_DEPLOY_STEP_MISSING = "Canary Deploy step is not configured.";
  public static final String ASG_CANARY_DELETE_STEP_ALREADY_EXECUTED =
      "Canary asg has already been deleted. Skipping delete canary asg in rollback";
  public static final String ASG_CANARY_DELETE_STEP_SKIPPED =
      "Asg Canary Deploy Step was not executed. Skipping Canary Delete.";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private AsgStepCommonHelper asgStepCommonHelper;
  @Inject private AccountService accountService;
  @Inject private StepHelper stepHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<AsgCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      AsgCanaryDeleteResponse asgCanaryDeleteResponse = (AsgCanaryDeleteResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(asgCanaryDeleteResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, asgCanaryDeleteResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing asg canary delete response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, AsgCanaryDeleteResponse asgCanaryDeleteResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (asgCanaryDeleteResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(asgStepCommonHelper.getErrorMessage(asgCanaryDeleteResponse))
                                          .build())
                         .build();
    } else {
      AsgCanaryDeleteResult asgCanaryDeleteResult = asgCanaryDeleteResponse.getAsgCanaryDeleteResult();

      AsgCanaryDeleteOutcome asgCanaryDeleteOutcome = AsgCanaryDeleteOutcome.builder()
                                                          .canaryDeleted(asgCanaryDeleteResult.isCanaryDeleted())
                                                          .canaryAsgName(asgCanaryDeleteResult.getCanaryAsgName())
                                                          .build();

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ASG_CANARY_DELETE_OUTCOME,
          asgCanaryDeleteOutcome, StepOutcomeGroup.STEP.name());

      List<ServerInstanceInfo> serverInstanceInfos =
          asgStepCommonHelper.getServerInstanceInfos(asgCanaryDeleteResponse, null, null);
      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(asgCanaryDeleteOutcome)
                                          .build())
                         .stepOutcome(stepOutcome)
                         .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AsgCanaryDeleteStepParameters asgCanaryDeleteStepParameters =
        (AsgCanaryDeleteStepParameters) stepElementParameters.getSpec();

    if (EmptyPredicate.isEmpty(asgCanaryDeleteStepParameters.getAsgCanaryDeployFqn())) {
      throw new InvalidRequestException(ASG_CANARY_DEPLOY_STEP_MISSING, USER);
    }

    OptionalSweepingOutput asgCanaryDeployOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(asgCanaryDeleteStepParameters.getAsgCanaryDeployFqn() + "."
            + OutcomeExpressionConstants.ASG_CANARY_DEPLOY_OUTCOME));

    if (!asgCanaryDeployOptionalOutput.isFound()) {
      return skipTaskRequestOrThrowException(ambiance);
    }

    if (StepUtils.isStepInRollbackSection(ambiance)
        && EmptyPredicate.isNotEmpty(asgCanaryDeleteStepParameters.getAsgCanaryDeleteFqn())) {
      OptionalSweepingOutput existingCanaryDeleteOutput = executionSweepingOutputService.resolveOptional(ambiance,
          RefObjectUtils.getSweepingOutputRefObject(asgCanaryDeleteStepParameters.getAsgCanaryDeleteFqn() + "."
              + OutcomeExpressionConstants.ASG_CANARY_DELETE_OUTCOME));
      if (existingCanaryDeleteOutput.isFound()) {
        return TaskRequest.newBuilder()
            .setSkipTaskRequest(
                SkipTaskRequest.newBuilder().setMessage(ASG_CANARY_DELETE_STEP_ALREADY_EXECUTED).build())
            .build();
      }
    }

    AsgCanaryDeployOutcome asgCanaryDeployOutcome = (AsgCanaryDeployOutcome) asgCanaryDeployOptionalOutput.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AsgCanaryDeleteRequest asgCanaryDeleteRequest =
        AsgCanaryDeleteRequest.builder()
            .accountId(accountId)
            .commandName(ASG_CANARY_DELETE_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .canaryAsgName(asgCanaryDeployOutcome.getAsg().getAutoScalingGroupName())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();

    return asgStepCommonHelper
        .queueAsgTask(stepElementParameters, asgCanaryDeleteRequest, ambiance,
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            AWS_ASG_CANARY_DELETE_TASK_NG)
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequestOrThrowException(Ambiance ambiance) {
    if (StepUtils.isStepInRollbackSection(ambiance)) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(ASG_CANARY_DELETE_STEP_SKIPPED).build())
          .build();
    }

    throw new InvalidRequestException(ASG_CANARY_DEPLOY_STEP_MISSING, USER);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
