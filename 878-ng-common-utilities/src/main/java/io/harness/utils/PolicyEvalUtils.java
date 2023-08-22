/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import static io.harness.pms.sdk.core.steps.io.StepResponse.builder;

import io.harness.PolicyConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
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

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PolicyEvalUtils {
  public String getPolicySetsStringForQueryParam(List<String> policySets) {
    return policySets.toString().replace("[", "").replace("]", "").replace(" ", "");
  }

  public boolean isInvalidPayload(String payload) {
    try {
      YamlField yamlField = YamlUtils.readTree(payload);
      // Policy manager does not support primitive values like strings or numbers.
      YamlNode rootNode = yamlField.getNode();
      return isInvalidNode(rootNode);
    } catch (IOException e) {
      log.error("Exception while reading payload", e);
      return true;
    }
  }

  private static boolean isInvalidNode(YamlNode rootNode) {
    if (rootNode.isArray()) {
      for (YamlNode element : rootNode.asArray()) {
        // Perform checks on individual elements of the array
        if (isInvalidNode(element)) {
          return true;
        }
      }
      return false;
    }
    return !rootNode.isObject();
  }

  public StepResponse buildPolicyEvaluationErrorStepResponse(String errorResponseString, StepResponse stepResponse) {
    try {
      PolicyEvaluationErrorResponse policyEvaluationErrorResponse =
          YamlUtils.read(errorResponseString, PolicyEvaluationErrorResponse.class);
      String errorMessage = policyEvaluationErrorResponse.getMessage().replace(
          PolicyConstants.POLICY_SET_IN_SMALL_CASE, PolicyConstants.POLICY_SET_IN_CAMEL_CASE);
      return PolicyEvalUtils.buildFailureStepResponse(
          ErrorCode.POLICY_SET_ERROR, errorMessage, FailureType.UNKNOWN_FAILURE, stepResponse);

    } catch (IOException e) {
      log.error("Unable to parse error response from Policy Manager. Error response:\n" + errorResponseString, e);
      return PolicyEvalUtils.buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          PolicyConstants.POLICY_EVALUATION_UNEXPECTED_ERROR_MSG, FailureType.APPLICATION_FAILURE, stepResponse);
    }
  }

  public StepResponse buildFailureStepResponse(ErrorCode errorCode, String message, FailureType failureType) {
    return buildFailureStepResponse(errorCode, message, failureType, null, null);
  }

  public StepResponse buildFailureStepResponse(
      ErrorCode errorCode, String message, FailureType failureType, StepOutcome stepOutcome) {
    return buildFailureStepResponse(errorCode, message, failureType, stepOutcome, null);
  }

  public StepResponse buildFailureStepResponse(
      ErrorCode errorCode, String message, FailureType failureType, StepResponse stepResponse) {
    return buildFailureStepResponse(errorCode, message, failureType, null, stepResponse);
  }

  public String getEntityMetadataString(String stepName) {
    Map<String, String> metadataMap =
        ImmutableMap.<String, String>builder().put(PolicyConstants.ENTITY_NAME, stepName).build();
    try {
      return URLEncoder.encode(JsonUtils.asJson(metadataMap), StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      throw new UnexpectedException("Unable to encode entity metadata JSON into URL String");
    }
  }

  private StepResponse buildFailureStepResponse(ErrorCode errorCode, String message, FailureType failureType,
      StepOutcome stepOutcome, StepResponse stepResponse) {
    FailureData failureData = FailureData.newBuilder()
                                  .setCode(errorCode.name())
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(message)
                                  .addFailureTypes(failureType)
                                  .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).setErrorMessage(message).build();
    if (stepOutcome == null) {
      // need a special if block so that an empty null element is not added to the overall step outcomes list later
      if (stepResponse == null) {
        return builder().status(Status.FAILED).failureInfo(failureInfo).build();
      } else {
        return stepResponse.toBuilder().status(Status.FAILED).failureInfo(failureInfo).build();
      }
    }
    if (stepResponse == null) {
      return builder().status(Status.FAILED).failureInfo(failureInfo).stepOutcome(stepOutcome).build();
    } else {
      return stepResponse.toBuilder().status(Status.FAILED).failureInfo(failureInfo).stepOutcome(stepOutcome).build();
    }
  }

  public String buildPolicyEvaluationFailureMessage(OpaEvaluationResponseHolder opaEvaluationResponseHolder) {
    List<OpaPolicySetEvaluationResponse> policySetResponses = opaEvaluationResponseHolder.getDetails();
    List<String> failedPolicySets = policySetResponses.stream()
                                        .filter(response -> OpaConstants.OPA_STATUS_ERROR.equals(response.getStatus()))
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

  public StepResponse evalPolicies(Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse,
      OpaServiceClient opaServiceClient) {
    if (stepParameters.getEnforce() == null || ParameterField.isNull(stepParameters.getEnforce().getPolicySets())
        || isEmpty(stepParameters.getEnforce().getPolicySets().getValue())) {
      return stepResponse;
    }
    OpaEvaluationResponseHolder opaEvaluationResponseHolder;
    try {
      opaEvaluationResponseHolder = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.evaluateWithCredentialsByID(AmbianceUtils.getAccountId(ambiance),
              AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
              getPolicySetsStringForQueryParam(stepParameters.getEnforce().getPolicySets().getValue()),
              getEntityMetadataString(stepParameters.getName()), stepResponse.getStepOutcomes()));
    } catch (InvalidRequestException ex) {
      log.error(PolicyConstants.OPA_EVALUATION_ERROR_MSG, ex);
      return PolicyEvalUtils.buildPolicyEvaluationErrorStepResponse(ex.getMessage(), stepResponse);
    } catch (Exception ex) {
      log.error(PolicyConstants.OPA_EVALUATION_ERROR_MSG, ex);
      return buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          PolicyConstants.POLICY_EVALUATION_UNEXPECTED_ERROR_MSG, FailureType.APPLICATION_FAILURE, stepResponse);
    }
    PolicyStepOutcome outcome = PolicyStepOutcomeMapper.toOutcome(opaEvaluationResponseHolder);
    StepOutcome policyOutcome = StepOutcome.builder()
                                    .group(StepCategory.STEP.name())
                                    .name(YAMLFieldNameConstants.POLICY_OUTPUT)
                                    .outcome(outcome)
                                    .build();
    if (OpaConstants.OPA_STATUS_ERROR.equals(opaEvaluationResponseHolder.getStatus())) {
      String errorMessage = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(opaEvaluationResponseHolder);
      stepResponse = PolicyEvalUtils.buildFailureStepResponse(ErrorCode.POLICY_EVALUATION_FAILURE, errorMessage,
          FailureType.POLICY_EVALUATION_FAILURE, policyOutcome, stepResponse);
      stepResponse = stepResponse.toBuilder().status(Status.FAILED).build();
    } else {
      stepResponse = stepResponse.toBuilder().stepOutcome(policyOutcome).build();
    }
    return stepResponse;
  }
}
