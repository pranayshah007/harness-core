/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;

import io.leangen.graphql.annotations.GraphQLNonNull;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "RecommendationItem", description = "A single Cloud Cost Recommendation entity.")
public class RecommendationItemDTO {
  @GraphQLNonNull @NotNull String id;
  String clusterName;
  String namespace;
  String resourceName;
  Double monthlySaving;
  Double monthlyCost;
  @GraphQLNonNull @NotNull ResourceType resourceType;
  RecommendationState recommendationState;
  String jiraConnectorRef;
  String jiraIssueKey;
  String jiraStatus;
  String servicenowConnectorRef;
  String servicenowIssueKey;
  String servicenowIssueStatus;
  RecommendationDetailsDTO recommendationDetails;
  String perspectiveId;
  String perspectiveName;
  String cloudProvider;
  String governanceRuleId;
}
