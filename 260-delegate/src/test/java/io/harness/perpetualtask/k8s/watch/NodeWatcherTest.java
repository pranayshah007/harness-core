/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.ccm.CcmConstants.CLUSTER_ID_IDENTIFIER;
import static io.harness.perpetualtask.k8s.watch.NodeEvent.EventType.EVENT_TYPE_START;
import static io.harness.perpetualtask.k8s.watch.NodeEvent.EventType.EVENT_TYPE_STOP;
import static io.harness.rule.OwnerRule.UTSAV;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1ListMeta;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1NodeSpec;
import io.kubernetes.client.openapi.models.V1NodeStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class NodeWatcherTest extends CategoryTest {
  private static final String UID = UUID.randomUUID().toString();
  private static final String NAME = "test-node";
  private static final String GCP_PROVIDER_ID = "gce://ccm-play/us-east4-a/gke-ccm-test-default-pool-d13df1f8-zk7p";
  private static final OffsetDateTime TIMESTAMP = OffsetDateTime.now();
  private static final Map<String, String> LABELS = ImmutableMap.of("k1", "v1", "k2", "v2");
  private static final String START_RV = "1000";
  private static final String END_RV = "1001";

  private NodeWatcher nodeWatcher;
  private EventPublisher eventPublisher;
  private SharedInformerFactory sharedInformerFactory;

  private static final UrlPattern NODE_URL_MATCHING = urlMatching("^/api/v1/nodes.*");

  @Captor ArgumentCaptor<Map<String, String>> mapArgumentCaptor;
  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  @Before
  public void setUp() throws Exception {
    ApiClient apiClient =
        new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build().setReadTimeout(0);

    eventPublisher = mock(EventPublisher.class);

    sharedInformerFactory = new SharedInformerFactory(apiClient);
    nodeWatcher = new NodeWatcher(apiClient,
        ClusterDetails.builder()
            .clusterName("clusterName")
            .clusterId("clusterId")
            .cloudProviderId("cloud-provider-id")
            .kubeSystemUid("cluster-uid")
            .build(),
        sharedInformerFactory, eventPublisher);

    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("flaky test: comment this while testing on local")
  public void testOnAdd() throws InterruptedException {
    V1NodeList nodeList = new V1NodeList().metadata(new V1ListMeta().resourceVersion(START_RV)).items(Arrays.asList());

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("false"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(nodeList))));

    V1Node NODE =
        createNewNodeWithoutMeta().metadata(createNewMetadata().resourceVersion(END_RV).creationTimestamp(TIMESTAMP));
    Watch.Response<V1Node> watchResponse = new Watch.Response<>(EventType.ADDED.name(), NODE);

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("true"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(watchResponse))));

    sharedInformerFactory.startAllRegisteredInformers();
    // try increase sleep if no requests received
    Thread.sleep(200);

    WireMock.verify(1, getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("false")));
    WireMock.verify(getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("true")));

    verify(eventPublisher, times(2)).publishMessage(any(Message.class), any(Timestamp.class), any(Map.class));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(eventPublisher, times(2))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(NodeInfo.class, nodeInfo -> {
      assertThat(nodeInfo.getNodeUid()).isEqualTo(UID);
      assertThat(nodeInfo.getNodeName()).isEqualTo(NAME);
      assertThat(HTimestamps.toMillis(nodeInfo.getCreationTime())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(nodeInfo.getLabelsMap()).isEqualTo(LABELS);
      assertThat(mapArgumentCaptor.getValue().containsKey(CLUSTER_ID_IDENTIFIER));
      assertThat(mapArgumentCaptor.getValue().containsKey(UID));
    });

    assertThat(captor.getAllValues().get(1)).isInstanceOfSatisfying(NodeEvent.class, nodeEvent -> {
      assertThat(nodeEvent.getNodeUid()).isEqualTo(UID);
      assertThat(nodeEvent.getNodeName()).isEqualTo(NAME);
      assertThat(nodeEvent.getType()).isEqualTo(EVENT_TYPE_START);
      assertThat(HTimestamps.toMillis(nodeEvent.getTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
    });

    sharedInformerFactory.stopAllRegisteredInformers();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("flaky test: comment this while testing on local")
  public void testOnDelete() throws InterruptedException {
    V1Node NODErv1 =
        createNewNodeWithoutMeta().metadata(createNewMetadata().resourceVersion(START_RV).creationTimestamp(TIMESTAMP));

    V1NodeList nodeList =
        new V1NodeList().metadata(new V1ListMeta().resourceVersion(START_RV)).items(Arrays.asList(NODErv1));

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("false"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(nodeList))));

    V1Node NODErv2 = createNewNodeWithoutMeta().metadata(
        createNewMetadata().resourceVersion(END_RV).creationTimestamp(TIMESTAMP).deletionTimestamp(
            TIMESTAMP.toInstant().plusMillis(100).atOffset(TIMESTAMP.getOffset())));

    Watch.Response<V1Node> watchResponse = new Watch.Response<>(EventType.DELETED.name(), NODErv2);

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("true"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(watchResponse))));

    sharedInformerFactory.startAllRegisteredInformers();
    // try increase sleep if no requests received
    Thread.sleep(200);

    WireMock.verify(1, getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("false")));
    WireMock.verify(getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("true")));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(eventPublisher, times(3))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(NodeInfo.class, nodeInfo -> {
      assertThat(nodeInfo.getNodeUid()).isEqualTo(UID);
      assertThat(nodeInfo.getNodeName()).isEqualTo(NAME);
      assertThat(HTimestamps.toMillis(nodeInfo.getCreationTime())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(nodeInfo.getLabelsMap()).isEqualTo(LABELS);
      assertThat(mapArgumentCaptor.getValue().containsKey(CLUSTER_ID_IDENTIFIER));
      assertThat(mapArgumentCaptor.getValue().containsKey(UID));
    });

    // invoked because we are initializing with one node.
    assertThat(captor.getAllValues().get(1)).isInstanceOfSatisfying(NodeEvent.class, nodeEvent -> {
      assertThat(nodeEvent.getNodeUid()).isEqualTo(UID);
      assertThat(nodeEvent.getNodeName()).isEqualTo(NAME);
      assertThat(nodeEvent.getType()).isEqualTo(EVENT_TYPE_START);
      assertThat(HTimestamps.toMillis(nodeEvent.getTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
    });

    assertThat(captor.getAllValues().get(2)).isInstanceOfSatisfying(NodeEvent.class, nodeEvent -> {
      assertThat(nodeEvent.getNodeUid()).isEqualTo(UID);
      assertThat(nodeEvent.getNodeName()).isEqualTo(NAME);
      assertThat(nodeEvent.getType()).isEqualTo(EVENT_TYPE_STOP);
      assertThat(HTimestamps.toMillis(nodeEvent.getTimestamp()))
          .isEqualTo(TIMESTAMP.toInstant().plusMillis(100).toEpochMilli());
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
    });

    sharedInformerFactory.stopAllRegisteredInformers();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("flaky test: comment this while testing on local")
  public void testOnUpdate() throws InterruptedException {
    V1Node NODErv1 =
        createNewNodeWithoutMeta().metadata(createNewMetadata().resourceVersion(START_RV).creationTimestamp(TIMESTAMP));

    V1NodeList nodeList =
        new V1NodeList().metadata(new V1ListMeta().resourceVersion(START_RV)).items(Arrays.asList(NODErv1));

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("false"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(nodeList))));

    Map<String, String> LABEL2 = ImmutableMap.of("k3", "v3");
    V1Node NODErv2 = createNewNodeWithoutMeta().metadata(
        createNewMetadata().resourceVersion(END_RV).creationTimestamp(TIMESTAMP).labels(LABEL2));

    Watch.Response<V1Node> watchResponse = new Watch.Response<>(EventType.MODIFIED.name(), NODErv2);

    stubFor(get(NODE_URL_MATCHING)
                .withQueryParam("watch", equalTo("true"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(watchResponse))));

    sharedInformerFactory.startAllRegisteredInformers();
    // try increase sleep if no requests received
    Thread.sleep(200);

    WireMock.verify(1, getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("false")));
    WireMock.verify(getRequestedFor(NODE_URL_MATCHING).withQueryParam("watch", equalTo("true")));

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(eventPublisher, times(2))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(NodeInfo.class, nodeInfo -> {
      assertThat(nodeInfo.getNodeUid()).isEqualTo(UID);
      assertThat(nodeInfo.getNodeName()).isEqualTo(NAME);
      assertThat(HTimestamps.toMillis(nodeInfo.getCreationTime())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(nodeInfo.getLabelsMap()).isEqualTo(LABELS);
      assertThat(mapArgumentCaptor.getValue().containsKey(CLUSTER_ID_IDENTIFIER));
      assertThat(mapArgumentCaptor.getValue().containsKey(UID));
    });

    // invoked because we are initializing with one node
    assertThat(captor.getAllValues().get(1)).isInstanceOfSatisfying(NodeEvent.class, nodeEvent -> {
      assertThat(nodeEvent.getNodeUid()).isEqualTo(UID);
      assertThat(nodeEvent.getNodeName()).isEqualTo(NAME);
      assertThat(nodeEvent.getType()).isEqualTo(EVENT_TYPE_START);
      assertThat(HTimestamps.toMillis(nodeEvent.getTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(CLUSTER_ID_IDENTIFIER);
      assertThat(mapArgumentCaptor.getValue().keySet()).contains(UID);
    });

    sharedInformerFactory.stopAllRegisteredInformers();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldPublishNodeInfo() {
    V1Node node = createNewNode();
    nodeWatcher.publishNodeInfo(node);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(NodeInfo.class, nodeInfo -> {
      assertThat(nodeInfo.getNodeUid()).isEqualTo(UID);
      assertThat(nodeInfo.getNodeName()).isEqualTo(NAME);
      assertThat(HTimestamps.toMillis(nodeInfo.getCreationTime())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(nodeInfo.getLabelsMap()).isEqualTo(LABELS);
      assertThat(mapArgumentCaptor.getValue().containsKey(CLUSTER_ID_IDENTIFIER));
      assertThat(mapArgumentCaptor.getValue().containsKey(UID));
    });
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldPublishNodeStoppedEvent() {
    V1Node node =
        createNewNodeWithoutMeta().metadata(createNewMetadata().resourceVersion(START_RV).deletionTimestamp(TIMESTAMP));
    nodeWatcher.publishNodeStoppedEvent(node);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());
    assertThat(captor.getAllValues().get(0)).isInstanceOfSatisfying(NodeEvent.class, nodeEvent -> {
      assertThat(nodeEvent.getNodeUid()).isEqualTo(UID);
      assertThat(nodeEvent.getNodeName()).isEqualTo(NAME);
      assertThat(nodeEvent.getType()).isEqualTo(EVENT_TYPE_STOP);
      assertThat(HTimestamps.toMillis(nodeEvent.getTimestamp())).isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
      assertThat(mapArgumentCaptor.getValue().containsKey(CLUSTER_ID_IDENTIFIER));
      assertThat(mapArgumentCaptor.getValue().containsKey(UID));
    });
  }

  private V1Node createNewNode() {
    return createNewNodeWithoutMeta().metadata(
        createNewMetadata().resourceVersion(START_RV).creationTimestamp(TIMESTAMP));
  }

  private V1ObjectMeta createNewMetadata() {
    return new V1ObjectMeta().name(NAME).uid(UID).labels(LABELS);
  }

  private V1Node createNewNodeWithoutMeta() {
    return new V1Node()
        .spec(new V1NodeSpec().providerID(GCP_PROVIDER_ID))
        .status(new V1NodeStatus().allocatable(new HashMap<>()));
  }
}
