/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.cdng.aws.asg.AsgStepCommonHelper.getLoadBalancers;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.AWS_ASG_SHIFT_TRAFFIC_TASK_NG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficRequest;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResponse;
import io.harness.delegate.task.aws.asg.AsgShiftTrafficResult;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
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
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgShiftTrafficStep extends CdTaskExecutable<AsgCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_SHIFT_TRAFFIC.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final String ASG_BLUE_GREEN_DEPLOY_STEP_MISSING = "Blue Green Deploy step is not configured.";
  public static final String COMMAND_NAME = "AsgShiftTraffic";

  @Inject private AsgStepCommonHelper asgStepCommonHelper;
  @Inject private AsgStepHelper asgStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private OutcomeService outcomeService;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // Nothing to validate
  }
  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepParameters, ThrowingSupplier<AsgCommandResponse> responseDataSupplier) throws Exception {
    try {
      AsgShiftTrafficResponse asgShiftTrafficResponse = (AsgShiftTrafficResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(asgShiftTrafficResponse.getUnitProgressData().getUnitProgresses());

      return generateStepResponse(ambiance, asgShiftTrafficResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing asg shift traffic response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AsgShiftTrafficStepParameters asgShiftTrafficStepParameters =
        (AsgShiftTrafficStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(asgShiftTrafficStepParameters.getAsgBlueGreenDeployFqn())) {
      throw new InvalidRequestException(ASG_BLUE_GREEN_DEPLOY_STEP_MISSING, USER);
    }

    OptionalSweepingOutput asgBlueGreenPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(asgShiftTrafficStepParameters.getAsgBlueGreenDeployFqn() + "."
                + OutcomeExpressionConstants.ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME));

    OptionalSweepingOutput asgBlueGreenDeployDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(asgShiftTrafficStepParameters.getAsgBlueGreenDeployFqn() + "."
            + OutcomeExpressionConstants.ASG_BLUE_GREEN_DEPLOY_OUTCOME));

    if (!asgBlueGreenPrepareRollbackDataOptional.isFound() || !asgBlueGreenDeployDataOptional.isFound()) {
      throw new InvalidRequestException(ASG_BLUE_GREEN_DEPLOY_STEP_MISSING, USER);
    }

    AsgBlueGreenPrepareRollbackDataOutcome asgBlueGreenPrepareRollbackDataOutcome =
        (AsgBlueGreenPrepareRollbackDataOutcome) asgBlueGreenPrepareRollbackDataOptional.getOutput();

    AsgBlueGreenDeployOutcome asgBlueGreenDeployDataOutcome =
        (AsgBlueGreenDeployOutcome) asgBlueGreenDeployDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome =
        asgStepCommonHelper.getInfrastructureOutcomeWithUpdatedExpressions(ambiance);

    List<AsgLoadBalancerConfig> loadBalancers = getLoadBalancers(asgBlueGreenPrepareRollbackDataOutcome, true);

    if (isEmpty(loadBalancers)) {
      throw new InvalidRequestException("No loadBalancer provided with only prodListener defined", USER);
    }

    AsgShiftTrafficRequest request =
        AsgShiftTrafficRequest.builder()
            .accountId(accountId)
            .commandName(COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .loadBalancers(loadBalancers)
            .prodAsgName(asgBlueGreenPrepareRollbackDataOutcome.getProdAsgName())
            .stageAsgName(asgBlueGreenDeployDataOutcome.getStageAsg().getAutoScalingGroupName())
            .weight(getParameterFieldValue(asgShiftTrafficStepParameters.getWeight()))
            .downsizeOldAsg(
                ParameterFieldHelper.getBooleanParameterFieldValue(asgShiftTrafficStepParameters.getDownsizeOldAsg()))
            .build();

    return asgStepCommonHelper
        .queueAsgTask(stepParameters, request, ambiance,
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            AWS_ASG_SHIFT_TRAFFIC_TASK_NG)
        .getTaskRequest();
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, AsgShiftTrafficResponse asgShiftTrafficResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (asgShiftTrafficResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(asgStepCommonHelper.getErrorMessage(asgShiftTrafficResponse))
                                          .build())
                         .build();
    } else {
      AsgShiftTrafficResult asgShiftTrafficResult = asgShiftTrafficResponse.getResult();

      AsgShiftTrafficOutcome asgShiftTrafficOutcome =
          AsgShiftTrafficOutcome.builder()
              .trafficShifted(asgShiftTrafficResult.isTrafficShifted())
              .stageAsg(asgShiftTrafficResult.getStageAutoScalingGroupContainer())
              .prodAsg(asgShiftTrafficResult.getProdAutoScalingGroupContainer())
              .build();

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ASG_SHIFT_TRAFFIC_OUTCOME,
          asgShiftTrafficOutcome, StepOutcomeGroup.STEP.name());

      // TODO instance sync

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(asgShiftTrafficOutcome)
                                          .build())
                         .build();
    }

    return stepResponse;
  }
}
