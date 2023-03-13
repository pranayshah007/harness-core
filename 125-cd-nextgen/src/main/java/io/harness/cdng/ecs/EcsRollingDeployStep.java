/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingDeployOutcome;
import io.harness.cdng.ecs.beans.EcsS3FetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.request.EcsPrepareRollbackDataRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest.EcsRollingDeployRequestBuilder;
import io.harness.delegate.task.ecs.request.EcsTaskArnRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsTaskArnRollingDeployRequest.EcsTaskArnRollingDeployRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
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

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsRollingDeployStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_ROLLING_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String ECS_ROLLING_DEPLOY_COMMAND_NAME = "EcsRollingDeploy";
  private final String ECS_PREPARE_ROLLBACK_COMMAND_NAME = "EcsPrepareRollback";

  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return ecsStepCommonHelper.executeNextLinkRolling(
        this, ambiance, stepParameters, passThroughData, responseSupplier, ecsStepHelper);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof EcsGitFetchFailurePassThroughData) {
      return ecsStepCommonHelper.handleGitTaskFailure((EcsGitFetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof EcsS3FetchFailurePassThroughData) {
      return ecsStepCommonHelper.handleS3TaskFailure((EcsS3FetchFailurePassThroughData) passThroughData);
    } else if (passThroughData instanceof EcsStepExceptionPassThroughData) {
      return ecsStepCommonHelper.handleStepExceptionFailure((EcsStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    EcsExecutionPassThroughData ecsExecutionPassThroughData = (EcsExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = ecsExecutionPassThroughData.getInfrastructure();
    EcsRollingDeployResponse ecsRollingDeployResponse;
    try {
      ecsRollingDeployResponse = (EcsRollingDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(ecsRollingDeployResponse.getUnitProgressData().getUnitProgresses());
    if (ecsRollingDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsRollingDeployResponse, stepResponseBuilder).build();
    }

    EcsRollingDeployOutcome ecsRollingDeployOutcome =
        EcsRollingDeployOutcome.builder()
            .serviceName(ecsRollingDeployResponse.getEcsRollingDeployResult().getServiceName())
            .build();

    List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
        ecsRollingDeployResponse, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(ecsRollingDeployOutcome)
                         .build())
        .stepOutcome(stepOutcome)
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return ecsStepCommonHelper.startChainLink(this, ambiance, stepParameters, ecsStepHelper);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskChainResponse executeEcsTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      EcsStepExecutorParams ecsStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsRollingDeployStepParameters ecsRollingDeployStepParameters =
        (EcsRollingDeployStepParameters) stepElementParameters.getSpec();

    if (ecsStepExecutorParams.getEcsTaskDefinitionManifestContent() == null) {
      return executeEcsTaskWithTaskArn(
          stepElementParameters, ambiance, unitProgressData, ecsStepExecutorParams, executionPassThroughData);
    }

    EcsRollingDeployRequestBuilder ecsRollingDeployRequestBuilder =
        EcsRollingDeployRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_ROLLING_DEPLOY)
            .commandName(ECS_ROLLING_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList());

    if (ecsRollingDeployStepParameters.getSameAsAlreadyRunningInstances().getValue() != null) {
      ecsRollingDeployRequestBuilder.sameAsAlreadyRunningInstances(
          ecsRollingDeployStepParameters.getSameAsAlreadyRunningInstances().getValue().booleanValue());
    }
    if (ecsRollingDeployStepParameters.getForceNewDeployment().getValue() != null) {
      ecsRollingDeployRequestBuilder.forceNewDeployment(
          ecsRollingDeployStepParameters.getForceNewDeployment().getValue().booleanValue());
    }

    EcsRollingDeployRequest ecsRollingDeployRequest = ecsRollingDeployRequestBuilder.build();

    return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsRollingDeployRequest, ambiance,
        executionPassThroughData, true, TaskType.ECS_COMMAND_TASK_NG);
  }

  @Override
  public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = ecsPrepareRollbackDataPassThroughData.getInfrastructureOutcome();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsPrepareRollbackDataRequest ecsPrepareRollbackDataRequest =
        EcsPrepareRollbackDataRequest.builder()
            .commandName(ECS_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_PREPARE_ROLLBACK_DATA)
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .ecsServiceDefinitionManifestContent(
                ecsPrepareRollbackDataPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();
    return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsPrepareRollbackDataRequest, ambiance,
        ecsPrepareRollbackDataPassThroughData, false, TaskType.ECS_COMMAND_TASK_NG);
  }

  private TaskChainResponse executeEcsTaskWithTaskArn(StepElementParameters stepElementParameters, Ambiance ambiance,
      UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams,
      EcsExecutionPassThroughData executionPassThroughData) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsRollingDeployStepParameters ecsRollingDeployStepParameters =
        (EcsRollingDeployStepParameters) stepElementParameters.getSpec();
    EcsTaskArnRollingDeployRequestBuilder ecsTaskArnRollingDeployRequestBuilder =
        EcsTaskArnRollingDeployRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_TASK_ARN_ROLLING_DEPLOY)
            .commandName(ECS_ROLLING_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionArn(ecsStepCommonHelper.getTaskDefinitionArn(ambiance))
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .forceNewDeployment(true);

    if (ecsRollingDeployStepParameters.getSameAsAlreadyRunningInstances().getValue() != null) {
      ecsTaskArnRollingDeployRequestBuilder.sameAsAlreadyRunningInstances(
          ecsRollingDeployStepParameters.getSameAsAlreadyRunningInstances().getValue().booleanValue());
    }

    return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsTaskArnRollingDeployRequestBuilder.build(),
        ambiance, executionPassThroughData, true, TaskType.ECS_TASK_ARN_ROLLING_DEPLOY_NG);
  }
}
