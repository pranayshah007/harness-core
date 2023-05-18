/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsOverviewQueryV2;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterPropertiesDTO;
import io.harness.ccm.remote.beans.recommendation.FilterValuesDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RESTWrapperRecommendationOverviewTest extends CategoryTest {
  @Mock private RecommendationsOverviewQueryV2 overviewQueryV2;
  @Mock private CCMRbacHelper rbacHelper;
  @InjectMocks private RESTWrapperRecommendationOverview restWrapperRecommendationOverview;

  private ArgumentCaptor<ResolutionEnvironment> envCaptor;
  private ArgumentCaptor<K8sRecommendationFilterDTO> filterCaptor;
  private CCMRecommendationFilterPropertiesDTO ccmFilter;
  private ArgumentCaptor<RecommendationOverviewStats> recCaptor;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final GraphQLUtils graphQLUtils = new GraphQLUtils();

  @Before
  public void setUp() throws Exception {
    ccmFilter = CCMRecommendationFilterPropertiesDTO.builder().build();

    envCaptor = ArgumentCaptor.forClass(ResolutionEnvironment.class);
    filterCaptor = ArgumentCaptor.forClass(K8sRecommendationFilterDTO.class);
    recCaptor = ArgumentCaptor.forClass(RecommendationOverviewStats.class);
  }

  @After
  public void tearDown() throws Exception {
    assertThat(graphQLUtils.getAccountIdentifier(envCaptor.getValue())).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testList() throws Exception {
    RecommendationsDTO data = RecommendationsDTO.builder().build();

    when(overviewQueryV2.recommendations(filterCaptor.capture(), envCaptor.capture())).thenReturn(data);

    assertThat(restWrapperRecommendationOverview.list(ACCOUNT_ID, ccmFilter).getData()).isEqualTo(data);

    assertPaginatedFilter(filterCaptor.getValue());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testStats() throws Exception {
    RecommendationOverviewStats data = RecommendationOverviewStats.builder().build();

    when(overviewQueryV2.recommendationStats(filterCaptor.capture(), envCaptor.capture())).thenReturn(data);

    assertThat(restWrapperRecommendationOverview.stats(ACCOUNT_ID, ccmFilter).getData()).isEqualTo(data);

    assertNonPaginatedFilter(filterCaptor.getValue());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testCount() throws Exception {
    when(overviewQueryV2.count(recCaptor.capture(), envCaptor.capture())).thenReturn(10);

    assertThat(restWrapperRecommendationOverview.count(ACCOUNT_ID, ccmFilter).getData()).isEqualTo(10);

    Object object = envCaptor.getValue().dataFetchingEnvironment.getVariables().get("filter");
    assertThat(object).isInstanceOfSatisfying(K8sRecommendationFilterDTO.class, this::assertNonPaginatedFilter);

    verify(overviewQueryV2, times(0)).count(any(RecommendationsDTO.class), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testFilterStats() throws Exception {
    final List<FilterStatsDTO> filterStatsDTOList = Collections.singletonList(FilterStatsDTO.builder().build());

    when(overviewQueryV2.recommendationFilterStats(any(), filterCaptor.capture(), envCaptor.capture()))
        .thenReturn(filterStatsDTOList);

    FilterValuesDTO filterValuesDTO = FilterValuesDTO.builder().build();
    assertThat(restWrapperRecommendationOverview.filterStats(ACCOUNT_ID, filterValuesDTO).getData())
        .containsExactlyInAnyOrderElementsOf(filterStatsDTOList);

    assertNonPaginatedFilter(filterCaptor.getValue());
  }

  private void assertNonPaginatedFilter(@NotNull K8sRecommendationFilterDTO modifiedFilter) {
    assertThat(modifiedFilter.getLimit()).isNull();
    assertThat(modifiedFilter.getOffset()).isNull();
    assertThat(modifiedFilter.getMinSaving()).isEqualTo(0D);
  }

  private void assertPaginatedFilter(@NotNull K8sRecommendationFilterDTO modifiedFilter) {
    assertThat(modifiedFilter.getLimit()).isEqualTo(GraphQLUtils.DEFAULT_LIMIT);
    assertThat(modifiedFilter.getOffset()).isEqualTo(GraphQLUtils.DEFAULT_OFFSET);
    assertThat(modifiedFilter.getMinSaving()).isEqualTo(0D);
  }
}
