/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.ccm.CcmConstants.CLUSTER_ID_IDENTIFIER;
import static io.harness.ccm.CcmConstants.UID;
import static io.harness.perpetualtask.k8s.watch.PodEvent.EventType.EVENT_TYPE_SCHEDULED;
import static io.harness.perpetualtask.k8s.watch.PodEvent.EventType.EVENT_TYPE_TERMINATED;
import static io.harness.rule.OwnerRule.ABHINAV3;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.UTSAV;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static io.kubernetes.client.custom.Quantity.Format.BINARY_SI;
import static io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodConditionBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class PodWatcherTest extends CategoryTest {
  private PodWatcher podWatcher;
  private EventPublisher eventPublisher;
  private SharedInformerFactory sharedInformerFactory;
  private PVCFetcher pvcFetcher;
  private NamespaceFetcher namespaceFetcher;

  private static final OffsetDateTime TIMESTAMP = OffsetDateTime.now();
  private static final OffsetDateTime DELETION_TIMESTAMP = TIMESTAMP.plusMinutes(5);
  private static final String START_RV = "1001";
  private static final String END_RV = "1002";
  private static final String MAP_KEY_WITH_DOT = "harness.io/created.by";
  private static final String MAP_VALUE = "harness.io/created.by";
  private static final Map<String, String> NAMESPACE_LABELS = ImmutableMap.of("harness-managed", "true");
  private static final Map<String, String> SAMPLE_MAP = ImmutableMap.of(MAP_KEY_WITH_DOT, MAP_VALUE);
  private static final UrlPattern POD_URL_MATCHING = urlMatching("^/api/v1/pods.*");

  ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
  @Captor ArgumentCaptor<Map<String, String>> mapArgumentCaptor;

  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    ApiClient apiClient =
        new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build().setReadTimeout(0);
    sharedInformerFactory = new SharedInformerFactory(apiClient);

    eventPublisher = mock(EventPublisher.class);
    pvcFetcher = mock(PVCFetcher.class);
    namespaceFetcher = mock(NamespaceFetcher.class);

    K8sControllerFetcher controllerFetcher = mock(K8sControllerFetcher.class);

    when(controllerFetcher.getTopLevelOwner(any()))
        .thenReturn(io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
                        .setKind("Deployment")
                        .setName("manager")
                        .setUid("9a1e372f-a7c1-410b-8b07-e09b0b965fcc")
                        .putLabels("app", "manager")
                        .putLabels("harness.io/release-name", "2cb07f52-ee19-3ab3-a3e7-8b8de3e2d0d1")
                        .build());

    when(pvcFetcher.getPvcByKey(any(), any()))
        .thenReturn(new V1PersistentVolumeClaimBuilder()
                        .withNewSpec()
                        .withNewResources()
                        .addToRequests("storage", new io.kubernetes.client.custom.Quantity("1Ki"))
                        .endResources()
                        .endSpec()
                        .build());

    when(namespaceFetcher.getNamespaceByKey(any()))
        .thenReturn(new V1NamespaceBuilder()
                        .withNewMetadata()
                        .withName("harness")
                        .withLabels(NAMESPACE_LABELS)
                        .endMetadata()
                        .build());

    podWatcher = new PodWatcher(apiClient,
        ClusterDetails.builder()
            .clusterName("clusterName")
            .clusterId("clusterId")
            .cloudProviderId("cloud-provider-id")
            .kubeSystemUid("cluster-uid")
            .build(),
        controllerFetcher, sharedInformerFactory, pvcFetcher, namespaceFetcher, eventPublisher);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("flaky test: comment this while testing on local")
  public void testEventFiredOnAdd() throws InterruptedException {
    V1PodList podList = new V1PodList().metadata(new V1ListMeta().resourceVersion(START_RV)).items(Arrays.asList());

    stubFor(get(POD_URL_MATCHING)
                .inScenario("onAdd")
                .willSetStateTo("watch=true")
                .withQueryParam("watch", equalTo("false"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(podList))));

    V1Pod POD = podBuilder().build();
    POD.metadata(POD.getMetadata().resourceVersion(END_RV));
    Watch.Response<V1Pod> watchResponse = new Watch.Response<>(EventType.ADDED.name(), POD);

    stubFor(get(POD_URL_MATCHING)
                .inScenario("onAdd")
                .whenScenarioStateIs("watch=true")
                .willSetStateTo("random123")
                .withQueryParam("watch", equalTo("true"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(watchResponse))));

    sharedInformerFactory.startAllRegisteredInformers();
    // try increase sleep if no requests received
    Thread.sleep(200);

    WireMock.verify(1, getRequestedFor(POD_URL_MATCHING).withQueryParam("watch", equalTo("false")));
    WireMock.verify(getRequestedFor(POD_URL_MATCHING).withQueryParam("watch", equalTo("true")));

    verify(eventPublisher, times(2)).publishMessage(captor.capture(), any(), any());

    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PodInfo.class, this::infoMessageAssertions);
    assertThat(captor.getAllValues().get(1)).isInstanceOfSatisfying(PodEvent.class, this::scheduledMessageAssertions);

    sharedInformerFactory.stopAllRegisteredInformers();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishPodScheduledAndPodInfo() throws Exception {
    podWatcher.eventReceived(scheduledPodWithStatus());
    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PodInfo.class, this::infoMessageAssertions);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
  }

  @Test
  @Owner(developers = ABHINAV3)
  @Category(UnitTests.class)
  public void shouldRunningStatusPodInfo() throws Exception {
    V1Pod v1Pod = podBuilder().build();

    V1PodStatus v1PodStatus = new V1PodStatus();
    v1PodStatus.setQosClass("Guaranteed");
    v1PodStatus.setPhase("Running");
    V1PodCondition v1PodCondition = new V1PodCondition();
    v1PodCondition.setType("PodScheduled");
    v1PodCondition.setStatus("True");
    v1PodStatus.setConditions(Arrays.asList(v1PodCondition));
    v1Pod.setStatus(v1PodStatus);
    podWatcher.eventReceived(v1Pod);
    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PodInfo.class, this::infoMessageAssertions);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
  }

  @Test
  @Owner(developers = ABHINAV3)
  @Category(UnitTests.class)
  public void shouldRunningStatusPodInfoWithPendingStatus() throws Exception {
    V1Pod v1Pod = podBuilder().build();

    V1PodStatus v1PodStatus = new V1PodStatus();
    v1PodStatus.setQosClass("Guaranteed");
    v1PodStatus.setPhase("Pending");
    V1PodCondition v1PodCondition = new V1PodCondition();
    v1PodCondition.setType("PodScheduled");
    v1PodCondition.setStatus("True");
    v1PodStatus.setConditions(Arrays.asList(v1PodCondition));
    v1Pod.setStatus(v1PodStatus);
    podWatcher.eventReceived(v1Pod);
    verify(eventPublisher, times(0))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishPodDeleted() throws Exception {
    podWatcher.eventReceived(scheduledAndDeletedPod());

    verify(eventPublisher, atLeastOnce())
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    assertThat(captor.getAllValues().get(1)).isInstanceOfSatisfying(PodEvent.class, this::deletedMessageAssertions);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldPopulateMetadataAnnotations() throws Exception {
    podWatcher.eventReceived(scheduledAndDeletedPodWitMetadataAnnotation());

    verify(eventPublisher, atLeastOnce()).publishMessage(captor.capture(), any(Timestamp.class), any());

    assertThat(captor.getAllValues()).hasSize(2);

    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(PodInfo.class, podInfo -> {
      assertThat(podInfo.getMetadataAnnotationsMap()).isNotEmpty();
      assertThat(podInfo.getMetadataAnnotationsMap()).isEqualTo(SAMPLE_MAP);
    });
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldPopulateWithNoMetadataAnnotations() throws Exception {
    podWatcher.eventReceived(scheduledAndDeletedPod());

    verify(eventPublisher, atLeastOnce()).publishMessage(captor.capture(), any(Timestamp.class), any());

    assertThat(captor.getAllValues()).hasSize(2);
    assertThat(captor.getAllValues().get(0))
        .isInstanceOfSatisfying(PodInfo.class, p -> assertThat(p.getMetadataAnnotationsMap()).isNotNull().isEmpty());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishDuplicates() throws Exception {
    podWatcher.eventReceived(podBuilder().build()); // none
    podWatcher.eventReceived(scheduledPodWithStatus()); // info, scheduled
    podWatcher.eventReceived(scheduledPod()); // none
    podWatcher.eventReceived(scheduledAndDeletedPod()); // deleted

    verify(eventPublisher, atLeastOnce())
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    List<Message> publishedMessages = captor.getAllValues();
    assertThat(publishedMessages).hasSize(2);
    assertThat(publishedMessages.get(0)).isInstanceOfSatisfying(PodInfo.class, this::infoMessageAssertions);
    assertThat(publishedMessages.get(1)).isInstanceOfSatisfying(PodEvent.class, this::deletedMessageAssertions);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
    assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
  }

  private static V1Pod scheduledPod() {
    return podBuilder()
        .editSpec()
        .withNodeName("gke-pr-private-pool-1-49d0f375-12xx")
        .endSpec()
        .editOrNewStatus()
        .withConditions(new V1PodConditionBuilder()
                            .withLastTransitionTime(TIMESTAMP)
                            .withType("PodScheduled")
                            .withStatus("True")
                            .build())
        .endStatus()
        .build();
  }

  private static V1Pod scheduledPodWithStatus() {
    return podBuilder()
        .editSpec()
        .withNodeName("gke-pr-private-pool-1-49d0f375-12xx")
        .endSpec()
        .editOrNewStatus()
        .withConditions(new V1PodConditionBuilder()
                            .withLastTransitionTime(TIMESTAMP)
                            .withType("PodScheduled")
                            .withStatus("True")
                            .build())
        .endStatus()
        .withStatus(
            new V1PodStatus()
                .phase("Running")
                .qosClass("Guaranteed")
                .addConditionsItem(new V1PodConditionBuilder().withStatus("True").withType("PodScheduled").build()))
        .build();
  }

  private static V1Pod scheduledAndDeletedPodWitMetadataAnnotation() {
    return new V1PodBuilder(scheduledAndDeletedPod()).editMetadata().addToAnnotations(SAMPLE_MAP).endMetadata().build();
  }

  private static V1Pod scheduledAndDeletedPod() {
    return new V1PodBuilder(scheduledPodWithStatus())
        .editMetadata()
        .withDeletionGracePeriodSeconds(0L)
        .withDeletionTimestamp(DELETION_TIMESTAMP)
        .endMetadata()
        .build();
  }

  private static V1PodBuilder podBuilder() {
    return new V1PodBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
        .withUid("948e988d-d300-11e9-b63d-4201ac100a04")
        .withName("manager-79cc97bdfb-r6kzs")
        .withCreationTimestamp(TIMESTAMP)
        .withNamespace("harness")
        .withLabels(
            ImmutableMap.of("app", "manager", "harness.io/release-name", "2cb07f52-ee19-3ab3-a3e7-8b8de3e2d0d1"))
        .withResourceVersion("77330477")
        .endMetadata()
        .withNewStatus()
        .withConditions(ImmutableList.of(new V1PodConditionBuilder()
                                             .withType("PodScheduled")
                                             .withStatus("True")
                                             .withLastTransitionTime(TIMESTAMP)
                                             .build()))
        .withQosClass("Guaranteed")
        .endStatus()
        .withNewSpec()
        .withVolumes(new V1VolumeBuilder()
                         .withNewPersistentVolumeClaim()
                         .withClaimName("mongo-data")
                         .endPersistentVolumeClaim()
                         .build())
        .withNodeName("gke-pr-private-pool-1-49d0f375-12xx")
        .withContainers(
            new V1ContainerBuilder()
                .withImage("us.gcr.io/platform-205701/harness/feature-manager:19204")
                .withName("manager")
                .withNewResources()
                .addToLimits("cpu", new io.kubernetes.client.custom.Quantity(new BigDecimal("1"), DECIMAL_SI))
                .addToLimits(
                    "memory", new io.kubernetes.client.custom.Quantity(new BigDecimal("2861563904"), BINARY_SI))
                .addToRequests("cpu", new io.kubernetes.client.custom.Quantity(new BigDecimal("1"), DECIMAL_SI))
                .addToRequests(
                    "memory", new io.kubernetes.client.custom.Quantity(new BigDecimal("2861563904"), BINARY_SI))
                .endResources()
                .build())
        .endSpec();
  }

  private void deletedMessageAssertions(PodEvent podEvent) {
    assertThat(podEvent.getPodUid()).isEqualTo("948e988d-d300-11e9-b63d-4201ac100a04");
    assertThat(podEvent.getType()).isEqualTo(EVENT_TYPE_TERMINATED);
    assertThat(HTimestamps.toMillis(podEvent.getTimestamp())).isEqualTo(DELETION_TIMESTAMP.toInstant().toEpochMilli());
  }

  private void scheduledMessageAssertions(PodEvent podEvent) {
    assertThat(podEvent.getPodUid()).isEqualTo("948e988d-d300-11e9-b63d-4201ac100a04");
    assertThat(podEvent.getType()).isEqualTo(EVENT_TYPE_SCHEDULED);
    assertThat(HTimestamps.toMillis(podEvent.getTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
  }

  private void infoMessageAssertions(PodInfo podInfo) {
    assertThat(podInfo.getPodUid()).isEqualTo("948e988d-d300-11e9-b63d-4201ac100a04");
    assertThat(podInfo.getPodName()).isEqualTo("manager-79cc97bdfb-r6kzs");
    assertThat(HTimestamps.toMillis(podInfo.getCreationTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
    assertThat(podInfo.getNamespace()).isEqualTo("harness");
    assertThat(podInfo.getNodeName()).isEqualTo("gke-pr-private-pool-1-49d0f375-12xx");
    assertThat(podInfo.getContainersList())
        .containsExactly(
            Container.newBuilder()
                .setName("manager")
                .setImage("us.gcr.io/platform-205701/harness/feature-manager:19204")
                .setResource(
                    Resource.newBuilder()
                        .putLimits("cpu", Quantity.newBuilder().setAmount(1_000_000_000L).setUnit("n").build())
                        .putLimits("memory", Quantity.newBuilder().setAmount(2861563904L).setUnit("").build())
                        .putRequests("cpu", Quantity.newBuilder().setAmount(1_000_000_000).setUnit("n").build())
                        .putRequests("memory", Quantity.newBuilder().setAmount(2861563904L).setUnit("").build())
                        .build())
                .build());
    assertThat(podInfo.getQosClass()).isEqualTo("Guaranteed");
    assertThat(podInfo.getTotalResource())
        .isEqualTo(Resource.newBuilder()
                       .putLimits("cpu", Quantity.newBuilder().setAmount(1_000_000_000).setUnit("n").build())
                       .putLimits("memory", Quantity.newBuilder().setAmount(2861563904L).setUnit("").build())
                       .putRequests("cpu", Quantity.newBuilder().setAmount(1_000_000_000).setUnit("n").build())
                       .putRequests("memory", Quantity.newBuilder().setAmount(2861563904L).setUnit("").build())
                       .build());
    assertThat(podInfo.getLabelsMap())
        .isEqualTo(
            ImmutableMap.of("app", "manager", "harness.io/release-name", "2cb07f52-ee19-3ab3-a3e7-8b8de3e2d0d1"));
    assertThat(podInfo.getNamespaceLabelsMap()).isEqualTo(NAMESPACE_LABELS);
    assertThat(podInfo.getTopLevelOwner())
        .isEqualTo(io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
                       .setKind("Deployment")
                       .setName("manager")
                       .setUid("9a1e372f-a7c1-410b-8b07-e09b0b965fcc")
                       .putLabels("app", "manager")
                       .putLabels("harness.io/release-name", "2cb07f52-ee19-3ab3-a3e7-8b8de3e2d0d1")
                       .build());
  }
}
