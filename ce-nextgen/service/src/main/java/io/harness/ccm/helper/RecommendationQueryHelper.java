/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterPropertiesDTO;

import lombok.experimental.UtilityClass;

@OwnedBy(CE)
@UtilityClass
public class RecommendationQueryHelper {
  public K8sRecommendationFilterDTO buildK8sRecommendationFilterDTO(CCMRecommendationFilterPropertiesDTO ccmFilter) {
    if (ccmFilter == null) {
      return null;
    }
    boolean areK8sRecommendationPropertiesPresent = ccmFilter.getK8sRecommendationFilterPropertiesDTO() != null;
    return K8sRecommendationFilterDTO.builder()
        .ids(
            areK8sRecommendationPropertiesPresent ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getIds() : null)
        .names(areK8sRecommendationPropertiesPresent ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getNames()
                                                     : null)
        .namespaces(areK8sRecommendationPropertiesPresent
                ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getNamespaces()
                : null)
        .clusterNames(areK8sRecommendationPropertiesPresent
                ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getClusterNames()
                : null)
        .resourceTypes(areK8sRecommendationPropertiesPresent
                ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getResourceTypes()
                : null)
        .recommendationStates(areK8sRecommendationPropertiesPresent
                ? ccmFilter.getK8sRecommendationFilterPropertiesDTO().getRecommendationStates()
                : null)
        .perspectiveFilters(ccmFilter.getPerspectiveFilters())
        .minSaving(ccmFilter.getMinSaving())
        .minCost(ccmFilter.getMinCost())
        .daysBack(ccmFilter.getDaysBack())
        .offset(ccmFilter.getOffset())
        .limit(ccmFilter.getLimit())
        .build();
  }
}
