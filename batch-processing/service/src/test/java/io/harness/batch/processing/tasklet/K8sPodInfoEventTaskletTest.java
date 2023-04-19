/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData.PrunedInstanceDataBuilder;
import static io.harness.ccm.commons.constants.CloudProvider.AZURE;
import static io.harness.ccm.commons.entities.k8s.K8sWorkload.encodeDotsInKey;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.NIKUNJ;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData;
import io.harness.batch.processing.billing.writer.support.ClusterDataGenerationValidator;
import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.HarnessServiceInfoFetcher;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.ClusterRecordDao;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.PodEvent;
import io.harness.perpetualtask.k8s.watch.PodEvent.EventType;
import io.harness.perpetualtask.k8s.watch.PodInfo;
import io.harness.perpetualtask.k8s.watch.Quantity;
import io.harness.perpetualtask.k8s.watch.Resource;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.security.authentication.BatchQueryConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.repeat.RepeatStatus;

@OwnedBy(HarnessTeam.CE)
@RunWith(MockitoJUnitRunner.class)
public class K8sPodInfoEventTaskletTest extends BaseTaskletTest {
  @InjectMocks private K8sPodEventTasklet k8sPodEventTasklet;
  @InjectMocks private K8sPodInfoTasklet k8sPodInfoTasklet;
  @Mock private BatchMainConfig config;
  @Mock private WorkloadRepository workloadRepository;
  @Mock private InstanceDataService instanceDataService;
  @Mock private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Mock private PublishedMessageDao publishedMessageDao;
  @Mock private HarnessServiceInfoFetcher harnessServiceInfoFetcher;
  @Mock private ClusterDataGenerationValidator clusterDataGenerationValidator;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ClusterRecordDao cgClusterRecordDao;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String POD_UID = "pod_uid";
  private static final String POD_NAME = "pod_name";
  private static final String NODE_POOL_NAME = "manager-pool";
  private static final String NAMESPACE = "namespace";
  private static final String KUBE_SYSTEM_NAMESPACE = "kube-system";
  private static final String KUBE_PROXY_POD_NAME = "kube-proxy-pod";
  private static final long CPU_AMOUNT = 1_000_000_000L; // 1 vcpu in nanocores
  private static final long MEMORY_AMOUNT = 1024L * 1024; // 1Mi in bytes
  private static final long CPU_LIMIT_AMOUNT = 2_000_000_000L;
  private static final long MEMORY_LIMIT_AMOUNT = 1024L * 1024 * 2;
  private static final String NODE_NAME = "node_name";
  private static final String CLOUD_PROVIDER_ID = "cloud_provider_id";
  private static final String CLOUD_PROVIDER_INSTANCE_ID = "cloud_provider_instance_id";
  private static final String CLOUD_PROVIDER_INSTANCE_ID_AZURE_VMSS =
      "azure:///subscriptions/20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0/resourceGroups/mc_ce_dev-resourcegroup_ce-dev-cluster2_eastus/providers/Microsoft.Compute/virtualMachineScaleSets/aks-agentpool-14257926-vmss/virtualMachines/1";
  private static final String ACCOUNT_ID = "account_id";
  private static final String CLUSTER_ID = "cluster_id";
  private static final String CLUSTER_NAME = "cluster_name";
  private static final String WORKLOAD_NAME = "workload_name";
  private static final String WORKLOAD_TYPE = "workload_type";
  private static final String WORKLOAD_ID = "workload_id";
  private static final String MAP_KEY_WITH_DOT = "harness.io/created.by";
  private static final String MAP_KEY_WITHOUT_DOT = "harness~io/created~by";
  private static final String MAP_VALUE = "harness.io/created.by";
  Map<String, String> SAMPLE_MAP = ImmutableMap.of(MAP_KEY_WITH_DOT, MAP_VALUE);

  Map<String, String> SAMPLE_MAP_ENCODED = ImmutableMap.of(MAP_KEY_WITHOUT_DOT, MAP_VALUE);

  private final Instant NOW = Instant.now();
  private final Timestamp START_TIMESTAMP = HTimestamps.fromInstant(NOW.minus(1, ChronoUnit.DAYS));

