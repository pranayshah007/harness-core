/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

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
import io.harness.steps.policy.step.outcome.PolicyStepOutcome;
import io.harness.steps.policy.step.outcome.PolicyStepOutcomeMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PolicyStep implements SyncExecutable<StepElementParameters> {
  public static StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.POLICY_STEP).setStepCategory(StepCategory.STEP).build();
  @Inject OpaServiceClient opaServiceClient;

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    PolicyStepSpecParameters policyStepSpecParameters = (PolicyStepSpecParameters) stepParameters.getSpec();
    // todo(@NamanVerma): Check for unresolved expressions
    List<String> policySets = policyStepSpecParameters.getPolicySets().getValue();
    if (EmptyPredicate.isEmpty(policySets)) {
      return PolicyStepHelper.buildFailureStepResponse(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION,
          "List of Policy Sets cannot by empty", FailureType.UNKNOWN_FAILURE);
    }

    String payload;
    String policyStepType = policyStepSpecParameters.getType();
    switch (policyStepType) {
      case PolicyStepConstants.CUSTOM_POLICY_STEP_TYPE:
        CustomPolicyStepSpec customPolicySpec = (CustomPolicyStepSpec) policyStepSpecParameters.getPolicySpec();
        // todo(@NamanVerma): Check for unresolved expressions
        payload = customPolicySpec.getPayload().getValue();
        if (PolicyStepHelper.isInvalidPayload(payload)) {
          log.error("Custom payload is not a valid JSON:\n" + payload);
          return PolicyStepHelper.buildFailureStepResponse(
              ErrorCode.INVALID_JSON_PAYLOAD, "Custom payload is not a valid JSON.", FailureType.UNKNOWN_FAILURE);
        }
        break;
      default:
        return PolicyStepHelper.buildFailureStepResponse(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION,
            "Policy Step type " + policyStepType + " is not supported.", FailureType.UNKNOWN_FAILURE);
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    OpaEvaluationResponseHolder opaEvaluationResponseHolder;
    try {
      String policySetsQueryParam = PolicyStepHelper.getPolicySetsStringForQueryParam(policySets);
      JsonNode payloadObject = YamlUtils.readTree(payload).getNode().getCurrJsonNode();
      opaEvaluationResponseHolder = SafeHttpCall.executeWithErrorMessage(
          opaServiceClient.evaluateWithCredentialsByID(accountId, orgIdentifier, projectIdentifier,
              policySetsQueryParam, PolicyStepHelper.getEntityMetadataString(stepParameters.getName()), payloadObject));
    } catch (InvalidRequestException ex) {
      return PolicyStepHelper.buildPolicyEvaluationErrorStepResponse(ex.getMessage());
    } catch (Exception ex) {
      log.error("Exception while evaluating OPA rules", ex);
      return PolicyStepHelper.buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          "Unexpected error occurred while evaluating Policies.", FailureType.APPLICATION_FAILURE);
    }
    PolicyStepOutcome outcome = PolicyStepOutcomeMapper.toOutcome(opaEvaluationResponseHolder);
    StepOutcome stepOutcome = StepOutcome.builder()
                                  .group(StepCategory.STEP.name())
                                  .name(YAMLFieldNameConstants.OUTPUT)
                                  .outcome(outcome)
                                  .build();

    if (opaEvaluationResponseHolder.getStatus().equals(OpaConstants.OPA_STATUS_ERROR)) {
      String errorMessage = PolicyStepHelper.buildPolicyEvaluationFailureMessage(opaEvaluationResponseHolder);
      return PolicyStepHelper.buildFailureStepResponse(
          ErrorCode.POLICY_EVALUATION_FAILURE, errorMessage, FailureType.POLICY_EVALUATION_FAILURE, stepOutcome);
    }
    return StepResponse.builder().status(Status.SUCCEEDED).stepOutcome(stepOutcome).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
