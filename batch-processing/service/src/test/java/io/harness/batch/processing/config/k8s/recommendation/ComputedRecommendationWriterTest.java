/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.batch.processing.tasklet.util.ClusterHelper;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudService;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class ComputedRecommendationWriterTest extends CategoryTest {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";
  public static final String CLUSTER_ID = "CLUSTER_ID";
  public static final String CLUSTER_NAME = "CLUSTER_NAME";
  public static final String NAMESPACE = "NAMESPACE";
  public static final String WORKLOAD_NAME = "WORKLOAD_NAME";
  public static final String WORKLOAD_TYPE = "WORKLOAD_TYPE";
  private static final String UUID = "UUID";

  public static final Instant JOB_START_DATE = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(1));

  private ComputedRecommendationWriter computedRecommendationWriter;

  private WorkloadCostService workloadCostService;
  private WorkloadRecommendationDao workloadRecommendationDao;

  private ArgumentCaptor<K8sWorkloadRecommendation> captor;
  private ArgumentCaptor<String> stringCaptor;
  private WorkloadRepository workloadRepository;
  private K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  private RecommendationCrudService recommendationCrudService;
  private ClusterHelper clusterHelper;

  @Before
  public void setUp() throws Exception {
    workloadCostService = mock(WorkloadCostService.class);
    workloadRecommendationDao = mock(WorkloadRecommendationDao.class);
    workloadRepository = mock(WorkloadRepository.class);
    k8sLabelServiceInfoFetcher = mock(K8sLabelServiceInfoFetcher.class);
    recommendationCrudService = mock(RecommendationCrudService.class);
    clusterHelper = mock(ClusterHelper.class);

    when(workloadRecommendationDao.save(any(K8sWorkloadRecommendation.class))).thenReturn(UUID);
    when(workloadRepository.getWorkload(any())).thenReturn(Optional.empty());
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(anyString(), anyMap()))
        .thenReturn(Optional.empty());
    doNothing().when(recommendationCrudService).upsertWorkloadRecommendation(any(), any(), any(), any());
    when(clusterHelper.fetchClusterName(eq(CLUSTER_ID))).thenReturn(CLUSTER_NAME);

    computedRecommendationWriter = new ComputedRecommendationWriter(workloadRecommendationDao, workloadCostService,
        workloadRepository, k8sLabelServiceInfoFetcher, recommendationCrudService, clusterHelper, JOB_START_DATE);
    captor = ArgumentCaptor.forClass(K8sWorkloadRecommendation.class);
    stringCaptor = ArgumentCaptor.forClass(String.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeResourceChangePercent() throws Exception {
    assertThat(
        ComputedRecommendationWriter.resourceChangePercent(
            ImmutableMap.of("ctr1",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder().request("cpu", "20m").request("memory", "100Mi").build())
                    .percentileBased(ImmutableMap.of(
                        "p90", ResourceRequirement.builder().request("cpu", "30m").request("memory", "10Mi").build()))
                    .build(),
                "ctr2",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder().request("cpu", "0.25").request("memory", "100Mi").build())
                    .percentileBased(ImmutableMap.of(
                        "p90", ResourceRequirement.builder().request("cpu", "0.5").request("memory", "100Mi").build()))
                    .build()),
            "cpu"))
        // cpu change is 20m+0.25->30m+0.5 => 270m->530m => 96.3%
        .isEqualByComparingTo(BigDecimal.valueOf(0.963));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeResourceChangePercentWhenOnlySomeContainersHaveRequests() throws Exception {
    assertThat(
        ComputedRecommendationWriter.resourceChangePercent(
            ImmutableMap.of("ctr1",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder().request("cpu", "20m").request("memory", "100Mi").build())
                    .percentileBased(ImmutableMap.of(
                        "p90", ResourceRequirement.builder().request("cpu", "30m").request("memory", "10Mi").build()))
                    .build(),
                "ctr2",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder().request("memory", "100Mi").build())
                    // don't use this recommendation in change percent, as there's no current cpu here.
                    .percentileBased(ImmutableMap.of(
                        "p90", ResourceRequirement.builder().request("cpu", "0.5").request("memory", "100Mi").build()))
                    .build()),
            "cpu"))
        // cpu change is 20m->30m => 50%
        .isEqualByComparingTo(BigDecimal.valueOf(0.5));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeResourceChangePercentAsNullWhenNoContainersHaveRequests() throws Exception {
    assertThat(ComputedRecommendationWriter.resourceChangePercent(
                   ImmutableMap.of("ctr1",
                       ContainerRecommendation.builder()
                           .current(ResourceRequirement.builder().request("memory", "100Mi").build())
                           .percentileBased(ImmutableMap.of("p90",
                               ResourceRequirement.builder().request("cpu", "30m").request("memory", "10Mi").build()))
                           .build(),
                       "ctr2",
                       ContainerRecommendation.builder()
                           .current(ResourceRequirement.builder().request("memory", "100Mi").build())
                           // don't use this recommendation in change percent, as there's no current cpu here.
                           .percentileBased(ImmutableMap.of("p90",
                               ResourceRequirement.builder().request("cpu", "0.5").request("memory", "100Mi").build()))
                           .build()),
                   "cpu"))
        .isNull();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeResourceChangePercentAsZeroWhenNoDifferenceInCurrentAndRecommendation() throws Exception {
    ImmutableMap<String, ContainerRecommendation> containerRecommendations = ImmutableMap.of("ctr1",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "30m").request("memory", "10Mi").build())
            .percentileBased(ImmutableMap.of(
                "p90", ResourceRequirement.builder().request("cpu", "30m").request("memory", "10Mi").build()))
            .build());
    assertThat(ComputedRecommendationWriter.resourceChangePercent(containerRecommendations, "cpu")).isZero();
    assertThat(ComputedRecommendationWriter.resourceChangePercent(containerRecommendations, "memory")).isZero();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldEstimateMonthlySavings() throws Exception {
    ImmutableMap<String, ContainerRecommendation> containerRecommendations = ImmutableMap.of("ctr1",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "20m").request("memory", "100Mi").build())
            .percentileBased(ImmutableMap.of(
                "p90", ResourceRequirement.builder().request("cpu", "10m").request("memory", "10Mi").build()))
            .build(),
        "ctr2",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "0.75").request("memory", "100Mi").build())
            .percentileBased(ImmutableMap.of(
                "p90", ResourceRequirement.builder().request("cpu", "0.5").request("memory", "75Mi").build()))
            .build());

    // cpu change: ((10m+0.5)-(20m+0.75))/(20m+0.75) = (510m-770m)/770m = -0.338
    assertThat(ComputedRecommendationWriter.resourceChangePercent(containerRecommendations, "cpu"))
        .isEqualTo(BigDecimal.valueOf(-0.338));
    // mem change: ((10Mi+75Mi)-(100Mi+100Mi))/(100Mi+100Mi) = (85Mi-200Mi)/200Mi = -0.575
    assertThat(ComputedRecommendationWriter.resourceChangePercent(containerRecommendations, "memory"))
        .isEqualTo(BigDecimal.valueOf(-0.575));

    // last day's cpu & memory total cost
    Cost lastDayCost = Cost.builder().cpu(BigDecimal.valueOf(3.422)).memory(BigDecimal.valueOf(4.234)).build();
    assertThat(computedRecommendationWriter.estimateMonthlySavings(containerRecommendations, lastDayCost))
        // dailyChange: 3.422*(-0.338) + 4.234*(-0.575) = -3.591
        // monthlySavings = -3.591 * -30 = 107.74
        .isEqualTo(BigDecimal.valueOf(107.74));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testCopyExtendedResources() throws Exception {
    ResourceRequirement current = ResourceRequirement.builder()
                                      .request("cpu", "1")
                                      .request("nvidia.com/gpu", "1")
                                      .limit("nvidia.com/gpu", "2")
                                      .build();
    ResourceRequirement recommended = ResourceRequirement.builder()
                                          .request("cpu", "0.25")
                                          .request("memory", "1G")
                                          .limit("cpu", "1")
                                          .limit("memory", "2G")
                                          .build();
    recommended = ComputedRecommendationWriter.copyExtendedResources(current, recommended);
    assertThat(recommended)
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "0.25")
                       .request("memory", "1G")
                       .limit("cpu", "1")
                       .limit("memory", "2G")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testCopyExtendedResourcesNulls() throws Exception {
    ResourceRequirement current = ResourceRequirement.builder().build();
    ResourceRequirement recommended = ResourceRequirement.builder()
                                          .request("cpu", "0.25")
                                          .request("memory", "1G")
                                          .limit("cpu", "1")
                                          .limit("memory", "2G")
                                          .build();
    recommended = ComputedRecommendationWriter.copyExtendedResources(current, recommended);
    assertThat(recommended)
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "0.25")
                       .request("memory", "1G")
                       .limit("cpu", "1")
                       .limit("memory", "2G")
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeRecommendationsAndSavingsOnWrite() throws Exception {
    List<K8sWorkloadRecommendation> recommendations =
        ImmutableList.of(K8sWorkloadRecommendation.builder()
                             .dirty(true)
                             .accountId(ACCOUNT_ID)
                             .clusterId(CLUSTER_ID)
                             .workloadType(WORKLOAD_TYPE)
                             .namespace(NAMESPACE)
                             .workloadName(WORKLOAD_NAME)
                             .lastReceivedUtilDataAt(Instant.EPOCH)
                             .containerRecommendation("harness-example",
                                 ContainerRecommendation.builder()
                                     .current(ResourceRequirement.builder()
                                                  .request("cpu", "1")
                                                  .limit("cpu", "1")
                                                  .request("memory", "1536Mi")
                                                  .limit("memory", "1536Mi")
                                                  .request("nvidia.com/gpu", "1")
                                                  .limit("nvidia.com/gpu", "2")
                                                  .build())
                                     .build())
                             .containerCheckpoint("harness-example", createContainerCheckpoint())
                             .build());
    when(workloadCostService.getLastAvailableDayCost(eq(ResourceId.builder()
                                                             .accountId(ACCOUNT_ID)
                                                             .clusterId(CLUSTER_ID)
                                                             .namespace(NAMESPACE)
                                                             .kind(WORKLOAD_TYPE)
                                                             .name(WORKLOAD_NAME)
                                                             .build()),
             eq(JOB_START_DATE.minus(Duration.ofDays(7)))))
        .thenReturn(Cost.builder().cpu(BigDecimal.valueOf(3.422)).memory(BigDecimal.valueOf(4.234)).build());

    when(workloadRecommendationDao.fetchPartialRecommendationHistogramForWorkload(any(), any(), any()))
        .thenReturn(Collections.singletonList(
            PartialRecommendationHistogram.builder()
                .accountId(ACCOUNT_ID)
                .clusterId(CLUSTER_ID)
                .namespace(NAMESPACE)
                .workloadName(WORKLOAD_NAME)
                .workloadType(WORKLOAD_TYPE)
                .date(JOB_START_DATE)
                .containerCheckpoints(ImmutableMap.of("harness-example", createContainerCheckpoint()))
                .build()));

    computedRecommendationWriter.write(recommendations);

    verify(workloadRecommendationDao).save(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);
    K8sWorkloadRecommendation recommendation = captor.getValue();

    assertThat(recommendation.isDirty()).isFalse();
    assertThat(recommendation.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(recommendation.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(recommendation.getWorkloadType()).isEqualTo(WORKLOAD_TYPE);
    assertThat(recommendation.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(recommendation.getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(recommendation.getLastReceivedUtilDataAt()).isNotNull();

    Map<String, ContainerRecommendation> containerRecommendations = recommendation.getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(1);

    ContainerRecommendation containerRecommendation = containerRecommendations.get("harness-example");
    assertThat(containerRecommendation.getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "1")
                       .limit("cpu", "1")
                       .request("memory", "1536Mi")
                       .limit("memory", "1536Mi")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getBurstable())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .limit("cpu", "109m")
                       .request("memory", "547M")
                       .limit("memory", "1722M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getGuaranteed())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .limit("cpu", "25m")
                       .request("memory", "549M")
                       .limit("memory", "549M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getRecommended())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .request("memory", "549M")
                       .limit("memory", "549M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getNumDays()).isEqualTo(7);
    assertThat(containerRecommendation.getTotalSamplesCount()).isEqualTo(674);

    assertThat(containerRecommendation.getPercentileBased().get("p50"))
        .isNotNull()
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .request("memory", "477M")
                       .request("nvidia.com/gpu", "1")
                       .limit("cpu", "25m")
                       .limit("memory", "477M")
                       .limit("nvidia.com/gpu", "2")
                       .build());

    assertThat(containerRecommendation.getPercentileBased().get("p99"))
        .isNotNull()
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "43m")
                       .request("memory", "477M")
                       .request("nvidia.com/gpu", "1")
                       .limit("cpu", "43m")
                       .limit("memory", "477M")
                       .limit("nvidia.com/gpu", "2")
                       .build());

    assertThat(recommendation.getEstimatedSavings()).isEqualByComparingTo(BigDecimal.valueOf(189.52));
    assertThat(recommendation.isLastDayCostAvailable()).isTrue();

    verify(recommendationCrudService).upsertWorkloadRecommendation(stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getAllValues()).hasSize(1);
    assertThat(stringCaptor.getValue()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldComputeRecommendationsAndSavingsOnWritePartialHistogramNotPresent() throws Exception {
    List<K8sWorkloadRecommendation> recommendations =
        ImmutableList.of(K8sWorkloadRecommendation.builder()
                             .dirty(true)
                             .accountId(ACCOUNT_ID)
                             .clusterId(CLUSTER_ID)
                             .workloadType(WORKLOAD_TYPE)
                             .namespace(NAMESPACE)
                             .workloadName(WORKLOAD_NAME)
                             .lastReceivedUtilDataAt(Instant.EPOCH)
                             .containerRecommendation("harness-example",
                                 ContainerRecommendation.builder()
                                     .current(ResourceRequirement.builder()
                                                  .request("cpu", "1")
                                                  .limit("cpu", "1")
                                                  .request("memory", "1536Mi")
                                                  .limit("memory", "1536Mi")
                                                  .request("nvidia.com/gpu", "1")
                                                  .limit("nvidia.com/gpu", "2")
                                                  .build())
                                     .build())
                             .containerCheckpoint("harness-example", createContainerCheckpoint())
                             .build());
    when(workloadCostService.getLastAvailableDayCost(eq(ResourceId.builder()
                                                             .accountId(ACCOUNT_ID)
                                                             .clusterId(CLUSTER_ID)
                                                             .namespace(NAMESPACE)
                                                             .kind(WORKLOAD_TYPE)
                                                             .name(WORKLOAD_NAME)
                                                             .build()),
             eq(JOB_START_DATE.minus(Duration.ofDays(7)))))
        .thenReturn(Cost.builder().cpu(BigDecimal.valueOf(3.422)).memory(BigDecimal.valueOf(4.234)).build());
    when(workloadRecommendationDao.fetchPartialRecommendationHistogramForWorkload(any(), any(), any()))
        .thenReturn(Collections.emptyList());

    computedRecommendationWriter.write(recommendations);
    verify(workloadRecommendationDao).save(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);
    K8sWorkloadRecommendation recommendation = captor.getValue();

    assertThat(recommendation.isDirty()).isFalse();
    assertThat(recommendation.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(recommendation.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(recommendation.getWorkloadType()).isEqualTo(WORKLOAD_TYPE);
    assertThat(recommendation.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(recommendation.getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(recommendation.getLastReceivedUtilDataAt()).isNotNull();

    Map<String, ContainerRecommendation> containerRecommendations = recommendation.getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(1);

    ContainerRecommendation containerRecommendation = containerRecommendations.get("harness-example");
    assertThat(containerRecommendation.getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "1")
                       .limit("cpu", "1")
                       .request("memory", "1536Mi")
                       .limit("memory", "1536Mi")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getBurstable())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .limit("cpu", "109m")
                       .request("memory", "547M")
                       .limit("memory", "1722M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getGuaranteed())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .limit("cpu", "25m")
                       .request("memory", "549M")
                       .limit("memory", "549M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getRecommended())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .request("memory", "549M")
                       .limit("memory", "549M")
                       .request("nvidia.com/gpu", "1")
                       .limit("nvidia.com/gpu", "2")
                       .build());
    assertThat(containerRecommendation.getNumDays()).isEqualTo(7);
    assertThat(containerRecommendation.getTotalSamplesCount()).isEqualTo(674);

    assertThat(recommendation.getEstimatedSavings()).isEqualByComparingTo(BigDecimal.valueOf(183.80));
    assertThat(recommendation.isLastDayCostAvailable()).isTrue();

    verify(recommendationCrudService).upsertWorkloadRecommendation(stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getAllValues()).hasSize(1);
    assertThat(stringCaptor.getValue()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSetContainerLevelCost() {
    Map<String, ContainerRecommendation> containerRecommendationMap = ImmutableMap.of("c1",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "15m").request("memory", "20M").build())
            .build(),
        "c2",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "30m").request("memory", "80M").build())
            .build());

    Cost lastDayCost = Cost.builder().cpu(BigDecimal.valueOf(100)).memory(BigDecimal.valueOf(100)).build();
    computedRecommendationWriter.setContainerLevelCost(containerRecommendationMap, lastDayCost);

    final Offset<BigDecimal> costOffset = offset(BigDecimal.valueOf(0.09D));

    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getCpu())
        // (15 / (15 + 30)) * 100 = 0.33 * 100
        .isCloseTo(BigDecimal.valueOf(33.3333D), costOffset);
    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getMemory())
        // (20 / (20 + 80)) * 100 = 0.20 * 100
        .isCloseTo(BigDecimal.valueOf(20), costOffset);

    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getCpu())
        .isCloseTo(BigDecimal.valueOf(66.6667D), costOffset);
    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getMemory())
        .isCloseTo(BigDecimal.valueOf(80), costOffset);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSetContainerLevelCostWithNullCurrentResource() {
    Map<String, ContainerRecommendation> containerRecommendationMap =
        ImmutableMap.of("c1", ContainerRecommendation.builder().build(), "c2",
            ContainerRecommendation.builder()
                .current(ResourceRequirement.builder().request("cpu", "30m").request("memory", "80M").build())
                .build());

    Cost lastDayCost = Cost.builder().cpu(BigDecimal.valueOf(100)).memory(BigDecimal.valueOf(100)).build();
    computedRecommendationWriter.setContainerLevelCost(containerRecommendationMap, lastDayCost);

    final Offset<BigDecimal> costOffset = offset(BigDecimal.valueOf(0.09D));

    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getCpu())
        // (0 / 30) * 100 = 0 * 100
        .isCloseTo(BigDecimal.valueOf(0), costOffset);
    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getMemory())
        // (0 / 80) * 100 = 0 * 100
        .isCloseTo(BigDecimal.valueOf(0), costOffset);

    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getCpu())
        .isCloseTo(BigDecimal.valueOf(100), costOffset);
    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getMemory())
        .isCloseTo(BigDecimal.valueOf(100), costOffset);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSetContainerLevelCostWithExplicitZeroCurrentResource() {
    Map<String, ContainerRecommendation> containerRecommendationMap = ImmutableMap.of("c1",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "0m").request("memory", "0M").build())
            .build());

    Cost lastDayCost = Cost.builder().cpu(BigDecimal.valueOf(100)).memory(BigDecimal.valueOf(100)).build();
    computedRecommendationWriter.setContainerLevelCost(containerRecommendationMap, lastDayCost);

    assertThat(containerRecommendationMap.get("c1").getLastDayCost()).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSetContainerLevelCostWithZeroLastDayCost() {
    Map<String, ContainerRecommendation> containerRecommendationMap = ImmutableMap.of("c1",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "15m").request("memory", "20M").build())
            .build(),
        "c2",
        ContainerRecommendation.builder()
            .current(ResourceRequirement.builder().request("cpu", "30m").request("memory", "80M").build())
            .build());

    Cost lastDayCost = Cost.builder().cpu(BigDecimal.valueOf(0)).memory(BigDecimal.valueOf(0)).build();
    computedRecommendationWriter.setContainerLevelCost(containerRecommendationMap, lastDayCost);

    final Offset<BigDecimal> costOffset = offset(BigDecimal.valueOf(0.09D));

    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getCpu())
        // (15 / (15 + 30)) * 0 = 0.33 * 0
        .isCloseTo(BigDecimal.valueOf(0), costOffset);
    assertThat(containerRecommendationMap.get("c1").getLastDayCost().getMemory())
        // (20 / (20 + 80)) * 0 = 0.20 * 0
        .isCloseTo(BigDecimal.valueOf(0), costOffset);

    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getCpu())
        .isCloseTo(BigDecimal.valueOf(0), costOffset);
    assertThat(containerRecommendationMap.get("c2").getLastDayCost().getMemory())
        .isCloseTo(BigDecimal.valueOf(0), costOffset);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldSetLastDayCostAvailableToFalseIfNoWorkloadCost() {
    List<K8sWorkloadRecommendation> recommendations =
        ImmutableList.of(K8sWorkloadRecommendation.builder()
                             .dirty(true)
                             .accountId(ACCOUNT_ID)
                             .clusterId(CLUSTER_ID)
                             .workloadType(WORKLOAD_TYPE)
                             .namespace(NAMESPACE)
                             .workloadName(WORKLOAD_NAME)
                             .lastReceivedUtilDataAt(Instant.EPOCH)
                             .containerRecommendation("harness-example",
                                 ContainerRecommendation.builder()
                                     .current(ResourceRequirement.builder()
                                                  .request("cpu", "1")
                                                  .limit("cpu", "1")
                                                  .request("memory", "1536Mi")
                                                  .limit("memory", "1536Mi")
                                                  .request("nvidia.com/gpu", "1")
                                                  .limit("nvidia.com/gpu", "2")
                                                  .build())
                                     .build())
                             .containerCheckpoint("harness-example", createContainerCheckpoint())
                             .build());
    when(workloadCostService.getLastAvailableDayCost(eq(ResourceId.builder()
                                                             .accountId(ACCOUNT_ID)
                                                             .clusterId(CLUSTER_ID)
                                                             .namespace(NAMESPACE)
                                                             .kind(WORKLOAD_TYPE)
                                                             .name(WORKLOAD_NAME)
                                                             .build()),
             eq(JOB_START_DATE.minus(Duration.ofDays(7)))))
        .thenReturn(null);

    computedRecommendationWriter.write(recommendations);
    verify(workloadRecommendationDao).save(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);
    K8sWorkloadRecommendation recommendation = captor.getValue();

    assertThat(recommendation.isDirty()).isFalse();
    assertThat(recommendation.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(recommendation.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(recommendation.getWorkloadType()).isEqualTo(WORKLOAD_TYPE);
    assertThat(recommendation.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(recommendation.getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(recommendation.getLastReceivedUtilDataAt()).isNotNull();

    assertThat(recommendation.isLastDayCostAvailable()).isFalse();

    verify(recommendationCrudService).upsertWorkloadRecommendation(stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getAllValues()).hasSize(1);
    assertThat(stringCaptor.getValue()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldUseCurrentIfLessThanMinResources() throws Exception {
    List<K8sWorkloadRecommendation> recommendations = ImmutableList.of(
        K8sWorkloadRecommendation.builder()
            .dirty(true)
            .accountId(ACCOUNT_ID)
            .clusterId(CLUSTER_ID)
            .workloadType(WORKLOAD_TYPE)
            .namespace(NAMESPACE)
            .workloadName(WORKLOAD_NAME)
            .lastReceivedUtilDataAt(Instant.EPOCH)
            .containerRecommendation("harness-example",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder()
                                 .request("cpu", "15m")
                                 .limit("cpu", "15m")
                                 .request("memory", "20M")
                                 .limit("memory", "20M")
                                 .build())
                    .build())
            .containerCheckpoint("harness-example",
                ContainerCheckpoint.builder()
                    .lastUpdateTime(Instant.parse("2020-08-13T01:02:36.879Z"))
                    .cpuHistogram(HistogramCheckpoint.builder()
                                      .referenceTimestamp(Instant.parse("2020-08-13T00:00:00.000Z"))
                                      .bucketWeights(ImmutableMap.<Integer, Integer>builder().put(0, 10000).build())
                                      .totalWeight(17.0708629762673)
                                      .build())
                    .memoryHistogram(HistogramCheckpoint.builder()
                                         .referenceTimestamp(Instant.parse("2020-08-06T00:00:00.000Z"))
                                         .bucketWeights(ImmutableMap.of(0, 10000))
                                         .totalWeight(233.867887395115)
                                         .build())
                    .firstSampleStart(Instant.parse("2020-08-05T00:25:38.000Z"))
                    .lastSampleStart(Instant.parse("2020-08-12T19:03:01.000Z"))
                    .totalSamplesCount(453)
                    .memoryPeak(3616768L)
                    .windowEnd(Instant.parse("2020-08-13T00:25:38.000Z"))
                    .version(1)
                    .build())
            .build());

    computedRecommendationWriter.write(recommendations);
    verify(workloadRecommendationDao).save(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);
    K8sWorkloadRecommendation recommendation = captor.getValue();

    assertThat(recommendation.isDirty()).isFalse();
    assertThat(recommendation.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(recommendation.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(recommendation.getWorkloadType()).isEqualTo(WORKLOAD_TYPE);
    assertThat(recommendation.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(recommendation.getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(recommendation.getLastReceivedUtilDataAt()).isNotNull();

    Map<String, ContainerRecommendation> containerRecommendations = recommendation.getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(1);

    ContainerRecommendation containerRecommendation = containerRecommendations.get("harness-example");
    assertThat(containerRecommendation.getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "15m")
                       .limit("cpu", "15m")
                       .request("memory", "20M")
                       .limit("memory", "20M")
                       .build());
    assertThat(containerRecommendation.getGuaranteed())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "15m")
                       .limit("cpu", "15m")
                       .request("memory", "20M")
                       .limit("memory", "20M")
                       .build());
    assertThat(containerRecommendation.getRecommended())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "15m")
                       .request("memory", "20M")
                       .limit("memory", "20M")
                       .build());

    verify(recommendationCrudService).upsertWorkloadRecommendation(stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getAllValues()).hasSize(1);
    assertThat(stringCaptor.getValue()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldUseMinResourcesIfLessThanCurrent() throws Exception {
    List<K8sWorkloadRecommendation> recommendations = ImmutableList.of(
        K8sWorkloadRecommendation.builder()
            .dirty(true)
            .accountId(ACCOUNT_ID)
            .clusterId(CLUSTER_ID)
            .workloadType(WORKLOAD_TYPE)
            .namespace(NAMESPACE)
            .workloadName(WORKLOAD_NAME)
            .lastReceivedUtilDataAt(Instant.EPOCH)
            .containerRecommendation("harness-example",
                ContainerRecommendation.builder()
                    .current(ResourceRequirement.builder()
                                 .request("cpu", "1")
                                 .limit("cpu", "1")
                                 .request("memory", "1G")
                                 .limit("memory", "1G")
                                 .build())
                    .build())
            .containerCheckpoint("harness-example",
                ContainerCheckpoint.builder()
                    .lastUpdateTime(Instant.parse("2020-08-13T01:02:36.879Z"))
                    .cpuHistogram(HistogramCheckpoint.builder()
                                      .referenceTimestamp(Instant.parse("2020-08-13T00:00:00.000Z"))
                                      .bucketWeights(ImmutableMap.<Integer, Integer>builder().put(0, 10000).build())
                                      .totalWeight(17.0708629762673)
                                      .build())
                    .memoryHistogram(HistogramCheckpoint.builder()
                                         .referenceTimestamp(Instant.parse("2020-08-06T00:00:00.000Z"))
                                         .bucketWeights(ImmutableMap.of(0, 10000))
                                         .totalWeight(233.867887395115)
                                         .build())
                    .firstSampleStart(Instant.parse("2020-08-05T00:25:38.000Z"))
                    .lastSampleStart(Instant.parse("2020-08-12T19:03:01.000Z"))
                    .totalSamplesCount(453)
                    .memoryPeak(3616768L)
                    .windowEnd(Instant.parse("2020-08-13T00:25:38.000Z"))
                    .version(1)
                    .build())
            .build());

    computedRecommendationWriter.write(recommendations);
    verify(workloadRecommendationDao).save(captor.capture());

    assertThat(captor.getAllValues()).hasSize(1);
    K8sWorkloadRecommendation recommendation = captor.getValue();

    assertThat(recommendation.isDirty()).isFalse();
    assertThat(recommendation.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(recommendation.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(recommendation.getWorkloadType()).isEqualTo(WORKLOAD_TYPE);
    assertThat(recommendation.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(recommendation.getWorkloadName()).isEqualTo(WORKLOAD_NAME);
    assertThat(recommendation.getLastReceivedUtilDataAt()).isNotNull();

    Map<String, ContainerRecommendation> containerRecommendations = recommendation.getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(1);

    ContainerRecommendation containerRecommendation = containerRecommendations.get("harness-example");
    assertThat(containerRecommendation.getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "1")
                       .limit("cpu", "1")
                       .request("memory", "1G")
                       .limit("memory", "1G")
                       .build());
    assertThat(containerRecommendation.getGuaranteed())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .limit("cpu", "25m")
                       .request("memory", "250M")
                       .limit("memory", "250M")
                       .build());

    assertThat(containerRecommendation.getRecommended())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "25m")
                       .request("memory", "250M")
                       .limit("memory", "250M")
                       .build());

    verify(recommendationCrudService).upsertWorkloadRecommendation(stringCaptor.capture(), any(), any(), any());
    assertThat(stringCaptor.getAllValues()).hasSize(1);
    assertThat(stringCaptor.getValue()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldAttachHarnessServiceInfo() throws Exception {
    K8sWorkloadRecommendation k8sWorkloadRecommendation = K8sWorkloadRecommendation.builder().build();
    ResourceId workloadId = ResourceId.builder().accountId("account_id").build();
    when(workloadRepository.getWorkload(workloadId))
        .thenReturn(Optional.of(K8sWorkload.builder().labels(ImmutableMap.of("k1", "v1", "k2", "v2")).build()));
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(
             eq("account_id"), eq(ImmutableMap.of("k1", "v1", "k2", "v2"))))
        .thenReturn(Optional.of(HarnessServiceInfo.builder()
                                    .serviceId("app_id")
                                    .appId("app_id")
                                    .cloudProviderId("cloud_provider_id")
                                    .envId("env_id")
                                    .infraMappingId("infra_mapping_id")
                                    .deploymentSummaryId("deployment_summary_id")
                                    .build()));
    computedRecommendationWriter.addHarnessSvcInfo(workloadId, k8sWorkloadRecommendation);
    assertThat(k8sWorkloadRecommendation.getHarnessServiceInfo())
        .isEqualTo(HarnessServiceInfo.builder()
                       .serviceId("app_id")
                       .appId("app_id")
                       .cloudProviderId("cloud_provider_id")
                       .envId("env_id")
                       .infraMappingId("infra_mapping_id")
                       .deploymentSummaryId("deployment_summary_id")
                       .build());
  }

  private static ContainerCheckpoint createContainerCheckpoint() {
    return ContainerCheckpoint.builder()
        .lastUpdateTime(Instant.parse("2020-07-28T01:27:20.271Z"))
        .cpuHistogram(HistogramCheckpoint.builder()
                          .referenceTimestamp(Instant.parse("2020-07-28T00:00:00.000Z"))
                          .bucketWeights(ImmutableMap.<Integer, Integer>builder()
                                             .put(0, 10000)
                                             .put(1, 560)
                                             .put(2, 412)
                                             .put(3, 340)
                                             .put(4, 84)
                                             .put(5, 1)
                                             .put(36, 1)
                                             .build())
                          .totalWeight(10.1902752967582)
                          .build())
        .memoryHistogram(HistogramCheckpoint.builder()
                             .referenceTimestamp(Instant.parse("2020-07-21T00:00:00.000Z"))
                             .bucketWeights(ImmutableMap.of(23, 3710, 24, 10000))
                             .totalWeight(302.138052671595)
                             .build())
        .firstSampleStart(Instant.parse("2020-07-20T05:52:23.000Z"))
        .lastSampleStart(Instant.parse("2020-07-27T13:49:40.000Z"))
        .totalSamplesCount(674)
        .memoryPeak(460259328L)
        .windowEnd(Instant.parse("2020-07-28T05:52:23.000Z"))
        .version(1)
        .build();
  }
}
