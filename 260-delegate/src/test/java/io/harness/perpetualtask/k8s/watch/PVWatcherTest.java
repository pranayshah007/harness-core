/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.perpetualtask.k8s.watch.PVInfo.PVType.PV_TYPE_AWS_EBS;
import static io.harness.perpetualtask.k8s.watch.PVInfo.PVType.PV_TYPE_AZURE_DISK;
import static io.harness.perpetualtask.k8s.watch.PVInfo.PVType.PV_TYPE_GCE_PERSISTENT_DISK;
import static io.harness.rule.OwnerRule.UTSAV;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpecBuilder;
import io.kubernetes.client.openapi.models.V1StorageClass;
import io.kubernetes.client.openapi.models.V1StorageClassBuilder;
import io.kubernetes.client.util.ClientBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

public class PVWatcherTest extends CategoryTest {
  private PVWatcher pvWatcher;
  private EventPublisher eventPublisher;
  private V1PersistentVolume samplePV;
  private PVInfo pvInfo;
  private PVEvent pvEvent;
  private ClusterDetails clusterDetails;

  final OffsetDateTime TIMESTAMP = OffsetDateTime.now();
  final OffsetDateTime DELETION_TIMESTAMP = TIMESTAMP.plusMinutes(5);

  private static final String SC_NAME = "storage_class_name";
  private static final String SC_URL = "^/apis/storage.k8s.io/v1/storageclasses/";

  ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
  @Captor ArgumentCaptor<Map<String, String>> mapArgumentCaptor;

  @Rule public WireMockRule wireMockRule = new WireMockRule(0);

