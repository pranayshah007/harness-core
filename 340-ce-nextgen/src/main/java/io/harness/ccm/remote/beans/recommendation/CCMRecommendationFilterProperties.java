/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.recommendation;

import static io.harness.filter.FilterConstants.CCM_RECOMMENDATION_FILTER;

import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.filter.entity.FilterProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("CCMRecommendationFilterProperties")
@JsonTypeName(CCM_RECOMMENDATION_FILTER)
public class CCMRecommendationFilterProperties extends FilterProperties {
  K8sRecommendationFilterPropertiesDTO k8sRecommendationFilterPropertiesDTO;
  List<QLCEViewFilterWrapper> perspectiveFilters;

  Double minSaving;
  Double minCost;

  Long offset;
  Long limit;
}
