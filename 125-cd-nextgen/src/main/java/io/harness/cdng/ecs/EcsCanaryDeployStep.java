/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.cdng.ecs.beans.EcsCanaryDeleteDataOutcome;
import io.harness.cdng.ecs.beans.EcsCanaryDeployOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
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
import io.harness.delegate.task.ecs.request.EcsCanaryDeployRequest;
import io.harness.delegate.task.ecs.request.EcsTaskArnCanaryDeployRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsCanaryDeployStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_CANARY_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String ECS_CANARY_DEPLOY_COMMAND_NAME = "EcsCanaryDeploy";

  private static final String canarySuffix = "Canary";

  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
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
    return ecsStepCommonHelper.executeNextLinkCanary(
        this, ambiance, stepParameters, passThroughData, responseSupplier, ecsStepHelper);
  }

  @Override
  public TaskChainResponse executeEcsTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      EcsStepExecutorParams ecsStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    TaskChainResponse response;

    if (ecsStepExecutorParams.getEcsTaskDefinitionManifestContent() == null) {
      response = executeEcsTaskWithTaskArn(
          stepElementParameters, ambiance, unitProgressData, ecsStepExecutorParams, executionPassThroughData);
    } else {
      EcsCanaryDeployRequest ecsCanaryDeployRequest =
          EcsCanaryDeployRequest.builder()
              .accountId(accountId)
              .ecsCommandType(EcsCommandTypeNG.ECS_CANARY_DEPLOY)
              .commandName(ECS_CANARY_DEPLOY_COMMAND_NAME)
              .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
              .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
              .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
              .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
              .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
              .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
              .desiredCountOverride(1l)
              .ecsServiceNameSuffix(canarySuffix)
              .build();

      response = ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsCanaryDeployRequest, ambiance,
          executionPassThroughData, true, TaskType.ECS_COMMAND_TASK_NG);
    }

    EcsCanaryDeleteDataOutcome ecsCanaryDeleteDataOutcome =
        EcsCanaryDeleteDataOutcome.builder()
            .ecsServiceNameSuffix(canarySuffix)
            .createServiceRequestBuilderString(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_CANARY_DELETE_DATA_OUTCOME,
        ecsCanaryDeleteDataOutcome, StepOutcomeGroup.STEP.name());

    return response;
  }

  @Override
  public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData) {
    // nothing to prepare
    return null;
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
    EcsCanaryDeployResponse ecsCanaryDeployResponse;
    try {
      ecsCanaryDeployResponse = (EcsCanaryDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(ecsCanaryDeployResponse.getUnitProgressData().getUnitProgresses());
    if (ecsCanaryDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsCanaryDeployResponse, stepResponseBuilder).build();
    }

    EcsCanaryDeployOutcome ecsCanaryDeployOutcome =
        EcsCanaryDeployOutcome.builder()
            .canaryServiceName(ecsCanaryDeployResponse.getEcsCanaryDeployResult().getCanaryServiceName())
            .build();

    List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
        ecsCanaryDeployResponse, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_CANARY_DEPLOY_OUTCOME,
        ecsCanaryDeployOutcome, StepOutcomeGroup.STEP.name());

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(ecsCanaryDeployOutcome)
                         .build())
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

  private TaskChainResponse executeEcsTaskWithTaskArn(StepElementParameters stepElementParameters, Ambiance ambiance,
      UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams,
      EcsExecutionPassThroughData executionPassThroughData) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsTaskArnCanaryDeployRequest ecsTaskArnCanaryDeployRequest =
        EcsTaskArnCanaryDeployRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_TASK_ARN_CANARY_DEPLOY)
            .commandName(ECS_CANARY_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionArn(ecsStepCommonHelper.getTaskDefinitionArn(ambiance))
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .desiredCountOverride(1l)
            .ecsServiceNameSuffix(canarySuffix)
            .build();

    return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsTaskArnCanaryDeployRequest, ambiance,
        executionPassThroughData, true, TaskType.ECS_TASK_ARN_CANARY_DEPLOY_NG);
  }
}
