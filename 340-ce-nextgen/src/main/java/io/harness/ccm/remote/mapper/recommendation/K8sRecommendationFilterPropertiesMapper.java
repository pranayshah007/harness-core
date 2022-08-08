/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.mapper.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.remote.beans.recommendation.K8sRecommendationFilterProperties;
import io.harness.ccm.remote.beans.recommendation.K8sRecommendationFilterPropertiesDTO;

import lombok.experimental.UtilityClass;
import org.modelmapper.ModelMapper;

@OwnedBy(CE)
@UtilityClass
public class K8sRecommendationFilterPropertiesMapper {
  public K8sRecommendationFilterPropertiesDTO writeDTO(
      K8sRecommendationFilterProperties k8sRecommendationFilterProperties) {
    ModelMapper modelMapper = new ModelMapper();
    return modelMapper.map(k8sRecommendationFilterProperties, K8sRecommendationFilterPropertiesDTO.class);
  }

  public K8sRecommendationFilterProperties toEntity(
      K8sRecommendationFilterPropertiesDTO k8sRecommendationFilterPropertiesDTO) {
    ModelMapper modelMapper = new ModelMapper();
    return modelMapper.map(k8sRecommendationFilterPropertiesDTO, K8sRecommendationFilterProperties.class);
  }
}
