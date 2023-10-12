/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.DEPLOY;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_ACTION;
import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.cdng.provision.awscdk.AwsCdkHelper.CDK_OUTPUT_OUTCOME_NAME;
import static io.harness.cdng.provision.awscdk.AwsCdkHelper.GIT_COMMIT_ID;
import static io.harness.cdng.provision.awscdk.AwsCdkHelper.LATEST_SUCCESSFUL_PROVISIONING_COMMIT_ID;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.awscdk.beans.AwsCdkOutcome;
import io.harness.cdng.provision.awscdk.beans.ContainerResourceConfig;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsCdkDeployStep extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Inject private AwsCdkHelper awsCdkStepHelper;
  @Inject private AwsCdkConfigDAL awsCdkConfigDAL;
  @Inject private ProvisionerOutputHelper provisionerOutputHelper;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_CDK_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public long getTimeout(Ambiance ambiance, StepElementParameters stepElementParameters) {
    return Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
  }

  @Override
  public UnitStep getSerialisedStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, long timeout, String parkedTaskId) {
    AwsCdkDeployStepParameters awsCdkDeployStepParameters =
        (AwsCdkDeployStepParameters) stepElementParameters.getSpec();

    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, new HashMap<>(),
        awsCdkDeployStepParameters.getImage().getValue(), Collections.EMPTY_LIST);
  }

  protected AwsCdkConfig getAwsCdkConfig(
      Ambiance ambiance, AwsCdkDeployStepParameters awsCdkDeployStepParameters, String commitId) {
    ContainerResourceConfig resourceConfig = getResourceConfig(awsCdkDeployStepParameters);

    return AwsCdkConfig.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
        .provisionerIdentifier(getParameterFieldValue(awsCdkDeployStepParameters.getProvisionerIdentifier()))
        .stageExecutionId(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
        .resources(resourceConfig)
        .runAsUser(getParameterFieldValue(awsCdkDeployStepParameters.getRunAsUser()))
        .connectorRef(getParameterFieldValue(awsCdkDeployStepParameters.getConnectorRef()))
        .image(getParameterFieldValue(awsCdkDeployStepParameters.getImage()))
        .imagePullPolicy(getParameterFieldValue(awsCdkDeployStepParameters.getImagePullPolicy()))
        .envVariables(getEnvironmentVariables(awsCdkDeployStepParameters))
        .parameters(getParameterFieldValue(awsCdkDeployStepParameters.getParameters()))
        .privileged(getParameterFieldValue(awsCdkDeployStepParameters.getPrivileged()))
        .commitId(commitId)
        .build();
  }

  private ContainerResourceConfig getResourceConfig(AwsCdkDeployStepParameters awsCdkDeployStepParameters) {
    ContainerResource containerResource = awsCdkDeployStepParameters.getResources();
    if (containerResource != null) {
      ContainerResourceConfig.Limits requests = null;
      ContainerResourceConfig.Limits limits = null;
      if (containerResource.getRequests() != null) {
        requests = ContainerResourceConfig.Limits.builder()
                       .memory(getParameterFieldValue(containerResource.getRequests().getMemory()))
                       .cpu(getParameterFieldValue(containerResource.getRequests().getCpu()))
                       .build();
      }
      if (containerResource.getLimits() != null) {
        limits = ContainerResourceConfig.Limits.builder()
                     .memory(getParameterFieldValue(containerResource.getLimits().getMemory()))
                     .cpu(getParameterFieldValue(containerResource.getLimits().getCpu()))
                     .build();
      }
      return ContainerResourceConfig.builder().requests(requests).limits(limits).build();
    }
    return null;
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    AwsCdkDeployStepParameters awsCdkDeployStepParameters = (AwsCdkDeployStepParameters) stepParameters.getSpec();
    awsCdkStepHelper.handleBinaryResponseData(responseDataMap);
    StepStatusTaskResponseData stepStatusTaskResponseData =
        containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

    if (stepStatusTaskResponseData != null
        && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      StepMapOutput stepOutput = (StepMapOutput) stepStatusTaskResponseData.getStepStatus().getOutput();
      Map<String, Object> stepOutcome = awsCdkStepHelper.processOutput(stepOutput);
      AwsCdkConfig latestSuccessfulAwsCdkConfig = awsCdkConfigDAL.getRollbackAwsCdkConfig(
          ambiance, getParameterFieldValue(awsCdkDeployStepParameters.getProvisionerIdentifier()));
      String currentProvisioningCommitId = stepOutput.getMap().get(GIT_COMMIT_ID);
      String latestSuccessfulProvisioningCommitId = latestSuccessfulAwsCdkConfig != null
          ? latestSuccessfulAwsCdkConfig.getCommitId()
          : currentProvisioningCommitId;
      stepOutput.getMap().put(LATEST_SUCCESSFUL_PROVISIONING_COMMIT_ID, latestSuccessfulProvisioningCommitId);
      awsCdkConfigDAL.saveAwsCdkConfig(
          getAwsCdkConfig(ambiance, awsCdkDeployStepParameters, currentProvisioningCommitId));
      AwsCdkOutcome awsCdkOutcome = new AwsCdkOutcome(stepOutcome);
      provisionerOutputHelper.saveProvisionerOutputByStepIdentifier(ambiance, awsCdkOutcome);
      return StepResponse.StepOutcome.builder().name(CDK_OUTPUT_OUTCOME_NAME).outcome(awsCdkOutcome).build();
    }
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    awsCdkStepHelper.validateFeatureEnabled(ambiance);
    awsCdkStepHelper.validateRuntimePermissions(ambiance, (AwsCdkBaseStepInfo) stepParameters.getSpec());
  }

  private Map<String, String> getEnvironmentVariables(AwsCdkDeployStepParameters awsCdkDeployStepParameters) {
    ParameterField<Map<String, String>> envVariables = awsCdkDeployStepParameters.getEnvVariables();
    HashMap<String, String> environmentVariablesMap =
        awsCdkStepHelper.getCommonEnvVariables(getParameterFieldValue(awsCdkDeployStepParameters.getAppPath()),
            getParameterFieldValue(awsCdkDeployStepParameters.getCommandOptions()), envVariables);
    List<String> stackNames = getParameterFieldValue(awsCdkDeployStepParameters.getStackNames());

    if (isNotEmpty(stackNames)) {
      environmentVariablesMap.put(PLUGIN_AWS_CDK_STACK_NAMES, String.join(" ", stackNames));
    }

    awsCdkStepHelper.addParametersToEnvValues(
        getParameterFieldValue(awsCdkDeployStepParameters.getParameters()), environmentVariablesMap);
    environmentVariablesMap.put(PLUGIN_AWS_CDK_ACTION, DEPLOY);

    return environmentVariablesMap;
  }
}
