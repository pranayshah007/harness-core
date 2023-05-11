/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.filter.FilterConstants.CCM_RECOMMENDATION_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(CCM_RECOMMENDATION_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("CCMRecommendationFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CE)
@Schema(name = "CCMRecommendationFilterProperties",
    description = "Properties of the CCMRecommendation Filter defined in Harness")
public class CCMRecommendationFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(name = "k8sRecommendationFilterPropertiesDTO", description = "Filter fields specific to K8s Recommendations")
  K8sRecommendationFilterPropertiesDTO k8sRecommendationFilterPropertiesDTO;

  @Schema(name = "perspectiveFilters", description = "Get Recommendations for a perspective")
  List<QLCEViewFilterWrapper> perspectiveFilters;

  @Schema(name = "minSaving", description = "Fetch recommendations with Saving more than minSaving") Double minSaving;
  @Schema(name = "minCost", description = "Fetch recommendations with Cost more than minCost") Double minCost;
  @Schema(name = "daysBack", description = "Fetch recommendations generated in last daysBack days") Long daysBack;

  @Schema(name = "offset", description = "Query Offset") Long offset;
  @Schema(name = "limit", description = "Query Limit") Long limit;

  @Override
  @Schema(type = "string", allowableValues = {"CCMRecommendation"})
  public FilterType getFilterType() {
    return FilterType.CCMRECOMMENDATION;
  }
}
