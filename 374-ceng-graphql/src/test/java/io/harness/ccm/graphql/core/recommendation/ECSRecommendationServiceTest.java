/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSPartialRecommendationHistogram;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSServiceRecommendation;
import io.harness.ccm.graphql.dto.recommendation.ContainerHistogramDTO;
import io.harness.ccm.graphql.dto.recommendation.ECSRecommendationDTO;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ECSRecommendationServiceTest extends CategoryTest {
  private static final String CLUSTER_ID = "clusterId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String SERVICE_ARN = "serviceArn";
  private static final String SERVICE_NAME = "serviceName";
  private static final String ID = "id0";

  private static final Instant refDate = Instant.now().truncatedTo(ChronoUnit.DAYS);
  private static final List<ECSPartialRecommendationHistogram> histograms = Arrays.asList(
      ECSPartialRecommendationHistogram.builder()
          .accountId(ACCOUNT_ID)
          .serviceArn(SERVICE_ARN)
          .serviceName(SERVICE_NAME)
          .clusterName(CLUSTER_NAME)
          .date(refDate.plus(Duration.ofDays(2)))
          .cpuHistogram(HistogramCheckpoint.builder().totalWeight(1).bucketWeights(ImmutableMap.of(0, 10000)).build())
          .memoryHistogram(
              HistogramCheckpoint.builder().totalWeight(1).bucketWeights(ImmutableMap.of(0, 10000)).build())
          .memoryPeak(1500000000L)
          .build(),
      ECSPartialRecommendationHistogram.builder()
          .accountId(ACCOUNT_ID)
          .serviceArn(SERVICE_ARN)
          .serviceName(SERVICE_NAME)
          .clusterName(CLUSTER_NAME)
          .date(refDate.plus(Duration.ofDays(3)))
          .cpuHistogram(HistogramCheckpoint.builder().totalWeight(1).bucketWeights(ImmutableMap.of(2, 10000)).build())
          .memoryHistogram(
              HistogramCheckpoint.builder().totalWeight(1).bucketWeights(ImmutableMap.of(0, 10000)).build())
          .memoryPeak(2000000000L)
          .build());

  private static final Cost cost = Cost.builder().cpu(BigDecimal.valueOf(100)).memory(BigDecimal.valueOf(100)).build();

  @Mock private ECSRecommendationDAO ecsRecommendationDAO;
  @InjectMocks private ECSRecommendationService ecsRecommendationService;

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void getWorkloadRecommendationByIdNotFound() {
    when(ecsRecommendationDAO.fetchECSRecommendationById(eq(ACCOUNT_ID), eq(ID))).thenReturn(Optional.empty());

    verify(ecsRecommendationDAO, times(0)).fetchPartialRecommendationHistograms(any(), any(), any(), any(), any());

    final ECSRecommendationDTO ecsRecommendationDTO = ecsRecommendationService.getECSRecommendationById(
        ACCOUNT_ID, ID, OffsetDateTime.now(), OffsetDateTime.now(), 0L);

    assertThat(ecsRecommendationDTO).isNotNull();
    assertThat(ecsRecommendationDTO.getLastDayCost()).isNull();
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void getWorkloadRecommendationByIdItemFound() {
    final ECSServiceRecommendation recommendation =
        ECSServiceRecommendation.builder()
            .accountId(ACCOUNT_ID)
            .clusterId(CLUSTER_ID)
            .serviceArn(SERVICE_ARN)
            .serviceName(SERVICE_NAME)
            .clusterName(CLUSTER_NAME)
            .uuid(ID)
            .lastDayCost(cost)
            .currentResourceRequirements(ImmutableMap.of(CPU, "1024", MEMORY, "1024M"))
            .build();

    when(ecsRecommendationDAO.fetchECSRecommendationById(eq(ACCOUNT_ID), eq(ID)))
        .thenReturn(Optional.of(recommendation));
    when(ecsRecommendationDAO.fetchPartialRecommendationHistograms(eq(ACCOUNT_ID), any(), any(), any(), any()))
        .thenReturn(histograms);

    final ECSRecommendationDTO ecsRecommendationDTO = ecsRecommendationService.getECSRecommendationById(
        ACCOUNT_ID, ID, OffsetDateTime.now(), OffsetDateTime.now(), 0L);

    verify(ecsRecommendationDAO, times(1))
        .fetchPartialRecommendationHistograms(eq(ACCOUNT_ID), any(), any(), any(), any());

    assertThat(ecsRecommendationDTO).isNotNull();
    assertThat(ecsRecommendationDTO.getLastDayCost()).isEqualTo(cost);

    final ContainerHistogramDTO.HistogramExp cpuHistogram = ecsRecommendationDTO.getCpuHistogram();
    assertThat(cpuHistogram.getMinBucket()).isEqualTo(0);
    assertThat(cpuHistogram.getMaxBucket()).isEqualTo(2);
    assertThat(cpuHistogram.getTotalWeight()).isEqualTo(2.0);
    assertThat(cpuHistogram.getNumBuckets()).isEqualTo(3);
    assertThat(cpuHistogram.getBucketWeights()[0]).isEqualTo(1.0);
    assertThat(cpuHistogram.getBucketWeights()[2]).isEqualTo(1.0);

    final ContainerHistogramDTO.HistogramExp memoryHistogram = ecsRecommendationDTO.getMemoryHistogram();
    assertThat(memoryHistogram.getMinBucket()).isEqualTo(0);
    assertThat(memoryHistogram.getMaxBucket()).isEqualTo(0);
    assertThat(memoryHistogram.getTotalWeight()).isEqualTo(2.0);
    assertThat(memoryHistogram.getNumBuckets()).isEqualTo(1);
    assertThat(memoryHistogram.getBucketWeights()[0]).isEqualTo(2.0);
  }
}
