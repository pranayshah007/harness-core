/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import io.leangen.graphql.annotations.GraphQLQuery;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkloadRecommendationDTO implements RecommendationDetailsDTO {
  String id;
  @GraphQLQuery(description = "use items.containerRecommendation", deprecationReason = "")
  @Deprecated
  Map<String, ContainerRecommendation> containerRecommendations;
  List<ContainerHistogramDTO> items;
  Cost lastDayCost;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
