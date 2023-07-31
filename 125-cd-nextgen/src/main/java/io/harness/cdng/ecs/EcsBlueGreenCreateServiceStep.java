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
import io.harness.cdng.ecs.beans.EcsBlueGreenCreateServiceDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsS3FetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsTaskArnBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
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
public class EcsBlueGreenCreateServiceStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_BLUE_GREEN_CREATE_SERVICE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private final String ECS_BLUE_GREEN__CREATE_SERVICE_COMMAND_NAME = "EcsBlueGreenCreateService";
  private final String ECS_BLUE_GREEN_PREPARE_ROLLBACK_COMMAND_NAME = "EcsBlueGreenPrepareRollback";

  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public TaskChainResponse executeEcsTask(Ambiance ambiance, StepElementParameters stepParameters,
      EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      EcsStepExecutorParams ecsStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsBlueGreenCreateServiceStepParameters ecsBlueGreenCreateServiceStepParameters =
        (EcsBlueGreenCreateServiceStepParameters) stepParameters.getSpec();

    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenCreateServiceStepParameters.getLoadBalancer().getValue())
            .prodListenerArn(ecsBlueGreenCreateServiceStepParameters.getProdListener().getValue())
            .prodListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getProdListenerRuleArn().getValue())
            .stageListenerArn(ecsBlueGreenCreateServiceStepParameters.getStageListener().getValue())
            .stageListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getStageListenerRuleArn().getValue())
            .prodTargetGroupArn(ecsStepExecutorParams.getProdTargetGroupArn())
            .stageTargetGroupArn(ecsStepExecutorParams.getStageTargetGroupArn())
            .build();

    if (ecsStepExecutorParams.getEcsTaskDefinitionManifestContent() == null) {
      return executeEcsTaskWithTaskArn(stepParameters, ambiance, unitProgressData, ecsStepExecutorParams,
          executionPassThroughData, ecsLoadBalancerConfig);
    }

    EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest =
        EcsBlueGreenCreateServiceRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_CREATE_SERVICE)
            .commandName(ECS_BLUE_GREEN__CREATE_SERVICE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .targetGroupArnKey(ecsStepExecutorParams.getTargetGroupArnKey())
            .build();

    return ecsStepCommonHelper.queueEcsTask(stepParameters, ecsBlueGreenCreateServiceRequest, ambiance,
        executionPassThroughData, true, TaskType.ECS_COMMAND_TASK_NG);
  }

  @Override
  public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = ecsStepPassThroughData.getInfrastructureOutcome();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBlueGreenCreateServiceStepParameters ecsBlueGreenCreateServiceStepParameters =
        (EcsBlueGreenCreateServiceStepParameters) stepParameters.getSpec();
    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenCreateServiceStepParameters.getLoadBalancer().getValue())
            .prodListenerArn(ecsBlueGreenCreateServiceStepParameters.getProdListener().getValue())
            .prodListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getProdListenerRuleArn().getValue())
            .stageListenerArn(ecsBlueGreenCreateServiceStepParameters.getStageListener().getValue())
            .stageListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getStageListenerRuleArn().getValue())
            .build();
    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        EcsBlueGreenPrepareRollbackRequest.builder()
            .commandName(ECS_BLUE_GREEN_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA)
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .ecsServiceDefinitionManifestContent(ecsStepPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    return ecsStepCommonHelper.queueEcsTask(stepParameters, ecsBlueGreenPrepareRollbackRequest, ambiance,
        ecsStepPassThroughData, false, TaskType.ECS_COMMAND_TASK_NG);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return ecsStepCommonHelper.executeNextLinkBlueGreen(
        this, ambiance, stepParameters, passThroughData, responseSupplier);
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
    EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse;
    try {
      ecsBlueGreenCreateServiceResponse = (EcsBlueGreenCreateServiceResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
        ecsBlueGreenCreateServiceResponse.getUnitProgressData().getUnitProgresses());
    if (ecsBlueGreenCreateServiceResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsBlueGreenCreateServiceResponse, stepResponseBuilder)
          .build();
    }

    EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
        ecsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult();
    EcsBlueGreenCreateServiceDataOutcome ecsBlueGreenCreateServiceDataOutcome =
        EcsBlueGreenCreateServiceDataOutcome.builder()
            .isNewServiceCreated(ecsBlueGreenCreateServiceResult.isNewServiceCreated())
            .serviceName(ecsBlueGreenCreateServiceResult.getServiceName())
            .targetGroupArn(ecsBlueGreenCreateServiceResult.getTargetGroupArn())
            .loadBalancer(ecsBlueGreenCreateServiceResult.getLoadBalancer())
            .listenerArn(ecsBlueGreenCreateServiceResult.getListenerArn())
            .listenerRuleArn(ecsBlueGreenCreateServiceResult.getListenerRuleArn())
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME,
        ecsBlueGreenCreateServiceDataOutcome, StepOutcomeGroup.STEP.name());

    List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
        ecsBlueGreenCreateServiceResponse, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(ecsBlueGreenCreateServiceDataOutcome)
                         .build())
        .stepOutcome(stepOutcome)
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return ecsStepCommonHelper.startChainLink(this, ambiance, stepParameters, ecsStepHelper);
  }

  private TaskChainResponse executeEcsTaskWithTaskArn(StepElementParameters stepElementParameters, Ambiance ambiance,
      UnitProgressData unitProgressData, EcsStepExecutorParams ecsStepExecutorParams,
      EcsExecutionPassThroughData executionPassThroughData, EcsLoadBalancerConfig ecsLoadBalancerConfig) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsTaskArnBlueGreenCreateServiceRequest ecsTaskArnBlueGreenCreateServiceRequest =
        EcsTaskArnBlueGreenCreateServiceRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE)
            .commandName(ECS_BLUE_GREEN__CREATE_SERVICE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionArn(ecsStepCommonHelper.getTaskDefinitionArn(ambiance))
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .targetGroupArnKey(ecsStepExecutorParams.getTargetGroupArnKey())
            .build();

    return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsTaskArnBlueGreenCreateServiceRequest, ambiance,
        executionPassThroughData, true, TaskType.ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE_NG);
  }
}
