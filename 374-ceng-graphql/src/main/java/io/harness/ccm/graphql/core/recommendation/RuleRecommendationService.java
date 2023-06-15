/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import io.harness.ccm.graphql.dto.recommendation.RuleRecommendationDTO;
import io.harness.ccm.views.entities.RuleRecommendation;
import io.harness.ccm.views.service.RuleExecutionService;

import com.google.inject.Inject;

public class RuleRecommendationService {
  @Inject private RuleExecutionService ruleExecutionService;

  public RuleRecommendationDTO getRuleRecommendation(String id, String accountIdentifier) {
    RuleRecommendation ruleRecommendation = ruleExecutionService.getRuleRecommendation(id, accountIdentifier);
    return RuleRecommendationDTO.builder()
        .accountId(accountIdentifier)
        .uuid(ruleRecommendation.getUuid())
        .name(ruleRecommendation.getName())
        .resourceType(ruleRecommendation.getResourceType())
        .actionType(ruleRecommendation.getActionType())
        .isValid(ruleRecommendation.getIsValid())
        .executions(ruleRecommendation.getExecutions())
        .jiraDetails(ruleRecommendation.getJiraDetails())
        .build();
  }
}
