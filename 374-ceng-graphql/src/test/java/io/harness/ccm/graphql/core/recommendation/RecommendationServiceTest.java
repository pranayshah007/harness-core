/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.dao.recommendation.K8sRecommendationDAO;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.rule.Owner;
import io.harness.timescaledb.Tables;
import io.harness.timescaledb.tables.pojos.CeRecommendations;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.assertj.core.data.Offset;
import org.jooq.impl.DSL;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RecommendationServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String NAME = "name";
  private static final String CLUSTER_NAME = "clusterName";
  private static final Double MONTHLY_COST = 100D;
  private static final Double MONTHLY_SAVING = 40D;
  private static final String ID = "id0";
  private static final Offset<Double> DOUBLE_OFFSET = offset(0.001);

  @Mock K8sRecommendationDAO k8sRecommendationDAO;
  @InjectMocks RecommendationService recommendationService;

  private static final CeRecommendations ceRecommendation = new CeRecommendations()
                                                                .setId(ID)
                                                                .setName(NAME)
                                                                .setNamespace(NAME)
                                                                .setMonthlycost(MONTHLY_COST)
                                                                .setMonthlysaving(MONTHLY_SAVING)
                                                                .setClustername(CLUSTER_NAME)
                                                                .setResourcetype(ResourceType.WORKLOAD.name())
                                                                .setAccountid(ACCOUNT_ID)
                                                                .setIsvalid(true)
                                                                .setLastprocessedat(OffsetDateTime.now())
                                                                .setUpdatedat(OffsetDateTime.now())
                                                                .setJiraconnectorref("TestJiraConnector")
                                                                .setJiraissuekey("TJI-0000")
                                                                .setJirastatus("Todo")
                                                                .setRecommendationstate("OPEN");

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListAllReturnsNoItem() {
    when(k8sRecommendationDAO.fetchRecommendationsOverview(eq(ACCOUNT_ID), any(), eq(0L), eq(10L)))
        .thenReturn(Collections.emptyList());

    final List<RecommendationItemDTO> allRecommendations = recommendationService.listAll(ACCOUNT_ID, null, 0L, 10L);

    assertThat(allRecommendations).isNotNull();
    assertThat(allRecommendations).isEmpty();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetFilterStats() {
    when(k8sRecommendationDAO.getDistinctStringValues(
             eq(ACCOUNT_ID), any(), eq(Tables.CE_RECOMMENDATIONS.RESOURCETYPE), eq(Tables.CE_RECOMMENDATIONS)))
        .thenReturn(ImmutableList.of("v1", "v2"));

    List<FilterStatsDTO> result = recommendationService.getFilterStats(
        ACCOUNT_ID, null, Collections.singletonList("resourceType"), Tables.CE_RECOMMENDATIONS);

    verify(k8sRecommendationDAO, times(1))
        .getDistinctStringValues(
            eq(ACCOUNT_ID), any(), eq(Tables.CE_RECOMMENDATIONS.RESOURCETYPE), eq(Tables.CE_RECOMMENDATIONS));

    assertThat(result).isNotEmpty();
    assertThat(result.get(0).getKey()).isEqualTo("resourceType");
    assertThat(result.get(0).getValues()).containsExactlyInAnyOrder("v1", "v2");
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testListAllReturnsOneItem() {
    when(k8sRecommendationDAO.fetchRecommendationsOverview(eq(ACCOUNT_ID), any(), eq(0L), eq(10L)))
        .thenReturn(ImmutableList.of(ceRecommendation));

    final List<RecommendationItemDTO> allRecommendations = recommendationService.listAll(ACCOUNT_ID, null, 0L, 10L);

    assertThat(allRecommendations).isNotNull().hasSize(1);
    assertThat(allRecommendations.get(0).getId()).isEqualTo(ID);
    assertThat(allRecommendations.get(0).getResourceType()).isEqualTo(ResourceType.WORKLOAD);
    assertThat(allRecommendations.get(0).getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(allRecommendations.get(0).getRecommendationDetails()).isNull();
    assertThat(allRecommendations.get(0).getResourceName()).isEqualTo(NAME);
    assertThat(allRecommendations.get(0).getMonthlyCost()).isCloseTo(MONTHLY_COST, DOUBLE_OFFSET);
    assertThat(allRecommendations.get(0).getMonthlySaving()).isCloseTo(MONTHLY_SAVING, DOUBLE_OFFSET);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetStats() {
    final RecommendationOverviewStats expectedStats =
        RecommendationOverviewStats.builder().totalMonthlySaving(MONTHLY_SAVING).totalMonthlyCost(MONTHLY_COST).build();

    when(k8sRecommendationDAO.fetchRecommendationsOverviewStats(eq(ACCOUNT_ID), eq(DSL.noCondition())))
        .thenReturn(expectedStats);

    final RecommendationOverviewStats stats = recommendationService.getStats(ACCOUNT_ID, DSL.noCondition());
    assertThat(stats.getTotalMonthlyCost()).isEqualTo(MONTHLY_COST);
    assertThat(stats.getTotalMonthlySaving()).isEqualTo(MONTHLY_SAVING);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetRecommendationsCount() throws Exception {
    when(k8sRecommendationDAO.fetchRecommendationsCount(eq(ACCOUNT_ID), eq(DSL.noCondition()))).thenReturn(10);

    assertThat(recommendationService.getRecommendationsCount(ACCOUNT_ID, DSL.noCondition())).isEqualTo(10);

    verify(k8sRecommendationDAO, times(1)).fetchRecommendationsCount(any(), any());
  }
}
