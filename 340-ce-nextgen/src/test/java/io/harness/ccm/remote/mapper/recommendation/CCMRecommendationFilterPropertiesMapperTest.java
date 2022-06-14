/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.mapper.recommendation;

import static io.harness.rule.OwnerRule.ABHIJEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterProperties;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterPropertiesDTO;
import io.harness.ccm.remote.beans.recommendation.K8sRecommendationFilterPropertiesDTO;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CCMRecommendationFilterPropertiesMapperTest extends CategoryTest {
  List<String> ids = Arrays.asList("id1");
  List<String> names = Arrays.asList("name1");
  List<String> namespaces = Arrays.asList("namespace1");
  List<String> clusterNames = Arrays.asList("clusterName1");
  List<ResourceType> resourceTypes = Arrays.asList(ResourceType.WORKLOAD);
  K8sRecommendationFilterPropertiesDTO k8sRecommendationFilterPropertiesDTO =
      K8sRecommendationFilterPropertiesDTO.builder()
          .ids(ids)
          .names(names)
          .namespaces(namespaces)
          .clusterNames(clusterNames)
          .resourceTypes(resourceTypes)
          .build();
  Double minSaving = 123.45;
  Double minCost = 1234.5;
  List<QLCEViewFilterWrapper> perspectiveFilters = Arrays.asList(QLCEViewFilterWrapper.builder().build());
  @InjectMocks CCMRecommendationFilterPropertiesMapper ccmRecommendationFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHIJEET)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    FilterProperties filterProperties = CCMRecommendationFilterProperties.builder()
                                            .k8sRecommendationFilterPropertiesDTO(k8sRecommendationFilterPropertiesDTO)
                                            .perspectiveFilters(perspectiveFilters)
                                            .minSaving(minSaving)
                                            .minCost(minCost)
                                            .build();
    CCMRecommendationFilterPropertiesDTO filterPropertiesDTO =
        (CCMRecommendationFilterPropertiesDTO) ccmRecommendationFilterPropertiesMapper.writeDTO(filterProperties);
    assertThat(filterPropertiesDTO.getFilterType()).isEqualTo(FilterType.CCMRECOMMENDATION);
    assertThat(filterPropertiesDTO.getK8sRecommendationFilterPropertiesDTO())
        .isEqualTo(k8sRecommendationFilterPropertiesDTO);
    assertThat(filterPropertiesDTO.getPerspectiveFilters()).isEqualTo(perspectiveFilters);
    assertThat(filterPropertiesDTO.getMinSaving()).isEqualTo(minSaving);
    assertThat(filterPropertiesDTO.getMinCost()).isEqualTo(minCost);
  }

  @Test
  @Owner(developers = ABHIJEET)
  @Category(UnitTests.class)
  public void testToEntity() {
    FilterPropertiesDTO filterPropertiesDTO =
        CCMRecommendationFilterPropertiesDTO.builder()
            .k8sRecommendationFilterPropertiesDTO(k8sRecommendationFilterPropertiesDTO)
            .perspectiveFilters(perspectiveFilters)
            .minSaving(minSaving)
            .minCost(minCost)
            .build();
    CCMRecommendationFilterProperties filterProperties =
        (CCMRecommendationFilterProperties) ccmRecommendationFilterPropertiesMapper.toEntity(filterPropertiesDTO);
    assertThat(filterProperties.getType()).isEqualTo(FilterType.CCMRECOMMENDATION);
    assertThat(filterProperties.getK8sRecommendationFilterPropertiesDTO())
        .isEqualTo(k8sRecommendationFilterPropertiesDTO);
    assertThat(filterProperties.getPerspectiveFilters()).isEqualTo(perspectiveFilters);
    assertThat(filterProperties.getMinSaving()).isEqualTo(minSaving);
    assertThat(filterProperties.getMinCost()).isEqualTo(minCost);
  }
}