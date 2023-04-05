/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.recommendation;

import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties
@Schema(name = "K8sRecommendationFilter", description = "Common filter for all Cloud Cost Recommendation APIs.")
public class K8sRecommendationFilterDTO {
  List<String> ids;
  List<String> names;
  List<String> namespaces;
  List<String> clusterNames;
  List<ResourceType> resourceTypes;
  List<RecommendationState> recommendationStates;

  // generic field filter, supporting perspective
  @Schema(name = "perspectiveFilters", description = "Get Recommendations for a perspective")
  List<QLCEViewFilterWrapper> perspectiveFilters;

  Double minSaving;
  Double minCost;

  Long daysBack;
  Long offset;
  Long limit;
}
