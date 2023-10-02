/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CE)
public class RecommendationCrudServiceImplTest extends CategoryTest {
  @Mock private K8sRecommendationDAO k8sRecommendationDAO;
  @InjectMocks private RecommendationCrudServiceImpl recommendationCrudService;

  private static final String CLUSTER_ID = "clusterId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String UUID = "uuid";
  private static final Instant NOW = Instant.now();
  private static final ResourceId RESOURCE_ID =
      ResourceId.builder().accountId("accountId").clusterId(CLUSTER_ID).build();

  private ArgumentCaptor<Double> monthlyCostCaptor;
  private ArgumentCaptor<Double> monthlySavingCaptor;

  @Before
  public void setUp() throws Exception {
    monthlyCostCaptor = ArgumentCaptor.forClass(Double.class);
    monthlySavingCaptor = ArgumentCaptor.forClass(Double.class);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testUpsertWorkloadRecommendation() throws Exception {
    doNothing()
        .when(k8sRecommendationDAO)
        .upsertCeRecommendation(eq(UUID), eq(RESOURCE_ID), eq(CLUSTER_NAME), monthlyCostCaptor.capture(),
            monthlySavingCaptor.capture(), eq(true), eq(NOW));

    recommendationCrudService.upsertWorkloadRecommendation(
        UUID, RESOURCE_ID, CLUSTER_NAME, createValidRecommendation());

    verify(k8sRecommendationDAO, times(1));

    assertThat(monthlyCostCaptor.getValue()).isEqualTo(330D);
    assertThat(monthlySavingCaptor.getValue()).isEqualTo(10D);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testUpsertWorkloadRecommendationLastDayCostNotAvailable() throws Exception {
    doNothing()
        .when(k8sRecommendationDAO)
        .upsertCeRecommendation(eq(UUID), eq(RESOURCE_ID), eq(CLUSTER_NAME), monthlyCostCaptor.capture(),
            monthlySavingCaptor.capture(), eq(false), eq(Instant.EPOCH));

    recommendationCrudService.upsertWorkloadRecommendation(
        UUID, RESOURCE_ID, CLUSTER_NAME, K8sWorkloadRecommendation.builder().lastDayCostAvailable(false).build());

    verify(k8sRecommendationDAO, times(1));

    assertThat(monthlyCostCaptor.getValue()).isNull();
    assertThat(monthlySavingCaptor.getValue()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testUpsertNodeRecommendation() throws Exception {
    final NodePoolId nodePoolId = NodePoolId.builder().clusterid(CLUSTER_ID).build();
    final RecommendationOverviewStats stats =
        RecommendationOverviewStats.builder().totalMonthlySaving(1D).totalMonthlyCost(2D).build();

    ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

    JobConstants jobConstants = mock(JobConstants.class);
    when(jobConstants.getJobEndTime()).thenReturn(NOW.toEpochMilli());

    doNothing().when(k8sRecommendationDAO).upsertCeRecommendation(any(), any(), any(), any(), any(), any(), any());

    recommendationCrudService.upsertNodeRecommendation(UUID, jobConstants, nodePoolId, CLUSTER_NAME, stats, "");
    verify(k8sRecommendationDAO)
        .upsertCeRecommendation(any(), any(), any(), stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getValue()).isNotNull().isEqualTo(CLUSTER_NAME);
  }

  private static K8sWorkloadRecommendation createValidRecommendation() {
    return K8sWorkloadRecommendation.builder()
        .lastDayCostAvailable(true)
        .lastDayCost(Cost.builder().cpu(BigDecimal.TEN).memory(BigDecimal.ONE).build())
        .validRecommendation(true)
        .estimatedSavings(BigDecimal.TEN)
        .lastReceivedUtilDataAt(NOW)
        .numDays(1)
        .build();
  }
}
