/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.CostEventSource;
import io.harness.batch.processing.ccm.CostEventType;
import io.harness.batch.processing.ccm.EnrichedEvent;
import io.harness.batch.processing.config.k8s.resource.change.K8sWatchEventConfig;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.batch.processing.k8s.EstimatedCostDiff;
import io.harness.batch.processing.k8s.WatchEventCostEstimator;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.K8sYamlDao;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.perpetualtask.k8s.watch.K8sObjectReference;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;

public class K8sWatchEventConfigTest extends CategoryTest {
  private static final String CLUSTER_ID = "7c8638bb-fde8-4f5a-b4cd-4ba4ee29fba1";
  private static final String CLOUD_PROVIDER_ID = "ae94bc7e-1888-49fd-869c-e239a41b5686";
  private static final String UID = "b10eed3e-5e02-11ea-a4b7-4201ac100a0a";
  private static final String ACCOUNT_ID = "6a0173e1-ca0a-4af6-ab3a-75eff3acffa5";
  private static final String CLUSTER_NAME = "cluster-name";
  private static final String APP_ID = "7b444e73-d8e8-43ce-9c98-f5c92aeb6953";
  private static final String SVC_ID = "9b2ea68c-6cae-40bd-89b6-2783a127aa6e";
  private static final String ENV_ID = "43ad2869-10b1-4bfa-897e-dbc07aa27650";

  private K8sYamlDao k8sYamlDao;
  private K8sWatchEventConfig k8sWatchEventConfig;
  private WorkloadRepository workloadRepository;
  private K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  private CostEventService costEventService;
  private WatchEventCostEstimator watchEventCostEstimator;
  private Instant jobStartDate;