  @Before
  public void setUp() throws Exception {
    ApiClient apiClient =
        new ClientBuilder().setBasePath("http://localhost:" + wireMockRule.port()).build().setReadTimeout(0);

    eventPublisher = mock(EventPublisher.class);
    MockitoAnnotations.initMocks(this);
    clusterDetails = ClusterDetails.builder()
                         .clusterName("clusterName")
                         .clusterId("clusterId")
                         .cloudProviderId("cloud-provider-id")
                         .kubeSystemUid("cluster-uid")
                         .build();
    pvWatcher = new PVWatcher(apiClient, clusterDetails, new SharedInformerFactory(apiClient), eventPublisher);

    samplePV = new V1PersistentVolumeBuilder()
                   .withNewMetadata()
                   .withUid("uid")
                   .withName("test-pv")
                   .withCreationTimestamp(TIMESTAMP)
                   .endMetadata()
                   .withNewSpec()
                   .withNewGcePersistentDisk()
                   .withFsType("fsType")
                   .withPdName("gke-pr-private-0353f5d-pvc-ffefcf96-b30b-430b-9901-e5d8f6f636fe")
                   .endGcePersistentDisk()
                   .withCapacity(ImmutableMap.of("storage", new Quantity("10Ki")))
                   .withStorageClassName("standard")
                   .withNewClaimRef()
                   .withName("mongo-data")
                   .withNamespace("harness-namespace")
                   .endClaimRef()
                   .endSpec()
                   .build();

    pvEvent = PVEvent.newBuilder()
                  .setCloudProviderId(clusterDetails.getCloudProviderId())
                  .setClusterId(clusterDetails.getClusterId())
                  .setKubeSystemUid(clusterDetails.getKubeSystemUid())
                  .setTimestamp(HTimestamps.fromMillis(TIMESTAMP.toInstant().toEpochMilli()))
                  .setPvName(samplePV.getMetadata().getName())
                  .setPvUid(samplePV.getMetadata().getUid())
                  .build();

    pvInfo = PVInfo.newBuilder()
                 .setClaimNamespace(samplePV.getSpec().getClaimRef().getNamespace())
                 .setClaimName(samplePV.getSpec().getClaimRef().getName())
                 .setPvType(PV_TYPE_GCE_PERSISTENT_DISK)
                 .setCreationTimestamp(HTimestamps.fromMillis(TIMESTAMP.toInstant().toEpochMilli()))
                 .setCapacity(K8sResourceUtils.getStorageCapacity(samplePV.getSpec()))
                 .setPvUid(samplePV.getMetadata().getUid())
                 .setPvName(samplePV.getMetadata().getName())
                 .putAllLabels(Collections.emptyMap())
                 .setCapacity(K8sResourceUtils.getStorageCapacity(samplePV.getSpec()))
                 .setClusterId(clusterDetails.getClusterId())
                 .setClusterName(clusterDetails.getClusterName())
                 .setCloudProviderId(clusterDetails.getCloudProviderId())
                 .setKubeSystemUid(clusterDetails.getKubeSystemUid())
                 .build();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("TODO: needs to be fixed for the new version of com.github.tomakehurst.wiremock")
  public void shouldPublishOnAdd() throws Exception {
    pvWatcher.onAdd(samplePV);

    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    WireMock.verify(1, getRequestedFor(urlMatching(SC_URL + samplePV.getSpec().getStorageClassName() + ".*")));

    assertThat(captor.getAllValues().get(0)).isEqualTo(pvInfo);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("TODO: needs to be fixed for the new version of com.github.tomakehurst.wiremock")
  public void testPublishOnVolumeExpansion() throws Exception {
    V1PersistentVolume oldPV = new V1PersistentVolumeBuilder()
                                   .withNewMetadataLike(samplePV.getMetadata())
                                   .endMetadata()
                                   .withNewSpecLike(samplePV.getSpec())
                                   .endSpec()
                                   .build();
    oldPV.getSpec().setCapacity(ImmutableMap.of("storage", new Quantity("9Ki")));

    pvWatcher.onUpdate(oldPV, samplePV);

    verify(eventPublisher, times(2))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    WireMock.verify(1, getRequestedFor(urlMatching(SC_URL + samplePV.getSpec().getStorageClassName() + ".*")));

    System.out.println(K8sResourceUtils.getStorageCapacity(samplePV.getSpec()));
    assertThat(captor.getAllValues().get(0)).isEqualTo(pvInfo);

    assertThat(captor.getAllValues().get(1).toString())
        .contains(pvEvent.getPvName())
        .contains(pvEvent.getPvUid())
        .contains(PVEvent.EventType.EVENT_TYPE_EXPANSION.name());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testPublishOnDelete() throws Exception {
    samplePV.getMetadata().setDeletionTimestamp(DELETION_TIMESTAMP);
    pvWatcher.onDelete(samplePV, false);

    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    verify(eventPublisher, times(1))
        .publishMessage(captor.capture(), any(Timestamp.class), mapArgumentCaptor.capture());

    WireMock.verify(0, getRequestedFor(urlMatching(SC_URL + samplePV.getSpec().getStorageClassName() + ".*")));

    assertThat(captor.getAllValues().get(0))
        .isEqualTo(PVEvent.newBuilder(pvEvent)
                       .setEventType(PVEvent.EventType.EVENT_TYPE_STOP)
                       .setTimestamp(HTimestamps.fromMillis(DELETION_TIMESTAMP.toInstant().toEpochMilli()))
                       .build());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetPvType() {
    PVInfo.PVType aws_ebs = pvWatcher.getPvType(
        new V1PersistentVolumeSpecBuilder().withNewAwsElasticBlockStore().endAwsElasticBlockStore().build());
    PVInfo.PVType gce_pd = pvWatcher.getPvType(
        new V1PersistentVolumeSpecBuilder().withNewGcePersistentDisk().endGcePersistentDisk().build());
    PVInfo.PVType azure_disk =
        pvWatcher.getPvType(new V1PersistentVolumeSpecBuilder().withNewAzureDisk().endAzureDisk().build());

    assertThat(aws_ebs).isEqualTo(PV_TYPE_AWS_EBS);
    assertThat(gce_pd).isEqualTo(PV_TYPE_GCE_PERSISTENT_DISK);
    assertThat(azure_disk).isEqualTo(PV_TYPE_AZURE_DISK);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  @Ignore("TODO: needs to be fixed for the new version of com.github.tomakehurst.wiremock")
  public void testGetStorageClassParameters() {
    V1PersistentVolume samplePv =
        new V1PersistentVolumeBuilder().withNewSpec().withStorageClassName(SC_NAME).endSpec().build();
    Map<String, String> params = pvWatcher.getStorageClassParameters(samplePv);
    assertThat(params).isNull();

    V1StorageClass storageClass = new V1StorageClassBuilder().withParameters(ImmutableMap.of("type", "pd-ssd")).build();

    stubFor(WireMock.get(urlMatching(SC_URL + SC_NAME + ".*"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(storageClass))));

    params = pvWatcher.getStorageClassParameters(samplePv);
    assertThat(params).isEqualTo(ImmutableMap.of("type", "pd-ssd"));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testStorageClassParamsCache() {
    V1PersistentVolume samplePv =
        new V1PersistentVolumeBuilder().withNewSpec().withStorageClassName(SC_NAME).endSpec().build();

    V1StorageClass storageClass = new V1StorageClassBuilder().withParameters(ImmutableMap.of("type", "pd-ssd")).build();
    stubFor(WireMock.get(urlMatching(SC_URL + SC_NAME + ".*"))
                .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(new JSON().serialize(storageClass))));

    pvWatcher.getStorageClassParameters(samplePv);
    pvWatcher.getStorageClassParameters(samplePv);
    WireMock.verify(1, getRequestedFor(urlMatching(SC_URL + SC_NAME + ".*")));
  }
}
