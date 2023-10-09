/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import static software.wings.beans.TaskType.AWS_ASG_CANARY_DEPLOY_TASK_NG;
import static software.wings.beans.TaskType.AWS_ASG_CANARY_DEPLOY_TASK_NG_V2;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
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
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AsgCanaryDeployStep extends TaskChainExecutableWithRollbackAndRbac implements AsgStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_CANARY_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  private static final String ASG_CANARY_DEPLOY_COMMAND_NAME = "AsgCanaryDeploy";
  private static final String CANARY_SUFFIX = "Canary";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private AsgStepCommonHelper asgStepCommonHelper;

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
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepBaseParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    DelegateResponseData delegateResponseData = (DelegateResponseData) responseSupplier.get();
    AsgExecutionPassThroughData executionPassThroughData = (AsgExecutionPassThroughData) passThroughData;

    Supplier<TaskChainResponse> executeAsgTaskSupplier = ()
        -> executeAsgTask(ambiance, stepElementParameters, executionPassThroughData,
            executionPassThroughData.getLastActiveUnitProgressData(), null);

    return asgStepCommonHelper.chainFetchGitTaskUntilAllGitManifestsFetched(
        executionPassThroughData, delegateResponseData, ambiance, stepElementParameters, executeAsgTaskSupplier);
  }

  @Override
  public TaskChainResponse executeAsgTask(Ambiance ambiance, StepBaseParameters stepElementParameters,
      AsgExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData,
      AsgStepExecutorParams asgStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    Map<String, List<String>> asgStoreManifestsContent =
        asgStepCommonHelper.buildManifestContentMap(executionPassThroughData.getAsgManifestFetchData(), ambiance);

    AsgCanaryDeployStepParameters asgSpecParameters = (AsgCanaryDeployStepParameters) stepElementParameters.getSpec();
    AsgInfraConfig asgInfraConfig = asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance);
    String amiImageId = asgStepCommonHelper.getAmiImageId(ambiance);

    AsgCanaryDeployRequest asgCanaryDeployRequest =
        AsgCanaryDeployRequest.builder()
            .commandName(ASG_CANARY_DEPLOY_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgInfraConfig)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgStoreManifestsContent(asgStoreManifestsContent)
            .serviceNameSuffix(CANARY_SUFFIX)
            .unitValue(asgSpecParameters.getInstanceSelection().getSpec().getInstances())
            .unitType(asgSpecParameters.getInstanceSelection().getSpec().getType())
            .amiImageId(amiImageId)
            .asgName(getParameterFieldValue(asgSpecParameters.getAsgName()))
            .build();

    TaskType taskType =
        asgStepCommonHelper.isV2Feature(asgStoreManifestsContent, null, null, asgInfraConfig, asgSpecParameters)
        ? AWS_ASG_CANARY_DEPLOY_TASK_NG_V2
        : AWS_ASG_CANARY_DEPLOY_TASK_NG;

    return asgStepCommonHelper.queueAsgTask(
        stepElementParameters, asgCanaryDeployRequest, ambiance, executionPassThroughData, true, taskType);
  }

  @Override
  public TaskChainResponse executeAsgPrepareRollbackDataTask(Ambiance ambiance, StepBaseParameters stepParameters,
      AsgPrepareRollbackDataPassThroughData asgStepPassThroughData, UnitProgressData unitProgressData) {
    // nothing to prepare
    return null;
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof AsgStepExceptionPassThroughData) {
      return asgStepCommonHelper.handleStepExceptionFailure((AsgStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());

    AsgExecutionPassThroughData asgExecutionPassThroughData = (AsgExecutionPassThroughData) passThroughData;
    AsgCanaryDeployResponse asgCanaryDeployResponse;
    try {
      asgCanaryDeployResponse = (AsgCanaryDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing asg task response: {}", e.getMessage(), e);
      return asgStepCommonHelper.handleTaskException(ambiance, asgExecutionPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(asgCanaryDeployResponse.getUnitProgressData().getUnitProgresses());

    if (asgCanaryDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return AsgStepCommonHelper.getFailureResponseBuilder(asgCanaryDeployResponse, stepResponseBuilder).build();
    }

    AsgCanaryDeployOutcome asgCanaryDeployOutcome =
        AsgCanaryDeployOutcome.builder()
            .asg(asgCanaryDeployResponse.getAsgCanaryDeployResult().getAutoScalingGroupContainer())
            .build();

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ASG_CANARY_DEPLOY_OUTCOME,
        asgCanaryDeployOutcome, StepOutcomeGroup.STEP.name());

    InfrastructureOutcome infrastructureOutcome = asgExecutionPassThroughData.getInfrastructure();

    List<ServerInstanceInfo> serverInstanceInfos = asgStepCommonHelper.getServerInstanceInfos(asgCanaryDeployResponse,
        infrastructureOutcome.getInfrastructureKey(),
        asgStepCommonHelper.getAsgInfraConfig(infrastructureOutcome, ambiance).getRegion());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(asgCanaryDeployOutcome)
                         .build())
        .build();
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
}