  @Before
  public void setUp() throws Exception {
    k8sYamlDao = mock(K8sYamlDao.class);
    workloadRepository = mock(WorkloadRepository.class);
    k8sLabelServiceInfoFetcher = mock(K8sLabelServiceInfoFetcher.class);
    k8sWatchEventConfig = new K8sWatchEventConfig(null, null);
    k8sLabelServiceInfoFetcher = mock(K8sLabelServiceInfoFetcher.class);
    costEventService = mock(CostEventService.class);
    watchEventCostEstimator = mock(WatchEventCostEstimator.class);
    when(watchEventCostEstimator.estimateCostDiff(any()))
        .thenReturn(new EstimatedCostDiff(BigDecimal.valueOf(1000), BigDecimal.valueOf(1300)));
    jobStartDate = Instant.now();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNormalizer_shouldReturnNullIfTimestampNotAfterLastChangeTimestamp() throws Exception {
    Instant lastChangeTime = Instant.now();
    when(costEventService.getEventsForWorkload(ACCOUNT_ID, CLUSTER_ID, UID, CostEventType.K8S_RESOURCE_CHANGE.name(),
             jobStartDate.minus(1, ChronoUnit.DAYS).toEpochMilli()))
        .thenReturn(singletonList(CostEventData.builder().startTimestamp(lastChangeTime.toEpochMilli()).build()));
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .build())
                                   .occurredAt(lastChangeTime.minus(10, ChronoUnit.SECONDS).toEpochMilli())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isNull();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNormalizer_shouldPassthroughIfTypeUpdated() throws Exception {
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .build())
                                   .occurredAt(Instant.now().toEpochMilli())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isSameAs(message);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNormalizer_shouldPassthroughIfTypeDeleted() throws Exception {
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_DELETED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .build())
                                   .occurredAt(Instant.now().toEpochMilli())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isSameAs(message);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNormalizer_shouldPassThroughIfTrueAdded() throws Exception {
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_ADDED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .build())
                                   .occurredAt(Instant.now().toEpochMilli())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isSameAs(message);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNormalizer_shouldReturnNullIfAlreadyAdded() throws Exception {
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_ADDED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .setNewResourceYaml("yaml")
                                                .build())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isNull();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  @Ignore("Should clear after fixing what should happen for unobserved events") // TODO(avmohan): Fix this.
  public void testNormalizer_shouldConvertToUpdatedIfAlreadyAddedDifferentYaml() throws Exception {
    ItemProcessor<PublishedMessage, PublishedMessage> normalizer =
        k8sWatchEventConfig.normalizer(costEventService, jobStartDate.toEpochMilli());
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_ADDED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .setNewResourceVersion("12345")
                                                .setNewResourceYaml("yaml2")
                                                .build())
                                   .build();
    PublishedMessage processed = normalizer.process(message);
    assertThat(processed).isEqualTo(
        PublishedMessage.builder()
            .accountId(ACCOUNT_ID)
            .message(K8sWatchEvent.newBuilder()
                         .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                         .setClusterId(CLUSTER_ID)
                         .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                         .setOldResourceVersion("12334")
                         .setOldResourceYaml("yaml1")
                         .setNewResourceVersion("12345")
                         .setNewResourceYaml("yaml2")
                         .setDescription("Missed update event")
                         .build())
            .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testEnricher_shouldMapToHarnessServiceInfo() throws Exception {
    Map<String, String> labelMap = ImmutableMap.of("label1", "value1", "label2", "value2");
    when(workloadRepository.getWorkload(ACCOUNT_ID, CLUSTER_ID, UID))
        .thenReturn(Optional.of(K8sWorkload.builder().labels(labelMap).build()));
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        SVC_ID, APP_ID, "cloud-provider-id", ENV_ID, "infra-mapping-id", "deployment-summary-id");
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(ACCOUNT_ID, labelMap))
        .thenReturn(Optional.of(harnessServiceInfo));
    ItemProcessor<PublishedMessage, EnrichedEvent<K8sWatchEvent>> enricher =
        k8sWatchEventConfig.enricher(workloadRepository, k8sLabelServiceInfoFetcher);
    PublishedMessage message = PublishedMessage.builder()
                                   .accountId(ACCOUNT_ID)
                                   .message(K8sWatchEvent.newBuilder()
                                                .setType(K8sWatchEvent.Type.TYPE_ADDED)
                                                .setClusterId(CLUSTER_ID)
                                                .setResourceRef(K8sObjectReference.newBuilder().setUid(UID).build())
                                                .setNewResourceVersion("12345")
                                                .setNewResourceYaml("yaml1")
                                                .build())
                                   .build();
    EnrichedEvent<K8sWatchEvent> enriched = enricher.process(message);
    assertThat(enriched.getHarnessServiceInfo()).isEqualTo(harnessServiceInfo);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWriter_shouldHandleAdded() throws Exception {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    when(costEventService.create(captor.capture())).thenReturn(true);
    when(k8sYamlDao.ensureYamlSaved(ACCOUNT_ID, CLUSTER_ID, UID, "12345", "added-yaml"))
        .thenReturn("ZS5mUwfQSFO1pTljLKWG-Q");
    ItemWriter<EnrichedEvent<K8sWatchEvent>> writer =
        k8sWatchEventConfig.writer(k8sYamlDao, costEventService, watchEventCostEstimator);
    long occurredAt = 1583395677;
    K8sWatchEvent event = K8sWatchEvent.newBuilder()
                              .setType(K8sWatchEvent.Type.TYPE_ADDED)
                              .setDescription("Resource added")
                              .setClusterId(CLUSTER_ID)
                              .setClusterName(CLUSTER_NAME)
                              .setCloudProviderId(CLOUD_PROVIDER_ID)
                              .setResourceRef(K8sObjectReference.newBuilder()
                                                  .setUid(UID)
                                                  .setName("workload-name")
                                                  .setNamespace("workload-namespace")
                                                  .setKind("workload-kind")
                                                  .build())
                              .setNewResourceYaml("added-yaml")
                              .setNewResourceVersion("12345")
                              .build();
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        SVC_ID, APP_ID, "cloud-provider-id", ENV_ID, "infra-mapping-id", "deployment-summary-id");
    writer.write(singletonList(new EnrichedEvent<>(ACCOUNT_ID, occurredAt, event, harnessServiceInfo)));
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0)).isInstanceOfSatisfying(CostEventData.class, costEventData -> {
      assertThat(costEventData)
          .isEqualTo(CostEventData.builder()
                         .accountId(ACCOUNT_ID)
                         .clusterType(ClusterType.K8S.name())
                         .costEventSource(CostEventSource.K8S_CLUSTER.name())
                         .costEventType(CostEventType.K8S_RESOURCE_CHANGE.name())
                         .clusterId(CLUSTER_ID)
                         .startTimestamp(occurredAt)
                         .eventDescription("Resource added")
                         .newYamlRef("ZS5mUwfQSFO1pTljLKWG-Q")
                         .clusterId(CLUSTER_ID)
                         .appId(APP_ID)
                         .serviceId(SVC_ID)
                         .cloudProviderId(CLOUD_PROVIDER_ID)
                         .envId(ENV_ID)
                         .namespace("workload-namespace")
                         .workloadName("workload-name")
                         .workloadType("workload-kind")
                         .billingAmount(BigDecimal.valueOf(300))
                         .costChangePercent(BigDecimal.valueOf(3000, 2))
                         .instanceId(UID)
                         .build());
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWriter_shouldHandleUpdated() throws Exception {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    when(costEventService.create(captor.capture())).thenReturn(true);
    when(k8sYamlDao.ensureYamlSaved(ACCOUNT_ID, CLUSTER_ID, UID, "12334", "old-yaml"))
        .thenReturn("Cb3cDTt3RBegwMzuV9gH3A");
    when(k8sYamlDao.ensureYamlSaved(ACCOUNT_ID, CLUSTER_ID, UID, "12345", "new-yaml"))
        .thenReturn("ZS5mUwfQSFO1pTljLKWG-Q");
    ItemWriter<EnrichedEvent<K8sWatchEvent>> writer =
        k8sWatchEventConfig.writer(k8sYamlDao, costEventService, watchEventCostEstimator);
    long occurredAt = 1583395677;
    K8sWatchEvent event = K8sWatchEvent.newBuilder()
                              .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                              .setDescription("Resource updated")
                              .setClusterId(CLUSTER_ID)
                              .setClusterName(CLUSTER_NAME)
                              .setCloudProviderId(CLOUD_PROVIDER_ID)
                              .setResourceRef(K8sObjectReference.newBuilder()
                                                  .setUid(UID)
                                                  .setName("workload-name")
                                                  .setNamespace("workload-namespace")
                                                  .setKind("workload-kind")
                                                  .build())
                              .setOldResourceYaml("old-yaml")
                              .setOldResourceVersion("12334")
                              .setNewResourceYaml("new-yaml")
                              .setNewResourceVersion("12345")
                              .build();
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        SVC_ID, APP_ID, "cloud-provider-id", ENV_ID, "infra-mapping-id", "deployment-summary-id");
    writer.write(singletonList(new EnrichedEvent<>(ACCOUNT_ID, occurredAt, event, harnessServiceInfo)));
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0)).isInstanceOfSatisfying(CostEventData.class, costEventData -> {
      assertThat(costEventData)
          .isEqualTo(CostEventData.builder()
                         .accountId(ACCOUNT_ID)
                         .clusterType(ClusterType.K8S.name())
                         .costEventSource(CostEventSource.K8S_CLUSTER.name())
                         .costEventType(CostEventType.K8S_RESOURCE_CHANGE.name())
                         .clusterId(CLUSTER_ID)
                         .startTimestamp(occurredAt)
                         .eventDescription("Resource updated")
                         .oldYamlRef("Cb3cDTt3RBegwMzuV9gH3A")
                         .newYamlRef("ZS5mUwfQSFO1pTljLKWG-Q")
                         .clusterId(CLUSTER_ID)
                         .appId(APP_ID)
                         .serviceId(SVC_ID)
                         .cloudProviderId(CLOUD_PROVIDER_ID)
                         .billingAmount(BigDecimal.valueOf(300))
                         .costChangePercent(BigDecimal.valueOf(3000, 2))
                         .envId(ENV_ID)
                         .namespace("workload-namespace")
                         .workloadName("workload-name")
                         .workloadType("workload-kind")
                         .instanceId(UID)
                         .build());
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWriter_shouldHandleDeleted() throws Exception {
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    when(costEventService.create(captor.capture())).thenReturn(true);
    when(k8sYamlDao.ensureYamlSaved(ACCOUNT_ID, CLUSTER_ID, UID, "12334", "deleted-yaml"))
        .thenReturn("Cb3cDTt3RBegwMzuV9gH3A");
    ItemWriter<EnrichedEvent<K8sWatchEvent>> writer =
        k8sWatchEventConfig.writer(k8sYamlDao, costEventService, watchEventCostEstimator);
    long occurredAt = 1583395677;
    K8sWatchEvent event = K8sWatchEvent.newBuilder()
                              .setType(K8sWatchEvent.Type.TYPE_DELETED)
                              .setDescription("Resource deleted")
                              .setClusterId(CLUSTER_ID)
                              .setClusterName(CLUSTER_NAME)
                              .setCloudProviderId(CLOUD_PROVIDER_ID)
                              .setResourceRef(K8sObjectReference.newBuilder()
                                                  .setUid(UID)
                                                  .setName("workload-name")
                                                  .setNamespace("workload-namespace")
                                                  .setKind("workload-kind")
                                                  .build())
                              .setOldResourceYaml("deleted-yaml")
                              .setOldResourceVersion("12334")
                              .build();
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        SVC_ID, APP_ID, "cloud-provider-id", ENV_ID, "infra-mapping-id", "deployment-summary-id");
    writer.write(singletonList(new EnrichedEvent<>(ACCOUNT_ID, occurredAt, event, harnessServiceInfo)));
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0)).isInstanceOfSatisfying(CostEventData.class, costEventData -> {
      assertThat(costEventData)
          .isEqualTo(CostEventData.builder()
                         .accountId(ACCOUNT_ID)
                         .clusterType(ClusterType.K8S.name())
                         .costEventSource(CostEventSource.K8S_CLUSTER.name())
                         .costEventType(CostEventType.K8S_RESOURCE_CHANGE.name())
                         .clusterId(CLUSTER_ID)
                         .startTimestamp(occurredAt)
                         .eventDescription("Resource deleted")
                         .oldYamlRef("Cb3cDTt3RBegwMzuV9gH3A")
                         .clusterId(CLUSTER_ID)
                         .appId(APP_ID)
                         .serviceId(SVC_ID)
                         .cloudProviderId(CLOUD_PROVIDER_ID)
                         .envId(ENV_ID)
                         .namespace("workload-namespace")
                         .workloadName("workload-name")
                         .workloadType("workload-kind")
                         .billingAmount(BigDecimal.valueOf(300))
                         .costChangePercent(BigDecimal.valueOf(3000, 2))
                         .instanceId(UID)
                         .build());
    });
  }
}
