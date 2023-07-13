
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.serverless.StackDetails;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
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
public class ServerlessAwsLambdaPrepareRollbackV2Step extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Inject ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private InstanceInfoService instanceInfoService;

  public static final StepType STEP_TYPE =
      StepType.newBuilder()
          .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_V2.getYamlType())
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
    ServerlessAwsLambdaPrepareRollbackV2StepParameters serverlessAwsLambdaPrepareRollbackV2StepParameters =
        (ServerlessAwsLambdaPrepareRollbackV2StepParameters) stepElementParameters.getSpec();

    // Check if image exists
    serverlessStepCommonHelper.verifyPluginImageIsProvider(
        serverlessAwsLambdaPrepareRollbackV2StepParameters.getImage());

    Map<String, String> envVarMap = new HashMap<>();
    serverlessStepCommonHelper.putValuesYamlEnvVars(
        ambiance, serverlessAwsLambdaPrepareRollbackV2StepParameters, envVarMap);

    return getUnitStep(ambiance, stepElementParameters, accountId, logKey, parkedTaskId,
        serverlessAwsLambdaPrepareRollbackV2StepParameters, envVarMap);
  }

  public UnitStep getUnitStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, String parkedTaskId,
      ServerlessAwsLambdaPrepareRollbackV2StepParameters serverlessAwsLambdaPrepareRollbackV2StepParameters,
      Map envVarMap) {
    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, envVarMap,
        serverlessAwsLambdaPrepareRollbackV2StepParameters.getImage().getValue(), Collections.EMPTY_LIST);
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String stackDetailsString = null;

    StepStatusTaskResponseData stepStatusTaskResponseData = null;

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof StepStatusTaskResponseData) {
        stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
      }
    }

    StepResponse.StepOutcome stepOutcome = null;

    if (stepStatusTaskResponseData != null
        && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

      if (stepOutput instanceof StepMapOutput) {
        StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
        String stackDetailsByte64 = stepMapOutput.getMap().get("stackDetails");
        stackDetailsString = serverlessStepCommonHelper.convertByte64ToString(stackDetailsByte64);
      }

      StackDetails stackDetails = null;
      try {
        stackDetails = serverlessStepCommonHelper.getStackDetails(stackDetailsString);
      } catch (Exception e) {
        log.error("Error while parsing Stack Details", e);
      }

      ServerlessAwsLambdaPrepareRollbackDataOutcome serverlessAwsLambdaPrepareRollbackDataOutcome = null;
      if (stackDetails != null) {
        serverlessAwsLambdaPrepareRollbackDataOutcome =
            ServerlessAwsLambdaPrepareRollbackDataOutcome.builder().stackDetails(stackDetails).build();
        executionSweepingOutputService.consume(ambiance,
            OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_DATA_OUTCOME_V2,
            serverlessAwsLambdaPrepareRollbackDataOutcome, StepOutcomeGroup.STEP.name());
      }
    }

    return stepOutcome;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}