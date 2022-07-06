/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.step.outcome;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicyData;
import io.harness.utils.IdentifierRefHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class PolicyStepOutcomeMapper {
  public PolicyStepOutcome toOutcome(OpaEvaluationResponseHolder evaluationResponse) {
    // todo(@NamanVerma): Change field names in OpaEvaluationResponseHolder to follow Java naming conventions
    List<OpaPolicySetEvaluationResponse> policySetsResponse =
        evaluationResponse.getDetails() == null ? Collections.emptyList() : evaluationResponse.getDetails();
    Map<String, PolicySetOutcome> policySetOutcomeMap =
        policySetsResponse.stream()
            .map(PolicyStepOutcomeMapper::toPolicySetOutcome)
            .collect(Collectors.toMap(PolicySetOutcome::getIdentifier, Function.identity()));
    return PolicyStepOutcome.builder()
        .evaluationId(evaluationResponse.getId())
        .status(evaluationResponse.getStatus())
        .policySetDetails(policySetOutcomeMap)
        .build();
  }

  PolicySetOutcome toPolicySetOutcome(OpaPolicySetEvaluationResponse response) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        response.getIdentifier(), response.getAccount_id(), response.getOrg_id(), response.getProject_id());
    String scopedIdentifier = identifierRef.buildScopedIdentifier();

    List<OpaPolicyEvaluationResponse> policyResponses =
        response.getDetails() == null ? Collections.emptyList() : response.getDetails();
    Map<String, PolicyOutcome> policyOutcomeMap =
        policyResponses.stream()
            .map(PolicyStepOutcomeMapper::toPolicyOutcome)
            .collect(Collectors.toMap(PolicyOutcome::getIdentifier, Function.identity()));

    return PolicySetOutcome.builder()
        .identifier(scopedIdentifier)
        .name(response.getName())
        .status(response.getStatus())
        .policyDetails(policyOutcomeMap)
        .build();
  }

  PolicyOutcome toPolicyOutcome(OpaPolicyEvaluationResponse response) {
    PolicyData responsePolicy = response.getPolicy();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(responsePolicy.getIdentifier(),
            responsePolicy.getAccount_id(), responsePolicy.getOrg_id(), responsePolicy.getProject_id());
    String scopedIdentifier = identifierRef.buildScopedIdentifier();
    return PolicyOutcome.builder()
        .status(response.getStatus())
        .identifier(scopedIdentifier)
        .name(responsePolicy.getName())
        .denyMessages(response.getDeny_messages() == null ? Collections.emptyList() : response.getDeny_messages())
        .error(response.getError() == null ? "" : response.getError())
        .build();
  }
}
