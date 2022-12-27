/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static software.wings.beans.TaskType.AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG;
import static software.wings.beans.TaskType.AWS_ASG_ROLLING_DEPLOY_TASK_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgRollingDeployStep extends TaskChainExecutableWithRollbackAndRbac implements AsgStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_ROLLING_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final String ASG_ROLLING_DEPLOY_COMMAND_NAME = "AsgRollingDeploy";
  private static final String ASG_ROLLING_PREPARE_ROLLBACK_DATA_COMMAND_NAME = "AsgRollingPrepareRollbackData";

  @Inject private AsgStepCommonHelper asgStepCommonHelper;
  @Inject private AsgStepHelper asgStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return asgStepCommonHelper.startChainLink(this, ambiance, stepParameters);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return asgStepCommonHelper.executeNextLinkRolling(
        this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public TaskChainResponse executeAsgTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      AsgExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      AsgStepExecutorParams asgStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AsgRollingDeployStepParameters asgSpecParameters = (AsgRollingDeployStepParameters) stepElementParameters.getSpec();

    AsgRollingDeployRequest asgRollingDeployRequest =
        AsgRollingDeployRequest.builder()
            .commandName(ASG_ROLLING_DEPLOY_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgStoreManifestsContent(asgStepExecutorParams.getAsgStoreManifestsContent())
            .skipMatching(asgSpecParameters.getSkipMatching().getValue())
            .useAlreadyRunningInstances(asgSpecParameters.getUseAlreadyRunningInstances().getValue())
            .instanceWarmup(asgSpecParameters.getInstanceWarmup().getValue())
            .minimumHealthyPercentage(asgSpecParameters.getMinimumHealthyPercentage().getValue())
            .build();

    return asgStepCommonHelper.queueAsgTask(stepElementParameters, asgRollingDeployRequest, ambiance,
        executionPassThroughData, true, AWS_ASG_ROLLING_DEPLOY_TASK_NG);
  }

  @Override
  public TaskChainResponse executeAsgPrepareRollbackDataTask(Ambiance ambiance,
      StepElementParameters stepElementParameters,
      AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = asgPrepareRollbackDataPassThroughData.getInfrastructureOutcome();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    AsgPrepareRollbackDataRequest asgPrepareRollbackDataRequest =
        AsgPrepareRollbackDataRequest.builder()
            .commandName(ASG_ROLLING_PREPARE_ROLLBACK_DATA_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .asgStoreManifestsContent(asgPrepareRollbackDataPassThroughData.getAsgStoreManifestsContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();
    return asgStepCommonHelper.queueAsgTask(stepElementParameters, asgPrepareRollbackDataRequest, ambiance,
        asgPrepareRollbackDataPassThroughData, false, AWS_ASG_PREPARE_ROLLBACK_DATA_TASK_NG);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof AsgStepExceptionPassThroughData) {
      return asgStepCommonHelper.handleStepExceptionFailure((AsgStepExceptionPassThroughData) passThroughData);
    }

    AsgExecutionPassThroughData asgExecutionPassThroughData = (AsgExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = asgExecutionPassThroughData.getInfrastructure();
    AsgRollingDeployResponse asgRollingDeployResponse;
    try {
      asgRollingDeployResponse = (AsgRollingDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing asg task response: {}", e.getMessage(), e);
      return asgStepCommonHelper.handleTaskException(ambiance, asgExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(asgRollingDeployResponse.getUnitProgressData().getUnitProgresses());

    if (asgRollingDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return AsgStepCommonHelper.getFailureResponseBuilder(asgRollingDeployResponse, stepResponseBuilder).build();
    }

    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
