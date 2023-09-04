/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.PolicyConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
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
import io.harness.steps.StepUtils;
import io.harness.steps.policy.PolicyStepConstants;
import io.harness.steps.policy.PolicyStepSpecParameters;
import io.harness.steps.policy.custom.CustomPolicyStepSpec;
import io.harness.utils.PolicyEvalUtils;
import io.harness.utils.PolicyStepOutcome;
import io.harness.utils.PolicyStepOutcomeMapper;

import software.wings.beans.LogColor;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PolicyStep implements SyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.POLICY_STEP_TYPE;
  @Inject OpaServiceClient opaServiceClient;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>(Collections.singleton("Execute")));
  }

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
      log.error(PolicyConstants.OPA_EVALUATION_ERROR_MSG, ex);
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

    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, ambiance, policyStepSpecParameters.COMMAND_UNIT, true);
    UnitProgress.Builder unitProgressBuilder = UnitProgress.newBuilder()
                                                   .setUnitName(policyStepSpecParameters.COMMAND_UNIT)
                                                   .setStatus(UnitStatus.SUCCESS)
                                                   .setStartTime(System.currentTimeMillis())
                                                   .setEndTime(System.currentTimeMillis());

    if (outcome != null && outcome.getPolicySetDetails() != null) {
      handlePolicyStepConsoleLog(logCallback, outcome, unitProgressBuilder);
    }

    if (opaEvaluationResponseHolder.getStatus().equals(OpaConstants.OPA_STATUS_ERROR)) {
      unitProgressBuilder.setStatus(UnitStatus.FAILURE);
      String errorMessage = PolicyEvalUtils.buildPolicyEvaluationFailureMessage(opaEvaluationResponseHolder);
      // No need to close the client explicitly, since if command execution status is terminal, save execution log
      // automatically closes the connection.
      saveExecutionLog(logCallback, "Policy step failed", LogLevel.INFO, CommandExecutionStatus.FAILURE);
      return PolicyEvalUtils.buildFailureStepResponse(ErrorCode.POLICY_EVALUATION_FAILURE, errorMessage,
          FailureType.POLICY_EVALUATION_FAILURE, stepOutcome, unitProgressBuilder);
    }
    // No need to close the client explicitly, since if command execution status is terminal, save execution log
    // automatically closes the connection.
    saveExecutionLog(logCallback, "Policy step succeeded", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .unitProgressList(Collections.singletonList(unitProgressBuilder.build()))
        .build();
  }

  private void handlePolicyStepConsoleLog(
      NGLogCallback logCallback, PolicyStepOutcome outcome, UnitProgress.Builder unitProgressBuilder) {
    outcome.getPolicySetDetails().forEach((policySetName, policySetOutcome) -> {
      saveExecutionLog(
          logCallback, format("POLICYSET: \"%s\" STATUS : \"%s\"", policySetName, policySetOutcome.getStatus()));
      policySetOutcome.getPolicyDetails().forEach((policyName, policyOutcome) -> {
        String denyMessages = String.join(", ", policyOutcome.getDenyMessages());
        LogColor logColor;
        switch (policyOutcome.getStatus()) {
          case OpaConstants.OPA_STATUS_PASS:
            logColor = LogColor.Green;
            break;
          case OpaConstants.OPA_STATUS_WARNING:
            logColor = LogColor.Orange;
            break;
          case OpaConstants.OPA_STATUS_ERROR:
            logColor = LogColor.Red;
            unitProgressBuilder.setStatus(UnitStatus.FAILURE);
            break;
          default:
            logColor = LogColor.White;
            break;
        }
        saveExecutionLog(logCallback,
            color(format("POLICY: \"%s\", STATUS: \"%s\", DETAILS: \"%s\"", policyName, policyOutcome.getStatus(),
                      denyMessages),
                logColor));
      });
    });
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  void saveExecutionLog(NGLogCallback logCallback, String line) {
    saveExecutionLog(logCallback, line, LogLevel.INFO, CommandExecutionStatus.RUNNING);
  }

  void saveExecutionLog(
      NGLogCallback logCallback, String line, LogLevel logLevel, CommandExecutionStatus commandExecutionStatus) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, logLevel, commandExecutionStatus);
    }
  }
}
