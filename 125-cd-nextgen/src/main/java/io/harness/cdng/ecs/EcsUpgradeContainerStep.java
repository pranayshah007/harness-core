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
import io.harness.cdng.ecs.beans.EcsBasicPrepareDeployDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsUpgradeContainerOutcome;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsUpgradeContainerServiceData;
import io.harness.delegate.task.ecs.request.EcsUpgradeContainerRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsUpgradeContainerResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
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
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class EcsUpgradeContainerStep extends CdTaskExecutable<EcsCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_UPGRADE_CONTAINER.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private InstanceInfoService instanceInfoService;

  public static final String ECS_UPGRADE_CONTAINER_COMMAND_NAME = "EcsUpgradeContainer";
  public static final String ECS_SERVICE_SETUP_STEP_MISSING = "Ecs Service Setup step is not configured.";
  public static final String ECS_SERVICE_SETUP_STEP_INCOMPLETE = "Ecs Service Setup step is not completed.";

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepBaseParameters stepParameters, ThrowingSupplier<EcsCommandResponse> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      EcsUpgradeContainerResponse upgradeContainerResponse = (EcsUpgradeContainerResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(upgradeContainerResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, upgradeContainerResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing ecs upgrade container response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(Ambiance ambiance, EcsUpgradeContainerResponse upgradeContainerResponse,
      StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (upgradeContainerResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder()
                               .setErrorMessage(ecsStepCommonHelper.getErrorMessage(upgradeContainerResponse))
                               .build())
              .build();
    } else {
      EcsUpgradeContainerOutcome upgradeContainerOutcome =
          EcsUpgradeContainerOutcome.builder()
              .oldService(upgradeContainerResponse.getDeployData().getOldServiceData().getServiceName())
              .newService(upgradeContainerResponse.getDeployData().getNewServiceData().getServiceName())
              .build();

      List<ServerInstanceInfo> serverInstanceInfos = ecsStepCommonHelper.getServerInstanceInfos(
          upgradeContainerResponse, upgradeContainerResponse.getInfrastructureKey());

      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(upgradeContainerOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsUpgradeContainerStepParameters upgradeContainerStepParameters =
        (EcsUpgradeContainerStepParameters) stepParameters.getSpec();

    if (EmptyPredicate.isEmpty(upgradeContainerStepParameters.getEcsServiceSetupFqn())) {
      return skipTaskRequest(ECS_SERVICE_SETUP_STEP_MISSING);
    }

    OptionalSweepingOutput prepareDeployDataOptional = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(upgradeContainerStepParameters.getEcsServiceSetupFqn() + "."
            + OutcomeExpressionConstants.ECS_BASIC_PREPARE_DEPLOY_DATA_OUTCOME));

    if (!prepareDeployDataOptional.isFound()) {
      return skipTaskRequest(ECS_SERVICE_SETUP_STEP_INCOMPLETE);
    }

    EcsBasicPrepareDeployDataOutcome prepareDeployData =
        (EcsBasicPrepareDeployDataOutcome) prepareDeployDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    EcsUpgradeContainerRequest upgradeContainerRequest =
        EcsUpgradeContainerRequest.builder()
            .accountId(accountId)
            .commandType(EcsCommandTypeNG.ECS_UPGRADE_CONTAINER)
            .commandName(ECS_UPGRADE_CONTAINER_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .infraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMillis(CDStepHelper.getTimeoutInMillis(stepParameters))
            .resizeStrategy(prepareDeployData.getResizeStrategy())
            .scalableTargetManifestContentList(prepareDeployData.getScalableTargetManifestContentList())
            .scalingPolicyManifestContentList(prepareDeployData.getScalingPolicyManifestContentList())
            .oldServiceData(EcsUpgradeContainerServiceData.builder()
                                .serviceName(prepareDeployData.getCurrentServiceName())
                                .thresholdInstanceCount(prepareDeployData.getCurrentServiceInstanceCount())
                                .instanceCount(ParameterFieldHelper.getParameterFieldValue(
                                    upgradeContainerStepParameters.getDownsizeOldServiceInstanceCount()))
                                .instanceUnitType(upgradeContainerStepParameters.getDownsizeOldServiceInstanceUnit())
                                .build())
            .newServiceData(EcsUpgradeContainerServiceData.builder()
                                .serviceName(prepareDeployData.getServiceName())
                                .thresholdInstanceCount(prepareDeployData.getThresholdInstanceCount())
                                .instanceCount(ParameterFieldHelper.getParameterFieldValue(
                                    upgradeContainerStepParameters.getNewServiceInstanceCount()))
                                .instanceUnitType(upgradeContainerStepParameters.getNewServiceInstanceUnit())
                                .build())
            .firstTimeDeployment(prepareDeployData.isFirstDeployment())
            .build();
    return ecsStepCommonHelper
        .queueEcsTask(stepParameters, upgradeContainerRequest, ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_UPGRADE_CONTAINER_TASK_NG)
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequest(String message) {
    return TaskRequest.newBuilder()
        .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(message).build())
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
}
