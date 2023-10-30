/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsS3FetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsServiceSetupOutcome;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsResizeStrategy;
import io.harness.delegate.task.ecs.request.EcsBasicPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest.EcsServiceSetupRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsServiceSetupResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsServiceSetupStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_SERVICE_SETUP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  private final String ECS_SERVICE_SETUP_COMMAND_NAME = "EcsServiceSetup";
  private final String ECS_BASIC_PREPARE_ROLLBACK_COMMAND_NAME = "EcsBasicPrepareRollback";

  @Override
  public TaskChainResponse executeEcsTask(Ambiance ambiance, StepBaseParameters stepParameters,
      EcsExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      EcsStepExecutorParams ecsStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsServiceSetupStepParameters ecsServiceSetupStepParameters =
        (EcsServiceSetupStepParameters) stepParameters.getSpec();

    EcsResizeStrategy resizeStrategy = ecsServiceSetupStepParameters.getResizeStrategy();

    if (resizeStrategy == null) {
      resizeStrategy = EcsResizeStrategy.RESIZE_NEW_FIRST;
    }

    EcsServiceSetupRequestBuilder ecsServiceSetupRequestBuilder =
        EcsServiceSetupRequest.builder()
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_SERVICE_SETUP)
            .commandName(ECS_SERVICE_SETUP_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .infraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
            .taskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .serviceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .scalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .scalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .resizeStrategy(resizeStrategy)
            .newServiceName(ecsStepExecutorParams.getNewServiceName())
            .oldServiceName(ecsStepExecutorParams.getOldServiceName())
            .firstTimeDeployment(ecsStepExecutorParams.isFirstTimeDeployment());
    if (ecsStepExecutorParams.getEcsTaskDefinitionManifestContent() == null) {
      ecsServiceSetupRequestBuilder.taskDefinitionArn(ecsStepCommonHelper.getTaskDefinitionArn(ambiance));
      ecsServiceSetupRequestBuilder.useTaskDefinitionArn(true);
    }
    return ecsStepCommonHelper.queueEcsTask(stepParameters, ecsServiceSetupRequestBuilder.build(), ambiance,
        executionPassThroughData, true, TaskType.ECS_SERVICE_SETUP_TASK_NG);
  }

  @Override
  public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepBaseParameters stepParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = ecsStepPassThroughData.getInfrastructureOutcome();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBasicPrepareRollbackRequest ecsBasicPrepareRollbackRequest =
        EcsBasicPrepareRollbackRequest.builder()
            .commandName(ECS_BASIC_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_BASIC_PREPARE_ROLLBACK)
            .infraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .serviceDefinitionManifestContent(ecsStepPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
            .build();
    return ecsStepCommonHelper.queueEcsTask(stepParameters, ecsBasicPrepareRollbackRequest, ambiance,
        ecsStepPassThroughData, false, TaskType.ECS_BASIC_PREPARE_ROLLBACK_TASK_NG);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return ecsStepCommonHelper.executeNextLinkBasic(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
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
    EcsServiceSetupResponse ecsServiceSetupResponse;
    try {
      ecsServiceSetupResponse = (EcsServiceSetupResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(ecsServiceSetupResponse.getUnitProgressData().getUnitProgresses());
    if (ecsServiceSetupResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsServiceSetupResponse, stepResponseBuilder).build();
    }

    EcsServiceSetupOutcome ecsServiceSetupOutcome =
        EcsServiceSetupOutcome.builder()
            .oldService(ecsServiceSetupResponse.getDeployData().getOldServiceData().getServiceName())
            .newService(ecsServiceSetupResponse.getDeployData().getNewServiceData().getServiceName())
            .build();

    List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
        ecsServiceSetupResponse, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(ecsServiceSetupOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    return ecsStepCommonHelper.startChainLink(this, ambiance, stepParameters, ecsStepHelper);
  }
}
