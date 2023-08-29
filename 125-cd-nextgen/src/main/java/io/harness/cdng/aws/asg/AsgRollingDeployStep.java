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
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResponse;
import io.harness.delegate.task.aws.asg.AsgRollingDeployResult;
import io.harness.delegate.task.git.GitFetchResponse;
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

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    return asgStepCommonHelper.startChainLink(this, ambiance, stepParameters);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
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

    return asgStepCommonHelper.executeNextLinkRolling(
        this, ambiance, stepParameters, passThroughData, delegateResponseData);
  }

  @Override
  public TaskChainResponse executeAsgTask(Ambiance ambiance, StepBaseParameters stepElementParameters,
      AsgExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      AsgStepExecutorParams asgStepExecutorParams) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();

    AsgRollingDeployStepParameters asgSpecParameters = (AsgRollingDeployStepParameters) stepElementParameters.getSpec();

    String amiImageId = asgStepCommonHelper.getAmiImageId(ambiance);

    AsgRollingDeployRequest asgRollingDeployRequest =
        AsgRollingDeployRequest.builder()
            .commandName(ASG_ROLLING_DEPLOY_COMMAND_NAME)
            .asgInfraConfig(asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance))
            .accountId(accountId)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgStoreManifestsContent(asgStepExecutorParams.getAsgStoreManifestsContent())
            .skipMatching(ParameterFieldHelper.getBooleanParameterFieldValue(asgSpecParameters.getSkipMatching()))
            .useAlreadyRunningInstances(
                ParameterFieldHelper.getBooleanParameterFieldValue(asgSpecParameters.getUseAlreadyRunningInstances()))
            .instanceWarmup(ParameterFieldHelper.getIntegerParameterFieldValue(asgSpecParameters.getInstanceWarmup()))
            .minimumHealthyPercentage(
                ParameterFieldHelper.getIntegerParameterFieldValue(asgSpecParameters.getMinimumHealthyPercentage()))
            .amiImageId(amiImageId)
            .build();

    return asgStepCommonHelper.queueAsgTask(stepElementParameters, asgRollingDeployRequest, ambiance,
        executionPassThroughData, true, AWS_ASG_ROLLING_DEPLOY_TASK_NG);
  }

  @Override
  public TaskChainResponse executeAsgPrepareRollbackDataTask(Ambiance ambiance,
      StepBaseParameters stepElementParameters,
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
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
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

    AsgRollingDeployResult asgRollingDeployResult = asgRollingDeployResponse.getAsgRollingDeployResult();

    AsgRollingDeployOutcome asgRollingDeployOutcome =
        AsgRollingDeployOutcome.builder().asg(asgRollingDeployResult.getAutoScalingGroupContainer()).build();

    List<ServerInstanceInfo> serverInstanceInfos = asgStepCommonHelper.getServerInstanceInfos(asgRollingDeployResponse,
        infrastructureOutcome.getInfrastructureKey(),
        asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance).getRegion());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(asgRollingDeployOutcome)
                         .build())
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
}
