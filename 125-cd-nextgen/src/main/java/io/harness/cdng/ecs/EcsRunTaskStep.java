/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.account.services.AccountService;
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
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.request.EcsRunTaskArnRequest;
import io.harness.delegate.task.ecs.request.EcsRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
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
public class EcsRunTaskStep extends TaskChainExecutableWithRollbackAndRbac implements EcsStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ECS_RUN_TASK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String ECS_RUN_TASK_COMMAND_NAME = "EcsRunTask";
  public static final String ECS_RUN_TASK_MISSING = "Run Task step is not configured.";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private EcsStepCommonHelper ecsStepCommonHelper;
  @Inject private EcsStepHelperImpl ecsStepHelper;
  @Inject private AccountService accountService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return ecsStepCommonHelper.executeNextLinkRunTask(
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

    EcsRunTaskResponse ecsRunTaskResponse;

    try {
      ecsRunTaskResponse = (EcsRunTaskResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing ecs task response: {}", e.getMessage(), e);
      return ecsStepCommonHelper.handleTaskException(ambiance, ecsExecutionPassThroughData, e);
    }

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(ecsRunTaskResponse.getUnitProgressData().getUnitProgresses());

    if (ecsRunTaskResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return EcsStepCommonHelper.getFailureResponseBuilder(ecsRunTaskResponse, stepResponseBuilder).build();
    }

    List<ServerInstanceInfo> serverInstanceInfos =
        ecsStepCommonHelper.getServerInstanceInfos(ecsRunTaskResponse, infrastructureOutcome.getInfrastructureKey());

    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    return stepResponseBuilder.status(Status.SUCCEEDED).stepOutcome(stepOutcome).build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return ecsStepCommonHelper.startChainLinkEcsRunTask(this, ambiance, stepParameters, ecsStepHelper);
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

    EcsRunTaskStepParameters ecsRunTaskStepParameters = (EcsRunTaskStepParameters) stepElementParameters.getSpec();

    if (ecsRunTaskStepParameters.getTaskDefinitionArn() == null
        || ecsRunTaskStepParameters.getTaskDefinitionArn().getValue() == null) {
      EcsRunTaskRequest ecsRunTaskRequest =
          EcsRunTaskRequest.builder()
              .accountId(accountId)
              .ecsCommandType(EcsCommandTypeNG.ECS_RUN_TASK)
              .commandName(ECS_RUN_TASK_COMMAND_NAME)
              .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
              .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
              .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
              .ecsRunTaskRequestDefinitionManifestContent(
                  ecsStepExecutorParams.getEcsRunTaskRequestDefinitionManifestContent())
              .skipSteadyStateCheck(ecsRunTaskStepParameters.getSkipSteadyStateCheck().getValue())
              .build();
      return ecsStepCommonHelper.queueEcsTask(stepElementParameters, ecsRunTaskRequest, ambiance,
          executionPassThroughData, true, TaskType.ECS_COMMAND_TASK_NG);
    } else {
      EcsRunTaskArnRequest ecsRunTaskArnRequest =
          EcsRunTaskArnRequest.builder()
              .accountId(accountId)
              .ecsCommandType(EcsCommandTypeNG.ECS_RUN_TASK_ARN)
              .commandName(ECS_RUN_TASK_COMMAND_NAME)
              .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
              .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
              .ecsTaskDefinition(ecsRunTaskStepParameters.getTaskDefinitionArn().getValue())
              .ecsRunTaskRequestDefinitionManifestContent(
                  ecsStepExecutorParams.getEcsRunTaskRequestDefinitionManifestContent())
              .skipSteadyStateCheck(ecsRunTaskStepParameters.getSkipSteadyStateCheck().getValue())
              .build();
      return ecsStepCommonHelper.queueEcsRunTaskArnTask(
          stepElementParameters, ecsRunTaskArnRequest, ambiance, executionPassThroughData, true);
    }
  }

  @Override
  public TaskChainResponse executeEcsPrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData, UnitProgressData unitProgressData) {
    return null;
  }
}
