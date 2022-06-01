/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import static io.harness.pms.sdk.core.steps.io.StepResponse.builder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.UnexpectedException;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class PolicyStepHelper {
  public String getPolicySetsStringForQueryParam(List<String> policySets) {
    return policySets.toString().replace("[", "").replace("]", "").replace(" ", "");
  }

  public boolean isInvalidPayload(String payload) {
    try {
      YamlField yamlField = YamlUtils.readTree(payload);
      // Policy manager does not support primitive values like strings or numbers. Arrays are also not supported
      return !yamlField.getNode().isObject();
    } catch (IOException e) {
      return true;
    }
  }

  public StepResponse buildPolicyEvaluationErrorStepResponse(String errorResponseString) {
    try {
      PolicyEvaluationErrorResponse policyEvaluationErrorResponse =
          YamlUtils.read(errorResponseString, PolicyEvaluationErrorResponse.class);
      String errorMessage = policyEvaluationErrorResponse.getMessage().replace("policy set", "Policy Set");
      return buildFailureStepResponse(ErrorCode.POLICY_SET_ERROR, errorMessage, FailureType.UNKNOWN_FAILURE);

    } catch (IOException e) {
      log.error("Unable to parse error response from Policy Manager. Error response:\n" + errorResponseString, e);
      return PolicyStepHelper.buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          "Unexpected error occurred while evaluating Policies.", FailureType.APPLICATION_FAILURE);
    }
  }

  public StepResponse buildFailureStepResponse(ErrorCode errorCode, String message, FailureType failureType) {
    return buildFailureStepResponse(errorCode, message, failureType, null);
  }

  public String getEntityMetadataString(String stepName) {
    Map<String, String> metadataMap = ImmutableMap.<String, String>builder().put("entityName", stepName).build();
    try {
      return URLEncoder.encode(JsonUtils.asJson(metadataMap), StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      throw new UnexpectedException("Unable to encode entity metadata JSON into URL String");
    }
  }

  public StepResponse buildFailureStepResponse(
      ErrorCode errorCode, String message, FailureType failureType, StepOutcome stepOutcome) {
    FailureData failureData = FailureData.newBuilder()
                                  .setCode(errorCode.name())
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(message)
                                  .addFailureTypes(failureType)
                                  .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(message).build();
    if (stepOutcome == null) {
      // need a special if block so that an empty null element is not added to the overall step outcomes list later
      return builder().status(Status.FAILED).failureInfo(failureInfo).build();
    }
    return builder().status(Status.FAILED).failureInfo(failureInfo).stepOutcome(stepOutcome).build();
  }

  public String buildPolicyEvaluationFailureMessage(OpaEvaluationResponseHolder opaEvaluationResponseHolder) {
    List<OpaPolicySetEvaluationResponse> policySetResponses = opaEvaluationResponseHolder.getDetails();
    List<String> failedPolicySets = policySetResponses.stream()
                                        .filter(response -> response.getStatus().equals(OpaConstants.OPA_STATUS_ERROR))
                                        .map(OpaPolicySetEvaluationResponse::getName)
                                        .collect(Collectors.toList());
    String failedPolicySetsString = String.join(", ", failedPolicySets);
    if (failedPolicySets.isEmpty()) {
      return "";
    }
    if (failedPolicySets.size() == 1) {
      return "The following Policy Set was not adhered to: " + failedPolicySetsString;
    }
    return "The following Policy Sets were not adhered to: " + failedPolicySetsString;
  }
}
