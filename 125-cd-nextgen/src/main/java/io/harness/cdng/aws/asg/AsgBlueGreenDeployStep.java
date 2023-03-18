/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static software.wings.beans.TaskType.AWS_ASG_BLUE_GREEN_DEPLOY_TASK_NG;
import static software.wings.beans.TaskType.AWS_ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_TASK_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResult;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.ExceptionUtils;
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

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgBlueGreenDeployStep extends TaskChainExecutableWithRollbackAndRbac implements AsgStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_BLUE_GREEN_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final String ASG_BLUE_GREEN_DEPLOY_COMMAND_NAME = "AsgBlueGreenDeploy";
  private static final String ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_COMMAND_NAME = "AsgBlueGreenPrepareRollbackData";

  @Inject private AsgStepCommonHelper asgStepCommonHelper;
  @Inject private AsgStepHelper asgStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;

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
    DelegateResponseData delegateResponseData = (DelegateResponseData) responseSupplier.get();
    if (delegateResponseData instanceof GitFetchResponse) {
      AsgExecutionPassThroughData executionPassThroughData = (AsgExecutionPassThroughData) passThroughData;

      Supplier<TaskChainResponse> executeAsgPrepareRollbackDataTaskSupplier = () -> {
        Map<String, List<String>> asgStoreManifestsContent =
            asgStepCommonHelper.buildManifestContentMap(executionPassThroughData.getAsgManifestFetchData(), ambiance);
        AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData =
            AsgPrepareRollbackDataPassThroughData.builder()
                .infrastructureOutcome(executionPassThroughData.getInfrastructure())
                .asgStoreManifestsContent(asgStoreManifestsContent)
                .build();

        return executeAsgPrepareRollbackDataTask(ambiance, stepParameters, asgPrepareRollbackDataPassThroughData,
            executionPassThroughData.getLastActiveUnitProgressData());
      };

      return asgStepCommonHelper.chainFetchGitTaskUntilAllGitManifestsFetched(executionPassThroughData,
          delegateResponseData, ambiance, stepParameters, executeAsgPrepareRollbackDataTaskSupplier);
    }

    return handleRollbackDataResponse(ambiance, stepParameters, passThroughData, delegateResponseData);
  }

  @Override
  public TaskChainResponse executeAsgTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      AsgExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      AsgStepExecutorParams asgStepExecutorParams) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();

    AsgBlueGreenExecutionPassThroughData asgBlueGreenExecutionPassThroughData =
        (AsgBlueGreenExecutionPassThroughData) executionPassThroughData;

    AsgBlueGreenDeployStepParameters asgBlueGreenDeployStepParameters =
        (AsgBlueGreenDeployStepParameters) stepElementParameters.getSpec();

    String amiImageId = asgStepCommonHelper.getAmiImageId(ambiance);

    AsgBlueGreenDeployRequest asgBlueGreenDeployRequest =
        AsgBlueGreenDeployRequest.builder()
            .commandName(ASG_BLUE_GREEN_DEPLOY_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .asgStoreManifestsContent(asgStepExecutorParams.getAsgStoreManifestsContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgName(asgBlueGreenExecutionPassThroughData.getAsgName())
            .firstDeployment(asgBlueGreenExecutionPassThroughData.isFirstDeployment())
            .asgLoadBalancerConfig(asgBlueGreenExecutionPassThroughData.getLoadBalancerConfig())
            .useAlreadyRunningInstances(ParameterFieldHelper.getBooleanParameterFieldValue(
                asgBlueGreenDeployStepParameters.getUseAlreadyRunningInstances()))
            .amiImageId(amiImageId)
            .build();

    return asgStepCommonHelper.queueAsgTask(stepElementParameters, asgBlueGreenDeployRequest, ambiance,
        executionPassThroughData, true, AWS_ASG_BLUE_GREEN_DEPLOY_TASK_NG);
  }

  @Override
  public TaskChainResponse executeAsgPrepareRollbackDataTask(Ambiance ambiance,
      StepElementParameters stepElementParameters,
      AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = asgPrepareRollbackDataPassThroughData.getInfrastructureOutcome();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AsgBlueGreenDeployStepParameters ecsBlueGreenCreateServiceStepParameters =
        (AsgBlueGreenDeployStepParameters) stepElementParameters.getSpec();

    AsgLoadBalancerConfig asgLoadBalancerConfig =
        AsgLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenCreateServiceStepParameters.getLoadBalancer().getValue())
            .stageListenerArn(ecsBlueGreenCreateServiceStepParameters.getStageListener().getValue())
            .stageListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getStageListenerRuleArn().getValue())
            .prodListenerArn(ecsBlueGreenCreateServiceStepParameters.getProdListener().getValue())
            .prodListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getProdListenerRuleArn().getValue())
            .build();

    AsgBlueGreenPrepareRollbackDataRequest asgBlueGreenPrepareRollbackDataRequest =
        AsgBlueGreenPrepareRollbackDataRequest.builder()
            .commandName(ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .asgStoreManifestsContent(asgPrepareRollbackDataPassThroughData.getAsgStoreManifestsContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgLoadBalancerConfig(asgLoadBalancerConfig)
            .build();

    return asgStepCommonHelper.queueAsgTask(stepElementParameters, asgBlueGreenPrepareRollbackDataRequest, ambiance,
        asgPrepareRollbackDataPassThroughData, false, AWS_ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_TASK_NG);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof AsgStepExceptionPassThroughData) {
      return asgStepCommonHelper.handleStepExceptionFailure((AsgStepExceptionPassThroughData) passThroughData);
    }

    AsgExecutionPassThroughData asgExecutionPassThroughData = (AsgExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructureOutcome = asgExecutionPassThroughData.getInfrastructure();

    AsgBlueGreenDeployResponse asgBlueGreenDeployResponse;
    try {
      asgBlueGreenDeployResponse = (AsgBlueGreenDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing asg task response: {}", e.getMessage(), e);
      return asgStepCommonHelper.handleTaskException(ambiance, asgExecutionPassThroughData, e);
    }

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(asgBlueGreenDeployResponse.getUnitProgressData().getUnitProgresses());

    if (asgBlueGreenDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return AsgStepCommonHelper.getFailureResponseBuilder(asgBlueGreenDeployResponse, stepResponseBuilder).build();
    }

    AsgBlueGreenDeployResult asgBlueGreenDeployResult = asgBlueGreenDeployResponse.getAsgBlueGreenDeployResult();

    AsgBlueGreenDeployOutcome asgBlueGreenDeployOutcome =
        AsgBlueGreenDeployOutcome.builder()
            .stageAsg(asgBlueGreenDeployResult.getStageAutoScalingGroupContainer())
            .prodAsg(asgBlueGreenDeployResult.getProdAutoScalingGroupContainer())
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ASG_BLUE_GREEN_DEPLOY_OUTCOME,
        asgBlueGreenDeployOutcome, StepOutcomeGroup.STEP.name());

    List<ServerInstanceInfo> serverInstanceInfos = asgStepCommonHelper.getServerInstanceInfos(
        asgBlueGreenDeployResponse, infrastructureOutcome.getInfrastructureKey(),
        asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance).getRegion());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(asgBlueGreenDeployOutcome)
                         .build())
        .stepOutcome(stepOutcome)
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  public TaskChainResponse handleRollbackDataResponse(Ambiance ambiance, StepElementParameters stepElementParameters,
      PassThroughData passThroughData, DelegateResponseData delegateResponseData) {
    AsgBlueGreenPrepareRollbackDataResponse asgPrepareRollbackDataResponse =
        (AsgBlueGreenPrepareRollbackDataResponse) delegateResponseData;
    AsgPrepareRollbackDataPassThroughData asgStepPassThroughData =
        (AsgPrepareRollbackDataPassThroughData) passThroughData;

    try {
      if (asgPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        AsgStepExceptionPassThroughData asgStepExceptionPassThroughData =
            AsgStepExceptionPassThroughData.builder()
                .errorMessage(asgPrepareRollbackDataResponse.getErrorMessage())
                .unitProgressData(asgPrepareRollbackDataResponse.getUnitProgressData())
                .build();
        return TaskChainResponse.builder().passThroughData(asgStepExceptionPassThroughData).chainEnd(true).build();
      }

      AsgBlueGreenPrepareRollbackDataResult asgPrepareRollbackDataResult =
          asgPrepareRollbackDataResponse.getAsgBlueGreenPrepareRollbackDataResult();
      AsgLoadBalancerConfig loadBalancerConfig = asgPrepareRollbackDataResult.getAsgLoadBalancerConfig();

      AsgBlueGreenPrepareRollbackDataOutcome asgBlueGreenPrepareRollbackDataOutcome =
          AsgBlueGreenPrepareRollbackDataOutcome.builder()
              .prodAsgName(asgPrepareRollbackDataResult.getProdAsgName())
              .asgName(asgPrepareRollbackDataResult.getAsgName())
              .prodAsgManifestDataForRollback(asgPrepareRollbackDataResult.getProdAsgManifestsDataForRollback())
              .stageAsgManifestDataForRollback(asgPrepareRollbackDataResult.getStageAsgManifestsDataForRollback())
              .loadBalancer(loadBalancerConfig.getLoadBalancer())
              .stageListenerArn(loadBalancerConfig.getStageListenerArn())
              .stageListenerRuleArn(loadBalancerConfig.getStageListenerRuleArn())
              .stageTargetGroupArnsList(loadBalancerConfig.getStageTargetGroupArnsList())
              .prodListenerArn(loadBalancerConfig.getProdListenerArn())
              .prodListenerRuleArn(loadBalancerConfig.getProdListenerRuleArn())
              .prodTargetGroupArnsList(loadBalancerConfig.getProdTargetGroupArnsList())
              .build();

      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME,
          asgBlueGreenPrepareRollbackDataOutcome, StepOutcomeGroup.STEP.name());

      AsgBlueGreenExecutionPassThroughData asgExecutionPassThroughData =
          AsgBlueGreenExecutionPassThroughData.blueGreenBuilder()
              .infrastructure(asgStepPassThroughData.getInfrastructureOutcome())
              .lastActiveUnitProgressData(asgPrepareRollbackDataResponse.getUnitProgressData())
              .asgName(asgPrepareRollbackDataResult.getAsgName())
              .loadBalancerConfig(loadBalancerConfig)
              .firstDeployment(asgPrepareRollbackDataResult.getProdAsgName() == null)
              .build();

      Map<String, List<String>> asgStoreManifestsContent = asgStepPassThroughData.getAsgStoreManifestsContent();

      AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
                                                        .shouldOpenFetchFilesLogStream(false)
                                                        .asgStoreManifestsContent(asgStoreManifestsContent)
                                                        .build();

      return executeAsgTask(ambiance, stepElementParameters, asgExecutionPassThroughData,
          asgPrepareRollbackDataResponse.getUnitProgressData(), asgStepExecutorParams);

    } catch (Exception e) {
      log.error("Error while processing asg task: {}", e.getMessage(), e);
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(AsgStepExceptionPassThroughData.builder()
                               .unitProgressData(asgPrepareRollbackDataResponse.getUnitProgressData())
                               .errorMessage(ExceptionUtils.getMessage(e))
                               .build())
          .build();
    }
  }
}
