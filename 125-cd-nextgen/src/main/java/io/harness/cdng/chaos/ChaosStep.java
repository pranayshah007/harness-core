/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.chaos;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.chaos.client.beans.ChaosQuery;
import io.harness.chaos.client.beans.ChaosRerunResponse;
import io.harness.chaos.client.remote.ChaosHttpClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.opaclient.OpaServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.tasks.ResponseData;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_GITOPS})
@Slf4j
public class ChaosStep extends AsyncExecutableWithCapabilities {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.CHAOS_STEP).setStepCategory(StepCategory.STEP).build();

  @Inject private ChaosHttpClient client;
  @Inject OpaServiceClient opaServiceClient;

  private static final String BODY =
      "mutation{reRunChaosWorkFlow(workflowID: \"%s\",identifiers:{orgIdentifier: \"%s\",projectIdentifier: \"%s\",accountIdentifier: \"%s\"}){notifyID}}";

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ChaosStepParameters params = (ChaosStepParameters) stepParameters.getSpec();
    String callbackId = triggerWorkflow(ambiance, params);
    log.info("Triggered chaos experiment with ref: {}, workflowRunId: {}", params.getExperimentRef(), callbackId);
    return AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).build();
  }

  @SneakyThrows
  private String triggerWorkflow(Ambiance ambiance, ChaosStepParameters params) {
    try {
      ChaosRerunResponse response =
          NGRestUtils.getResponse(client.reRunWorkflow(buildPayload(ambiance, params.getExperimentRef())));
      if (response != null && response.isSuccessful()) {
        return response.getNotifyId();
      }
      throw new ChaosRerunException("Error talking to chaos service");
    } catch (Exception ex) {
      log.error("Unable to trigger chaos experiment", ex);
      throw ex;
    }
  }

  @Override
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ChaosStepParameters params = (ChaosStepParameters) stepParameters.getSpec();
    ChaosStepNotifyData data = (ChaosStepNotifyData) responseDataMap.values().iterator().next();
    StepResponseBuilder responseBuilder =
        StepResponse.builder().stepOutcome(StepOutcome.builder().outcome(data).name("output").build());

    if (!data.isSuccess()) {
      return responseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder()
                                               .setLevel(Level.ERROR.name())
                                               .setCode(GENERAL_ERROR.name())
                                               .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                               .setMessage("Experiment did not finish Successfully")
                                               .build())
                           .setErrorMessage("Experiment did not finish Successfully")
                           .build())
          .build();
    }

    if (params.getExpectedResilienceScore() > data.getResiliencyScore()) {
      return responseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder()
                                               .setLevel(Level.ERROR.name())
                                               .setCode(GENERAL_ERROR.name())
                                               .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                               .setMessage(String.format(
                                                   "Score %2.2f is less than expected Resiliency Score of %2.2f",
                                                   data.getResiliencyScore(), params.getExpectedResilienceScore()))
                                               .build())
                           .setErrorMessage(String.format("Score %2.2f is less than expected Resiliency Score of %2.2f",
                               data.getResiliencyScore(), params.getExpectedResilienceScore()))
                           .build())
          .build();
    }

    if (!validateAssertions((ChaosStepParameters) stepParameters.getSpec(), data)) {
      return responseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder()
                                               .setLevel(Level.ERROR.name())
                                               .setCode(GENERAL_ERROR.name())
                                               .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                               .setMessage("Assertion failed")
                                               .build())
                           .setErrorMessage("Assertion failed")
                           .build())
          .build();
    }

    return responseBuilder.status(Status.SUCCEEDED).build();
  }

  private ChaosQuery buildPayload(Ambiance ambiance, String experimentRef) {
    String query = String.format(BODY, experimentRef, AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getAccountId(ambiance));
    return ChaosQuery.builder().query(query).build();
  }

  public static boolean validateAssertions(ChaosStepParameters stepParameters, ChaosStepNotifyData data) {
    if (ParameterField.isNull(stepParameters.getAssertion())) {
      return true;
    }

    ChaosStepExpressionEvaluator evaluator = new ChaosStepExpressionEvaluator(data);
    String assertion = (String) stepParameters.getAssertion().fetchFinalValue();
    if (assertion == null || EmptyPredicate.isEmpty(assertion.trim())) {
      return true;
    }
    try {
      Object value = evaluator.evaluateExpression(assertion);
      if (!(value instanceof Boolean)) {
        throw new InvalidRequestException(String.format(
            "Expected boolean assertion, got %s value", value == null ? "null" : value.getClass().getSimpleName()));
      }
      return (boolean) value;
    } catch (Exception e) {
      throw new InvalidRequestException("Assertion provided is not a valid expression", e);
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  @Override
  public StepResponse postAsyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }
}
