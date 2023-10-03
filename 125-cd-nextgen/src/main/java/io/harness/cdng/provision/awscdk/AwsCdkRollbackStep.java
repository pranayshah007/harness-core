/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsCdkRollbackStep extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Inject private AwsCdkHelper awsCdkStepHelper;

  @Inject private AwsCdkConfigDAL awsCdkConfigDAL;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_CDK_ROLLBACK.getYamlType())
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
    AwsCdkRollbackStepParameters awsCdkRollbackStepParameters =
        (AwsCdkRollbackStepParameters) stepElementParameters.getSpec();
    AwsCdkConfig awsCdkConfig = awsCdkConfigDAL.getRollbackAwsCdkConfig(
        ambiance, getParameterFieldValue(awsCdkRollbackStepParameters.getProvisionerIdentifier()));

    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, new HashMap<>(),
        awsCdkConfig.getImage(), Collections.EMPTY_LIST);
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (shouldSkip(ambiance, stepParameters)) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    awsCdkStepHelper.handleBinaryResponseData(responseDataMap);
    StepStatusTaskResponseData stepStatusTaskResponseData =
        containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);
    if (stepStatusTaskResponseData != null
        && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      AwsCdkRollbackStepParameters awsCdkRollbackStepParameters =
          (AwsCdkRollbackStepParameters) stepParameters.getSpec();
      StepMapOutput stepOutput = (StepMapOutput) stepStatusTaskResponseData.getStepStatus().getOutput();
      Map<String, String> processedOutput = awsCdkStepHelper.processOutput(stepOutput);
      stepOutput.setMap(processedOutput);
      awsCdkConfigDAL.deleteAwsCdkConfig(
          ambiance, getParameterFieldValue(awsCdkRollbackStepParameters.getProvisionerIdentifier()));
    }
    return super.handleAsyncResponse(ambiance, stepParameters, responseDataMap);
  }

  @Override
  public boolean shouldSkip(Ambiance ambiance, StepElementParameters stepElementParameters) {
    AwsCdkRollbackStepParameters awsCdkRollbackStepParameters =
        (AwsCdkRollbackStepParameters) stepElementParameters.getSpec();
    AwsCdkConfig awsCdkConfig = awsCdkConfigDAL.getRollbackAwsCdkConfig(
        ambiance, getParameterFieldValue(awsCdkRollbackStepParameters.getProvisionerIdentifier()));
    return awsCdkConfig == null;
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    awsCdkStepHelper.validateFeatureEnabled(ambiance);
  }

  private Map<String, String> getEnvironmentVariables(
      AwsCdkConfig awsCdkConfig, AwsCdkRollbackStepParameters awsCdkRollbackStepParameters) {
    Map<String, String> envVariables = awsCdkConfig.getEnvVariables();
    Map<String, String> rollbackEnvVariables = getParameterFieldValue(awsCdkRollbackStepParameters.getEnvVariables());
    if (isNotEmpty(rollbackEnvVariables)) {
      envVariables.putAll(rollbackEnvVariables);
    }

    return envVariables;
  }
}
