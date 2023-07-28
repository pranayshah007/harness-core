/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.StepHelper;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaRollbackV2Step extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Inject ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Inject private StepHelper stepHelper;
  @Inject private AccountService accountService;

  @Inject private OutcomeService outcomeService;
  @Inject private InstanceInfoService instanceInfoService;

  public static final StepType STEP_TYPE =
      StepType.newBuilder()
          .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2.getYamlType())
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
    ServerlessAwsLambdaRollbackV2StepParameters serverlessAwsLambdaRollbackV2StepParameters =
        (ServerlessAwsLambdaRollbackV2StepParameters) stepElementParameters.getSpec();

    // Check if image exists
    serverlessStepCommonHelper.verifyPluginImageIsProvider(serverlessAwsLambdaRollbackV2StepParameters.getImage());

    Map<String, String> envVarMap = new HashMap<>();
    populateEnvVariablesForRollbackStep(serverlessAwsLambdaRollbackV2StepParameters, ambiance, envVarMap);

    return getUnitStep(ambiance, stepElementParameters, accountId, logKey, parkedTaskId,
        serverlessAwsLambdaRollbackV2StepParameters, envVarMap);
  }

  public void populateEnvVariablesForRollbackStep(
      ServerlessAwsLambdaRollbackV2StepParameters serverlessAwsLambdaRollbackV2StepParameters, Ambiance ambiance,
      Map<String, String> envVarMap) {
    if (EmptyPredicate.isEmpty(serverlessAwsLambdaRollbackV2StepParameters.getServerlessAwsLambdaRollbackFnq())) {
      envVarMap.put("PLUGIN_SERVERLESS_PREPARE_ROLLBACK_EXECUTED", "false");
      return;
    }

    OptionalSweepingOutput serverlessRollbackDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                serverlessAwsLambdaRollbackV2StepParameters.getServerlessAwsLambdaRollbackFnq() + "."
                + OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_DATA_OUTCOME_V2));
    if (!serverlessRollbackDataOptionalOutput.isFound()) {
      envVarMap.put("PLUGIN_SERVERLESS_PREPARE_ROLLBACK_EXECUTED", "false");
      return;
    }

    envVarMap.put("PLUGIN_SERVERLESS_PREPARE_ROLLBACK_EXECUTED", "true");
    ServerlessAwsLambdaPrepareRollbackDataOutcome serverlessAwsLambdaPrepareRollbackDataOutcome =
        (ServerlessAwsLambdaPrepareRollbackDataOutcome) serverlessRollbackDataOptionalOutput.getOutput();

    if (serverlessAwsLambdaPrepareRollbackDataOutcome.isFirstDeployment()) {
      envVarMap.put("PLUGIN_SERVERLESS_FIRST_DEPLOYMENT", "true");
      envVarMap.put("PLUGIN_SERVERLESS_STACK_DETAILS", "");
    } else {
      envVarMap.put("PLUGIN_SERVERLESS_FIRST_DEPLOYMENT", "false");
      String stackDetailsBase64 = Base64.getEncoder().encodeToString(
          toJson(serverlessAwsLambdaPrepareRollbackDataOutcome.getStackDetails()).getBytes());
      envVarMap.put("PLUGIN_SERVERLESS_STACK_DETAILS", stackDetailsBase64);
    }
  }
  public String toJson(Object object) {
    try {
      ObjectMapper mapper = getObjectMapper();
      return mapper.writeValueAsString(object);
    } catch (JsonProcessingException jsonProcessingException) {
      return "";
    }
  }
  public ObjectMapper getObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    return mapper;
  }

  public UnitStep getUnitStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, String parkedTaskId,
      ServerlessAwsLambdaRollbackV2StepParameters serverlessAwsLambdaRollbackV2StepParameters, Map envVarMap) {
    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, envVarMap,
        serverlessAwsLambdaRollbackV2StepParameters.getImage().getValue(), Collections.EMPTY_LIST);
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // If any of the responses are in serialized format, deserialize them
    containerStepExecutionResponseHelper.deserializeResponse(responseDataMap);
    log.info("Serverless Aws Lambda Rollback V2:  Response deserialized");

    StepStatusTaskResponseData stepStatusTaskResponseData =
        containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

    if (stepStatusTaskResponseData == null) {
      log.info("Serverless Aws Lambda Rollback V2:  Received stepStatusTaskResponseData as null");
    } else if (stepStatusTaskResponseData.getStepStatus() == null) {
      log.info("Serverless Aws Lambda Rollback V2:  Received stepStatusTaskResponseData.stepExecutionStatus as null");
    } else {
      log.info(String.format("Serverless Aws Lambda Rollback V2:  Received stepStatusTaskResponseData with status %s",
          stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus()));
    }

    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}