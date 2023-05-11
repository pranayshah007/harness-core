/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.informer.SharedInformerFactoryFactory;
import io.harness.perpetualtask.k8s.utils.K8sClusterHelper;
import io.harness.perpetualtask.k8s.utils.K8sWatcherHelper;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.util.ClientBuilder;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

@Slf4j
public class K8sWatchServiceDelegateTest extends DelegateTestBase {
  private static final String KUBE_UID = "ed044e6a-8b7f-456c-b035-f05e9ce56a60";
  private K8sWatchServiceDelegate k8sWatchServiceDelegate;
  private WatcherFactory watcherFactory;
  private static final KubernetesConfig KUBERNETES_CONFIG = KubernetesConfig.builder().build();

  @Rule public WireMockRule wireMockRule = new WireMockRule(65221);
  @Inject KryoSerializer kryoSerializer;

  private static final String URL_REGEX_SUFFIX = "(\\?(.*))?";

  @Before
  public void setUp() throws Exception {
    watcherFactory = mock(WatcherFactory.class);
    SharedInformerFactoryFactory sharedInformerFactoryFactory = mock(SharedInformerFactoryFactory.class);
    ApiClientFactory apiClientFactory = mock(ApiClientFactory.class);

    this.k8sWatchServiceDelegate =
        new K8sWatchServiceDelegate(watcherFactory, sharedInformerFactoryFactory, apiClientFactory);

    SharedInformerFactory sharedInformerFactory = mock(SharedInformerFactory.class);
    when(sharedInformerFactoryFactory.createSharedInformerFactory(any(), any())).thenReturn(sharedInformerFactory);
    SharedIndexInformer sharedIndexInformer = mock(SharedIndexInformer.class);
    when(sharedInformerFactory.getExistingSharedIndexInformer(any())).thenReturn(sharedIndexInformer);
    when(sharedIndexInformer.hasSynced()).thenReturn(true);

    Indexer indexer = mock(Indexer.class);
    when(sharedIndexInformer.getIndexer()).thenReturn(indexer);

    when(apiClientFactory.getClient(any()))
        .thenReturn(new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build());

    stubFor(get(urlMatching("^/api/v1/namespaces/kube-system" + URL_REGEX_SUFFIX))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(new V1NamespaceBuilder()
                                                                   .withKind("Namespace")
                                                                   .withApiVersion("v1")
                                                                   .withNewMetadata()
                                                                   .withName("kube-system")
                                                                   .withUid(KUBE_UID)
                                                                   .endMetadata()
                                                                   .build()))));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldCreateAllWatchersAndFetchers() {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId = UUID.randomUUID().toString();

    ByteString k8sClusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster").namespace("namespace").cloudProvider(null).build()));

    K8sWatchTaskParams k8sWatchTaskParams = K8sWatchTaskParams.newBuilder()
                                                .setK8SClusterConfig(k8sClusterConfig)
                                                .setCloudProviderId(cloudProviderId)
                                                .setClusterId(clusterId)
                                                .build();
    String watchId = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);

    assertThat(watchId).isNotNull();
    assertThat(k8sWatchServiceDelegate.watchIds()).contains(watchId);

    verify(watcherFactory, atLeastOnce()).createPodWatcher(any(), any(), any(), any(), any(), any());
    verify(watcherFactory, atLeastOnce()).createNodeWatcher(any(), any(), any());
    verify(watcherFactory, atLeastOnce()).createPVCFetcher(any(ApiClient.class), any(SharedInformerFactory.class));
    verify(watcherFactory, atLeastOnce())
        .createPVWatcher(any(ApiClient.class), any(ClusterDetails.class), any(SharedInformerFactory.class));
    verify(watcherFactory, atLeastOnce())
        .createNamespaceFetcher(any(ApiClient.class), any(SharedInformerFactory.class));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldCreateClusterEventWatch() {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId = UUID.randomUUID().toString();
    ByteString k8sClusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster").namespace("namespace").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams = K8sWatchTaskParams.newBuilder()
                                                .setK8SClusterConfig(k8sClusterConfig)
                                                .setCloudProviderId(cloudProviderId)
                                                .setClusterId(clusterId)
                                                .build();
    String watchId = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);
    assertThat(watchId).isNotNull();
    assertThat(k8sWatchServiceDelegate.watchIds()).contains(watchId);
    assertThat(K8sClusterHelper.isSeen(clusterId, KUBE_UID)).isFalse();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotCreateDuplicateWatch() {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId = UUID.randomUUID().toString();
    ByteString k8sClusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster").namespace("namespace").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams = K8sWatchTaskParams.newBuilder()
                                                .setK8SClusterConfig(k8sClusterConfig)
                                                .setCloudProviderId(cloudProviderId)
                                                .setClusterId(clusterId)
                                                .build();
    String watch1 = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);
    String watch2;
    try (MockedStatic<K8sWatcherHelper> k8sWatcherHelperMock = Mockito.mockStatic(K8sWatcherHelper.class)) {
      k8sWatcherHelperMock
          .when(()
                    -> K8sWatcherHelper.updateLastSeen(
                        String.format(K8sWatcherHelper.POD_WATCHER_PREFIX, k8sWatchTaskParams.getClusterId()),
                        Instant.now()))
          .thenAnswer((Answer<?>) invocation -> null);
      watch2 = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);
    }
    assertThat(watch2).isEqualTo(watch1);
    verify(watcherFactory).createPodWatcher(any(ApiClient.class), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldCreateMultipleWatchesIfNotDuplicate() {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId1 = UUID.randomUUID().toString();
    ByteString k8sClusterConfig1 = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster1").namespace("namespace1").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams1 = K8sWatchTaskParams.newBuilder()
                                                 .setClusterId("clusterId1")
                                                 .setK8SClusterConfig(k8sClusterConfig1)
                                                 .setCloudProviderId(cloudProviderId1)
                                                 .setClusterId(clusterId)
                                                 .build();
    String cloudProviderId2 = UUID.randomUUID().toString();
    ByteString k8sClusterConfig2 = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster2").namespace("namespace2").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams2 = K8sWatchTaskParams.newBuilder()
                                                 .setClusterId("clusterId2")
                                                 .setK8SClusterConfig(k8sClusterConfig2)
                                                 .setCloudProviderId(cloudProviderId2)
                                                 .build();
    String watch1 = k8sWatchServiceDelegate.create(k8sWatchTaskParams1, KUBERNETES_CONFIG);
    String watch2 = k8sWatchServiceDelegate.create(k8sWatchTaskParams2, KUBERNETES_CONFIG);
    assertThat(watch2).isNotEqualTo(watch1);
    verify(watcherFactory, times(2)).createPodWatcher(any(ApiClient.class), any(), any(), any(), any(), any());
    assertThat(k8sWatchServiceDelegate.watchIds()).containsExactlyInAnyOrder(watch1, watch2);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldDeletePodWatch() throws Exception {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId = UUID.randomUUID().toString();
    ByteString k8sClusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster").namespace("namespace").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams = K8sWatchTaskParams.newBuilder()
                                                .setK8SClusterConfig(k8sClusterConfig)
                                                .setCloudProviderId(cloudProviderId)
                                                .setClusterId(clusterId)
                                                .build();
    String watchId = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);
    assertThat(k8sWatchServiceDelegate.watchIds()).contains(watchId);
    k8sWatchServiceDelegate.delete(watchId);
    assertThat(k8sWatchServiceDelegate.watchIds()).doesNotContain(watchId);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldDeleteNodeWatch() throws Exception {
    String clusterId = UUID.randomUUID().toString();
    String cloudProviderId = UUID.randomUUID().toString();
    ByteString k8sClusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(
        K8sClusterConfig.builder().clusterName("test-cluster").namespace("namespace").cloudProvider(null).build()));
    K8sWatchTaskParams k8sWatchTaskParams = K8sWatchTaskParams.newBuilder()
                                                .setK8SClusterConfig(k8sClusterConfig)
                                                .setCloudProviderId(cloudProviderId)
                                                .setClusterId(clusterId)
                                                .build();
    String watchId = k8sWatchServiceDelegate.create(k8sWatchTaskParams, KUBERNETES_CONFIG);
    assertThat(k8sWatchServiceDelegate.watchIds()).contains(watchId);
    k8sWatchServiceDelegate.delete(watchId);
    assertThat(k8sWatchServiceDelegate.watchIds()).doesNotContain(watchId);
  }
}