  @Before
  public void setup() {
    when(config.getBatchQueryConfig()).thenReturn(BatchQueryConfig.builder().queryBatchSize(50).build());
    when(clusterDataGenerationValidator.shouldGenerateClusterData(any(), any())).thenReturn(true);
    when(instanceDataBulkWriteService.updateInstanceEvent(any())).thenReturn(true);
    when(instanceDataBulkWriteService.upsertInstanceInfo(any())).thenReturn(true);
    when(harnessServiceInfoFetcher.fetchHarnessServiceInfo(any(), any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(featureFlagService.isEnabled(eq(FeatureName.NODE_RECOMMENDATION_AGGREGATE), eq(ACCOUNT_ID))).thenReturn(false);
    io.harness.ccm.cluster.entities.ClusterRecord clusterRecord =
        io.harness.ccm.cluster.entities.ClusterRecord.builder()
            .cluster(DirectKubernetesCluster.builder().clusterName(CLUSTER_NAME).build())
            .build();
    when(cloudToHarnessMappingService.getClusterRecord(CLUSTER_ID)).thenReturn(clusterRecord);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testPodInfoExecute() {
    PrunedInstanceData instanceData = getNodeInstantData(CloudProvider.GCP);
    when(instanceDataService.fetchPrunedInstanceDataWithName(
             ACCOUNT_ID, CLUSTER_ID, NODE_NAME, HTimestamps.toMillis(START_TIMESTAMP)))
        .thenReturn(instanceData);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.RELEASE_NAME, K8sCCMConstants.RELEASE_NAME);

    when(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, CLOUD_PROVIDER_ID, NAMESPACE, POD_NAME, label))
        .thenReturn(harnessServiceInfo());
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "M"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, "M"));
    Map<String, Quantity> limitQuantity = new HashMap<>();
    limitQuantity.put("cpu", getQuantity(CPU_LIMIT_AMOUNT, "M"));
    limitQuantity.put("memory", getQuantity(MEMORY_LIMIT_AMOUNT, "M"));
    Resource resource = Resource.newBuilder().putAllRequests(requestQuantity).putAllLimits(limitQuantity).build();
    PublishedMessage k8sPodInfoMessage =
        getK8sPodInfoMessage(POD_UID, POD_NAME, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID, CLUSTER_ID, CLUSTER_NAME,
            NAMESPACE, label, SAMPLE_MAP, resource, START_TIMESTAMP, WORKLOAD_NAME, WORKLOAD_TYPE, WORKLOAD_ID);

    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(k8sPodInfoMessage));

    RepeatStatus repeatStatus = k8sPodInfoTasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testPodMetadataAnnotation() {
    PublishedMessage k8sPodInfoMessage = getK8sPodInfoMessageWithAnnotations(SAMPLE_MAP);
    InstanceInfo instanceInfo = k8sPodInfoTasklet.process(k8sPodInfoMessage);
    assertThat(instanceInfo.getMetadataAnnotations()).isEqualTo(SAMPLE_MAP_ENCODED);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testPodMetadataAnnotationNotNull() {
    PublishedMessage k8sPodInfoMessage = getK8sPodInfoMessageWithAnnotations(Collections.emptyMap());
    InstanceInfo instanceInfo = k8sPodInfoTasklet.process(k8sPodInfoMessage);
    assertThat(instanceInfo.getMetadataAnnotations()).isEqualTo(Collections.emptyMap());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testPodEventExecute() {
    PublishedMessage k8sNodeEventMessage = getK8sPodEventMessage(
        POD_UID, CLOUD_PROVIDER_ID, CLUSTER_ID, ACCOUNT_ID, PodEvent.EventType.EVENT_TYPE_SCHEDULED, START_TIMESTAMP);
    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(k8sNodeEventMessage));
    RepeatStatus repeatStatus = k8sPodEventTasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceStartPodEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage = getK8sPodEventMessage(
        POD_UID, CLOUD_PROVIDER_ID, CLUSTER_ID, ACCOUNT_ID, PodEvent.EventType.EVENT_TYPE_SCHEDULED, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sPodEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
    assertThat(instanceEvent.getTimestamp()).isEqualTo(HTimestamps.toInstant(START_TIMESTAMP));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInvalidInstancePodEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage = getK8sPodEventMessage(
        POD_UID, CLOUD_PROVIDER_ID, CLUSTER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_UNSPECIFIED, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sPodEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
    assertThat(instanceEvent.getType()).isEqualTo(InstanceEvent.EventType.UNKNOWN);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceStopPodEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage = getK8sPodEventMessage(
        POD_UID, CLOUD_PROVIDER_ID, CLUSTER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_TERMINATED, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sPodEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstancePodInfo() throws Exception {
    PrunedInstanceData instanceData = getNodeInstantData(CloudProvider.GCP);
    when(instanceDataService.fetchPrunedInstanceDataWithName(
             ACCOUNT_ID, CLUSTER_ID, NODE_NAME, HTimestamps.toMillis(START_TIMESTAMP)))
        .thenReturn(instanceData);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.RELEASE_NAME, K8sCCMConstants.RELEASE_NAME);
    when(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, CLOUD_PROVIDER_ID, NAMESPACE, POD_NAME, label))
        .thenReturn(harnessServiceInfo());
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "M"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, "M"));
    Map<String, Quantity> limitQuantity = new HashMap<>();
    limitQuantity.put("cpu", getQuantity(CPU_LIMIT_AMOUNT, "M"));
    limitQuantity.put("memory", getQuantity(MEMORY_LIMIT_AMOUNT, "M"));
    Resource resource = Resource.newBuilder().putAllRequests(requestQuantity).putAllLimits(limitQuantity).build();
    PublishedMessage k8sPodInfoMessage =
        getK8sPodInfoMessage(POD_UID, POD_NAME, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID, CLUSTER_ID, CLUSTER_NAME,
            NAMESPACE, label, SAMPLE_MAP, resource, START_TIMESTAMP, WORKLOAD_NAME, WORKLOAD_TYPE, WORKLOAD_ID);
    InstanceInfo instanceInfo = k8sPodInfoTasklet.process(k8sPodInfoMessage);
    io.harness.ccm.commons.beans.Resource infoResource = instanceInfo.getResource();
    io.harness.ccm.commons.beans.Resource limitResource = instanceInfo.getResourceLimit();
    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_POD);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(limitResource.getMemoryMb()).isEqualTo(2.0);
    assertThat(limitResource.getCpuUnits()).isEqualTo(2048.0);
    assertThat(metaData.get(InstanceMetaDataConstants.WORKLOAD_NAME)).isEqualTo(WORKLOAD_NAME);
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY))
        .isEqualTo(String.valueOf((double) MEMORY_AMOUNT));
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID))
        .isEqualTo(CLOUD_PROVIDER_INSTANCE_ID);
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_CPU))
        .isEqualTo(String.valueOf((double) CPU_AMOUNT));
    assertThat(metaData.get(InstanceMetaDataConstants.NODE_POOL_NAME)).isEqualTo(NODE_POOL_NAME);
    assertThat(instanceInfo.getNamespaceLabels()).isEqualTo(encodeDotsInKey(SAMPLE_MAP));
    assertThat(instanceInfo.getNamespaceLabels()).isNotEqualTo(SAMPLE_MAP);
    verify(workloadRepository).savePodWorkload(ACCOUNT_ID, (PodInfo) k8sPodInfoMessage.getMessage());
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void shouldCreateInstancePodInfoAzure() throws Exception {
    PrunedInstanceData instanceData = getNodeInstantData(CloudProvider.AZURE);
    when(instanceDataService.fetchPrunedInstanceDataWithName(
             ACCOUNT_ID, CLUSTER_ID, NODE_NAME, HTimestamps.toMillis(START_TIMESTAMP)))
        .thenReturn(instanceData);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.RELEASE_NAME, K8sCCMConstants.RELEASE_NAME);
    when(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, CLOUD_PROVIDER_ID, NAMESPACE, POD_NAME, label))
        .thenReturn(harnessServiceInfo());
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "M"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, "M"));
    Map<String, Quantity> limitQuantity = new HashMap<>();
    limitQuantity.put("cpu", getQuantity(CPU_LIMIT_AMOUNT, "M"));
    limitQuantity.put("memory", getQuantity(MEMORY_LIMIT_AMOUNT, "M"));
    Resource resource = Resource.newBuilder().putAllRequests(requestQuantity).putAllLimits(limitQuantity).build();
    PublishedMessage k8sPodInfoMessage =
        getK8sPodInfoMessage(POD_UID, POD_NAME, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID, CLUSTER_ID, CLUSTER_NAME,
            NAMESPACE, label, SAMPLE_MAP, resource, START_TIMESTAMP, WORKLOAD_NAME, WORKLOAD_TYPE, WORKLOAD_ID);
    InstanceInfo instanceInfo = k8sPodInfoTasklet.process(k8sPodInfoMessage);
    io.harness.ccm.commons.beans.Resource infoResource = instanceInfo.getResource();
    io.harness.ccm.commons.beans.Resource limitResource = instanceInfo.getResourceLimit();
    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_POD);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(limitResource.getMemoryMb()).isEqualTo(2.0);
    assertThat(limitResource.getCpuUnits()).isEqualTo(2048.0);
    assertThat(metaData.get(InstanceMetaDataConstants.WORKLOAD_NAME)).isEqualTo(WORKLOAD_NAME);
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY))
        .isEqualTo(String.valueOf((double) MEMORY_AMOUNT));
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_CPU))
        .isEqualTo(String.valueOf((double) CPU_AMOUNT));
    assertThat(metaData.get(InstanceMetaDataConstants.NODE_POOL_NAME)).isEqualTo(NODE_POOL_NAME);
    assertThat(instanceInfo.getNamespaceLabels()).isEqualTo(encodeDotsInKey(SAMPLE_MAP));
    assertThat(instanceInfo.getNamespaceLabels()).isNotEqualTo(SAMPLE_MAP);
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER)).isEqualTo(AZURE.name());
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID))
        .isEqualTo(CLOUD_PROVIDER_INSTANCE_ID_AZURE_VMSS.toLowerCase());
    verify(workloadRepository).savePodWorkload(ACCOUNT_ID, (PodInfo) k8sPodInfoMessage.getMessage());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstancePodInfoWithKubeProxyWorkload() throws Exception {
    PrunedInstanceData instanceData = getNodeInstantData(CloudProvider.GCP);
    when(instanceDataService.fetchPrunedInstanceDataWithName(
             ACCOUNT_ID, CLUSTER_ID, NODE_NAME, HTimestamps.toMillis(START_TIMESTAMP)))
        .thenReturn(instanceData);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.RELEASE_NAME, K8sCCMConstants.RELEASE_NAME);
    when(harnessServiceInfoFetcher.fetchHarnessServiceInfo(
             ACCOUNT_ID, CLOUD_PROVIDER_ID, KUBE_SYSTEM_NAMESPACE, KUBE_PROXY_POD_NAME, label))
        .thenReturn(harnessServiceInfo());
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "M"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, "M"));
    Resource resource = Resource.newBuilder().putAllRequests(requestQuantity).build();
    PublishedMessage k8sPodInfoMessage =
        getK8sPodInfoMessage(POD_UID, KUBE_PROXY_POD_NAME, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID, CLUSTER_ID,
            CLUSTER_NAME, KUBE_SYSTEM_NAMESPACE, label, SAMPLE_MAP, resource, START_TIMESTAMP, "", "", "");
    InstanceInfo instanceInfo = k8sPodInfoTasklet.process(k8sPodInfoMessage);
    io.harness.ccm.commons.beans.Resource infoResource = instanceInfo.getResource();
    io.harness.ccm.commons.beans.Resource limitResource = instanceInfo.getResourceLimit();

    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_POD);
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(limitResource.getCpuUnits()).isEqualTo(0.0);
    assertThat(limitResource.getMemoryMb()).isEqualTo(0.0);
    assertThat(metaData.get(InstanceMetaDataConstants.WORKLOAD_NAME)).isEqualTo("kube-proxy");
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY))
        .isEqualTo(String.valueOf((double) MEMORY_AMOUNT));
    assertThat(metaData.get(InstanceMetaDataConstants.PARENT_RESOURCE_CPU))
        .isEqualTo(String.valueOf((double) CPU_AMOUNT));
    verify(workloadRepository).savePodWorkload(ACCOUNT_ID, (PodInfo) k8sPodInfoMessage.getMessage());
  }

  private PrunedInstanceData getNodeInstantData(CloudProvider cloudProvider) {
    Map<String, String> nodeMetaData = new HashMap<>();
    nodeMetaData.put(InstanceMetaDataConstants.REGION, InstanceMetaDataConstants.REGION);
    nodeMetaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    nodeMetaData.put(InstanceMetaDataConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    nodeMetaData.put(K8sCCMConstants.GKE_NODE_POOL_KEY, NODE_POOL_NAME);
    PrunedInstanceDataBuilder instanceData = PrunedInstanceData.builder();
    io.harness.ccm.commons.beans.Resource instanceResource = io.harness.ccm.commons.beans.Resource.builder()
                                                                 .cpuUnits((double) CPU_AMOUNT)
                                                                 .memoryMb((double) MEMORY_AMOUNT)
                                                                 .build();
    if (CloudProvider.GCP == cloudProvider) {
      nodeMetaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
      instanceData.cloudProviderInstanceId(CLOUD_PROVIDER_INSTANCE_ID);
    } else if (CloudProvider.AZURE == cloudProvider) {
      nodeMetaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AZURE.name());
      instanceData.cloudProviderInstanceId(CLOUD_PROVIDER_INSTANCE_ID_AZURE_VMSS.toLowerCase());
    } else if (CloudProvider.AWS == cloudProvider) {
      nodeMetaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.AWS.name());
      instanceData.cloudProviderInstanceId(CLOUD_PROVIDER_INSTANCE_ID);
    }

    return instanceData.instanceId(NODE_NAME).totalResource(instanceResource).metaData(nodeMetaData).build();
  }

  private Quantity getQuantity(long amount, String unit) {
    return Quantity.newBuilder().setAmount(amount).setUnit(unit).build();
  }

  private PublishedMessage getK8sPodInfoMessage(String podUid, String podName, String nodeName, String cloudProviderId,
      String accountId, String clusterId, String clusterName, String namespace, Map<String, String> label,
      Map<String, String> namespaceLabels, Resource resource, Timestamp timestamp, String workloadName,
      String workloadType, String workloadId) {
    PodInfo nodeInfo = PodInfo.newBuilder()
                           .setPodUid(podUid)
                           .setPodName(podName)
                           .setNodeName(nodeName)
                           .setCloudProviderId(cloudProviderId)
                           .setClusterId(clusterId)
                           .setClusterName(clusterName)
                           .setNamespace(namespace)
                           .putAllLabels(label)
                           .putAllNamespaceLabels(namespaceLabels)
                           .setTotalResource(resource)
                           .setCreationTimestamp(timestamp)
                           .setTopLevelOwner(getOwner(workloadName, workloadType, workloadId))
                           .build();
    return getPublishedMessage(accountId, nodeInfo, HTimestamps.toMillis(timestamp));
  }

  private PublishedMessage getK8sPodInfoMessageWithAnnotations(Map<String, String> metadataAnnotations) {
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "M"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, "M"));

    PodInfo nodeInfo = PodInfo.newBuilder()
                           .setPodUid(POD_UID)
                           .setPodName(POD_NAME)
                           .setNodeName(NODE_NAME)
                           .setCloudProviderId(CLOUD_PROVIDER_ID)
                           .setClusterId(CLUSTER_ID)
                           .setClusterName(CLUSTER_NAME)
                           .setNamespace(NAMESPACE)
                           .putAllLabels(SAMPLE_MAP)
                           .putAllNamespaceLabels(SAMPLE_MAP)
                           .setTotalResource(Resource.newBuilder().putAllRequests(requestQuantity).build())
                           .setCreationTimestamp(START_TIMESTAMP)
                           .putAllMetadataAnnotations(metadataAnnotations)
                           .setTopLevelOwner(getOwner(WORKLOAD_NAME, WORKLOAD_TYPE, WORKLOAD_ID))
                           .build();
    return getPublishedMessage(ACCOUNT_ID, nodeInfo, HTimestamps.toMillis(START_TIMESTAMP));
  }

  private io.harness.perpetualtask.k8s.watch.Owner getOwner(
      String workloadName, String workloadType, String workloadId) {
    return io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
        .setName(workloadName)
        .setKind(workloadType)
        .setUid(workloadId)
        .build();
  }

  private PublishedMessage getK8sPodEventMessage(String PodUid, String cloudProviderId, String clusterId,
      String accountId, PodEvent.EventType eventType, Timestamp timestamp) {
    PodEvent podEvent = PodEvent.newBuilder()
                            .setPodUid(PodUid)
                            .setCloudProviderId(cloudProviderId)
                            .setClusterId(clusterId)
                            .setType(eventType)
                            .setTimestamp(timestamp)
                            .build();
    return getPublishedMessage(accountId, podEvent, HTimestamps.toMillis(timestamp));
  }

  private PublishedMessage getPublishedMessage(String accountId, Message message, long occurredAt) {
    Any payload = Any.pack(message);
    return PublishedMessage.builder()
        .data(payload.toByteArray())
        .occurredAt(occurredAt)
        .type(message.getClass().getName())
        .accountId(accountId)
        .build();
  }

  private Optional<HarnessServiceInfo> harnessServiceInfo() {
    return Optional.of(new HarnessServiceInfo(
        "serviceId", "appId", "cloudProviderId", "envId", "infraMappingId", "deploymentSummaryId"));
  }
}
