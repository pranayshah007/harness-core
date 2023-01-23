/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static software.wings.beans.TaskType.AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgLoadBalancerConfig;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
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
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgBlueGreenRollbackStep extends CdTaskExecutable<AsgCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_BLUE_GREEN_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String ASG_ROLLING_ROLLBACK_COMMAND_NAME = "AsgBlueGreenRollback";

  public static final String ASG_BLUE_GREEN_DEPLOY_STEP_MISSING = "Blue Green Deploy step is not configured.";

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
    return null;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    AsgBlueGreenRollbackStepParameters asgBlueGreenDeployStepParameters =
        (AsgBlueGreenRollbackStepParameters) stepElementParameters.getSpec();

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    if (EmptyPredicate.isEmpty(asgBlueGreenDeployStepParameters.getAsgBlueGreenDeplyFnq())) {
      return skipTaskRequest(ambiance, ASG_BLUE_GREEN_DEPLOY_STEP_MISSING);
    }

    OptionalSweepingOutput asgBlueGreenPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(asgBlueGreenDeployStepParameters.getAsgBlueGreenDeplyFnq() + "."
                + OutcomeExpressionConstants.ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME));

    OptionalSweepingOutput asgBlueGreenDeployOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(asgBlueGreenDeployStepParameters.getAsgBlueGreenDeplyFnq() + "."
            + OutcomeExpressionConstants.ASG_BLUE_GREEN_DEPLOY_OUTCOME));

    if (!asgBlueGreenPrepareRollbackDataOptional.isFound() || !asgBlueGreenDeployOptional.isFound()) {
      return skipTaskRequest(ambiance, ASG_BLUE_GREEN_DEPLOY_STEP_MISSING);
    }

    AsgBlueGreenPrepareRollbackDataOutcome asgBlueGreenPrepareRollbackDataOutcome =
        (AsgBlueGreenPrepareRollbackDataOutcome) asgBlueGreenPrepareRollbackDataOptional.getOutput();

    AsgBlueGreenDeployOutcome asgBlueGreenDeployOutcome =
        (AsgBlueGreenDeployOutcome) asgBlueGreenDeployOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AsgLoadBalancerConfig asgLoadBalancerConfig =
        AsgLoadBalancerConfig.builder()
            .loadBalancer(asgBlueGreenPrepareRollbackDataOutcome.getLoadBalancer())
            .stageListenerArn(asgBlueGreenPrepareRollbackDataOutcome.getStageListenerArn())
            .stageListenerRuleArn(asgBlueGreenPrepareRollbackDataOutcome.getStageListenerRuleArn())
            .prodListenerArn(asgBlueGreenPrepareRollbackDataOutcome.getProdListenerArn())
            .prodListenerRuleArn(asgBlueGreenPrepareRollbackDataOutcome.getProdListenerRuleArn())
            .build();

    AsgBlueGreenRollbackRequest asgBlueGreenRollbackRequest =
        AsgBlueGreenRollbackRequest.builder()
            .commandName(ASG_ROLLING_ROLLBACK_COMMAND_NAME)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .accountId(accountId)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgLoadBalancerConfig(asgLoadBalancerConfig)
            .prodAsgName(asgBlueGreenPrepareRollbackDataOutcome.getProdAsgName())
            .stageAsgName(asgBlueGreenDeployOutcome.getAsgName())
            .build();

    return asgStepCommonHelper
        .queueAsgTask(stepElementParameters, asgBlueGreenRollbackRequest, ambiance,
            AsgExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            AWS_ASG_BLUE_GREEN_ROLLBACK_TASK_NG)
        .getTaskRequest();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private TaskRequest skipTaskRequest(Ambiance ambiance, String message) {
    return TaskRequest.newBuilder()
        .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(message).build())
        .build();
  }
}
