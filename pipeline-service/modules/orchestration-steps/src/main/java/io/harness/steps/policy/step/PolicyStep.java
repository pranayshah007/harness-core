/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.PolicyConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.network.SafeHttpCall;
import io.harness.opaclient.OpaServiceClient;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.policy.PolicyStepConstants;
import io.harness.steps.policy.PolicyStepSpecParameters;
import io.harness.steps.policy.custom.CustomPolicyStepSpec;
import io.harness.utils.PolicyEvalUtils;
import io.harness.utils.PolicyStepOutcome;
import io.harness.utils.PolicyStepOutcomeMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PolicyStep implements SyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.POLICY_STEP_TYPE;
  @Inject OpaServiceClient opaServiceClient;

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    PolicyStepSpecParameters policyStepSpecParameters = (PolicyStepSpecParameters) stepParameters.getSpec();
    List<String> policySets = policyStepSpecParameters.getPolicySets().getValue();
    if (EmptyPredicate.isEmpty(policySets)) {
      return PolicyEvalUtils.buildFailureStepResponse(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION,
          "List of Policy Sets cannot by empty", FailureType.UNKNOWN_FAILURE);
    }

    String payload;
    String policyStepType = policyStepSpecParameters.getType();
    switch (policyStepType) {
      case PolicyStepConstants.CUSTOM_POLICY_STEP_TYPE:
        CustomPolicyStepSpec customPolicySpec = (CustomPolicyStepSpec) policyStepSpecParameters.getPolicySpec();
        // We don't need to handle the case for unresolved expressions as expressionMode for PolicyStep is set to
        // THROW_EXCEPTION_IF_UNRESOLVED.
        payload = customPolicySpec.getPayload().getValue();
        if (EmptyPredicate.isEmpty(payload)) {
          log.error("Empty custom payload is not allowed.");
          return PolicyEvalUtils.buildFailureStepResponse(
              ErrorCode.INVALID_JSON_PAYLOAD, "Empty custom payload is not allowed.", FailureType.UNKNOWN_FAILURE);
        }
        if (PolicyEvalUtils.isInvalidPayload(payload)) {
          log.error("Custom payload is not a valid JSON:\n" + payload);
          return PolicyEvalUtils.buildFailureStepResponse(
              ErrorCode.INVALID_JSON_PAYLOAD, "Custom payload is not a valid JSON.", FailureType.UNKNOWN_FAILURE);
        }
        break;
      default:
        return PolicyEvalUtils.buildFailureStepResponse(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION,
            "Policy Step type " + policyStepType + " is not supported.", FailureType.UNKNOWN_FAILURE);
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    OpaEvaluationResponseHolder opaEvaluationResponseHolder;
    try {
      String policySetsQueryParam = PolicyEvalUtils.getPolicySetsStringForQueryParam(policySets);
      JsonNode payloadObject = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
      opaEvaluationResponseHolder = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.evaluateWithCredentialsByID(accountId, orgIdentifier, projectIdentifier,
              policySetsQueryParam, PolicyEvalUtils.getEntityMetadataString(stepParameters.getName()), payloadObject));
    } catch (InvalidRequestException ex) {
      return PolicyEvalUtils.buildPolicyEvaluationErrorStepResponse(ex.getMessage(), null);
    } catch (Exception ex) {
      log.error(PolicyConstants.OPA_EVALUATION_ERROR_MSG, ex);
      return PolicyEvalUtils.buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          PolicyConstants.POLICY_EVALUATION_UNEXPECTED_ERROR_MSG, FailureType.APPLICATION_FAILURE);
    }
    PolicyStepOutcome outcome = PolicyStepOutcomeMapper.toOutcome(opaEvaluationResponseHolder);
    StepOutcome stepOutcome = StepOutcome.builder()
                                  .group(StepCategory.STEP.name())
                                  .name(YAMLFieldNameConstants.OUTPUT)
                                  .outcome(outcome)
                                  .build();

    if (opaEvaluationResponseHolder.getStatus().equals(OpaConstants.OPA_STATUS_ERROR)) {
      String errorMessage = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(opaEvaluationResponseHolder);
      return PolicyEvalUtils.buildFailureStepResponse(
          ErrorCode.POLICY_EVALUATION_FAILURE, errorMessage, FailureType.POLICY_EVALUATION_FAILURE, stepOutcome);
    }
    return StepResponse.builder().status(Status.SUCCEEDED).stepOutcome(stepOutcome).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
