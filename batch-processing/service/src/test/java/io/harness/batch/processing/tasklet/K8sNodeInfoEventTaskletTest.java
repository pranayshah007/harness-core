/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.ccm.commons.constants.CloudProvider.AWS;
import static io.harness.ccm.commons.constants.CloudProvider.AZURE;
import static io.harness.ccm.commons.constants.CloudProvider.GCP;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.NIKUNJ;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.CloudProviderService;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.billing.InstanceCategory;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.NodeEvent;
import io.harness.perpetualtask.k8s.watch.NodeEvent.EventType;
import io.harness.perpetualtask.k8s.watch.NodeInfo;
import io.harness.perpetualtask.k8s.watch.Quantity;
import io.harness.rule.Owner;
import io.harness.testsupport.BaseTaskletTest;

import software.wings.security.authentication.BatchQueryConfig;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
public class K8sNodeInfoEventTaskletTest extends BaseTaskletTest {
  @InjectMocks private K8sNodeEventTasklet k8sNodeEventTasklet;
  @InjectMocks private K8sNodeInfoTasklet k8sNodeInfoTasklet;
  @Mock private CloudProviderService cloudProviderService;
  @Mock private InstanceResourceService instanceResourceService;
  @Mock private InstanceDataService instanceDataService;
  @Mock private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Mock private PublishedMessageDao publishedMessageDao;
  @Mock private BatchMainConfig config;
  @Mock private FeatureFlagService featureFlagService;

  private static final String NODE_UID = "node_uid";
  private static final String NODE_POOL_NAME = "manager-pool";
  private static final long CPU_AMOUNT = 1_000_000_000L; // 1 vcpu in nanocores
  private static final long MEMORY_AMOUNT = 1024L * 1024; // 1Mi in bytes
  private static final String NODE_NAME = "node_name";
  private static final String CLOUD_PROVIDER_ID = "cloud_provider_id";
  private static final String PROVIDER_ID = "gce://qa-setup/us-west1-b/gke-dev-private-delegate-596a23b8-hww7";
  private static final String PROVIDER_ID_AZURE_VMSS =
      "azure:///subscriptions/20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0/resourceGroups/mc_ce_dev-resourcegroup_ce-dev-cluster2_eastus/providers/Microsoft.Compute/virtualMachineScaleSets/aks-agentpool-14257926-vmss/virtualMachines/1";
  private static final String PROVIDER_ID_AZURE_VM =
      "azure:///subscriptions/20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0/resourceGroups/mc_ce_dev-resourcegroup_cetest1_eastus/providers/Microsoft.Compute/virtualMachines/aks-agentpool-41737416-1";
  private static final String CLUSTER_ID = "cluster_id";
  private static final String CLUSTER_NAME = "cluster_name";
  private final Instant NOW = Instant.now();
  private final Timestamp START_TIMESTAMP = HTimestamps.fromInstant(NOW.minus(1, ChronoUnit.DAYS));

  @Before
  public void setup() {
    when(config.getBatchQueryConfig()).thenReturn(BatchQueryConfig.builder().queryBatchSize(50).build());
    when(instanceDataBulkWriteService.updateInstanceEvent(any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.NODE_RECOMMENDATION_AGGREGATE), eq(ACCOUNT_ID))).thenReturn(false);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testNodeInfoExecute() {
    when(cloudProviderService.getK8SCloudProvider(any(), any())).thenReturn(CloudProvider.GCP);
    when(cloudProviderService.getFirstClassSupportedCloudProviders()).thenReturn(ImmutableList.of(AWS, AZURE, GCP));

    when(instanceResourceService.getComputeVMResource(any(), any(), any()))
        .thenReturn(Resource.builder().cpuUnits(1024.0).memoryMb(1024.0).build());
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "n"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, ""));
    PublishedMessage k8sNodeInfoMessage = getK8sNodeInfoMessage(NODE_UID, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID,
        CLUSTER_NAME, CLUSTER_ID, label, requestQuantity, START_TIMESTAMP, PROVIDER_ID);

    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(k8sNodeInfoMessage));

    RepeatStatus repeatStatus = k8sNodeInfoTasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testNodeEventExecute() {
    PublishedMessage k8sNodeEventMessage =
        getK8sNodeEventMessage(NODE_UID, CLOUD_PROVIDER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_STOP, START_TIMESTAMP);
    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(k8sNodeEventMessage));
    RepeatStatus repeatStatus = k8sNodeEventTasklet.execute(null, chunkContext);
    assertThat(repeatStatus).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceStartNodeEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage =
        getK8sNodeEventMessage(NODE_UID, CLOUD_PROVIDER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_START, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sNodeEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
    assertThat(instanceEvent.getTimestamp()).isEqualTo(HTimestamps.toInstant(START_TIMESTAMP));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInvalidInstanceNodeEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage = getK8sNodeEventMessage(
        NODE_UID, CLOUD_PROVIDER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_UNSPECIFIED, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sNodeEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
    assertThat(instanceEvent.getType()).isEqualTo(InstanceEvent.EventType.UNKNOWN);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceStopNodeEvent() throws Exception {
    PublishedMessage k8sNodeEventMessage =
        getK8sNodeEventMessage(NODE_UID, CLOUD_PROVIDER_ID, ACCOUNT_ID, EventType.EVENT_TYPE_STOP, START_TIMESTAMP);
    InstanceEvent instanceEvent = k8sNodeEventTasklet.process(k8sNodeEventMessage);
    assertThat(instanceEvent).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateEmptyInstanceNodeInfo() throws Exception {
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "n"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, ""));

    when(instanceDataService.fetchInstanceData(ACCOUNT_ID, CLUSTER_ID, NODE_UID))
        .thenReturn(InstanceData.builder().build());
    when(cloudProviderService.getK8SCloudProvider(any(), any())).thenReturn(CloudProvider.GCP);
    PublishedMessage k8sNodeEventMessage = getK8sNodeInfoMessage(NODE_UID, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID,
        CLUSTER_NAME, CLUSTER_ID, emptyMap(), emptyMap(), START_TIMESTAMP, PROVIDER_ID);
    InstanceInfo instanceInfo = k8sNodeInfoTasklet.processNodeInfoMessage(k8sNodeEventMessage);
    assertThat(instanceInfo.getInstanceId()).isNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceNodeInfo() throws Exception {
    when(cloudProviderService.getK8SCloudProvider(any(), any())).thenReturn(CloudProvider.GCP);
    when(cloudProviderService.getFirstClassSupportedCloudProviders()).thenReturn(ImmutableList.of(AWS, AZURE, GCP));

    when(instanceResourceService.getComputeVMResource(any(), any(), any()))
        .thenReturn(Resource.builder().cpuUnits(1024.0).memoryMb(1024.0).build());
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    label.put(K8sCCMConstants.GKE_NODE_POOL_KEY, NODE_POOL_NAME);
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "n"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, ""));
    PublishedMessage k8sNodeEventMessage = getK8sNodeInfoMessage(NODE_UID, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID,
        CLUSTER_NAME, CLUSTER_ID, label, requestQuantity, START_TIMESTAMP, PROVIDER_ID);
    InstanceInfo instanceInfo = k8sNodeInfoTasklet.process(k8sNodeEventMessage);
    Resource infoResource = instanceInfo.getResource();
    Resource infoAllocatableResource = instanceInfo.getAllocatableResource();
    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_NODE);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(instanceInfo.getCloudProviderInstanceId()).isEqualTo("gke-dev-private-delegate-596a23b8-hww7");
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1024.0);
    assertThat(infoAllocatableResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(infoAllocatableResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(metaData.get(InstanceMetaDataConstants.REGION)).isEqualTo(InstanceMetaDataConstants.REGION);
    assertThat(metaData.get(InstanceMetaDataConstants.INSTANCE_CATEGORY)).isEqualTo(InstanceCategory.ON_DEMAND.name());
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER)).isEqualTo(CloudProvider.GCP.name());
    assertThat(metaData.get(InstanceMetaDataConstants.NODE_POOL_NAME)).isEqualTo(NODE_POOL_NAME);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldCreateInstanceNodeInfoForAwsSpot() throws Exception {
    when(cloudProviderService.getK8SCloudProvider(any(), any())).thenReturn(CloudProvider.AWS);
    when(cloudProviderService.getFirstClassSupportedCloudProviders()).thenReturn(ImmutableList.of(AWS, AZURE, GCP));
    when(instanceResourceService.getComputeVMResource(any(), any(), any())).thenReturn(null);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    label.put(K8sCCMConstants.AWS_LIFECYCLE_KEY, "Ec2Spot");
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "n"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, ""));
    PublishedMessage k8sNodeEventMessage = getK8sNodeInfoMessage(NODE_UID, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID,
        CLUSTER_NAME, CLUSTER_ID, label, requestQuantity, START_TIMESTAMP, PROVIDER_ID);
    InstanceInfo instanceInfo = k8sNodeInfoTasklet.process(k8sNodeEventMessage);
    Resource infoResource = instanceInfo.getResource();
    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_NODE);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(metaData.get(InstanceMetaDataConstants.INSTANCE_CATEGORY)).isEqualTo(InstanceCategory.SPOT.name());
    assertThat(metaData.get(InstanceMetaDataConstants.REGION)).isEqualTo(InstanceMetaDataConstants.REGION);
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER)).isEqualTo(CloudProvider.AWS.name());
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void shouldCreateInstanceNodeInfoForAzure() throws Exception {
    when(cloudProviderService.getK8SCloudProvider(any(), any())).thenReturn(AZURE);
    when(cloudProviderService.getFirstClassSupportedCloudProviders()).thenReturn(ImmutableList.of(AWS, AZURE, GCP));
    when(instanceResourceService.getComputeVMResource(any(), any(), any())).thenReturn(null);
    Map<String, String> label = new HashMap<>();
    label.put(K8sCCMConstants.REGION, InstanceMetaDataConstants.REGION);
    label.put(K8sCCMConstants.INSTANCE_FAMILY, InstanceMetaDataConstants.INSTANCE_FAMILY);
    label.put(K8sCCMConstants.OPERATING_SYSTEM, InstanceMetaDataConstants.OPERATING_SYSTEM);
    Map<String, Quantity> requestQuantity = new HashMap<>();
    requestQuantity.put("cpu", getQuantity(CPU_AMOUNT, "n"));
    requestQuantity.put("memory", getQuantity(MEMORY_AMOUNT, ""));
    PublishedMessage k8sNodeEventMessage = getK8sNodeInfoMessage(NODE_UID, NODE_NAME, CLOUD_PROVIDER_ID, ACCOUNT_ID,
        CLUSTER_NAME, CLUSTER_ID, label, requestQuantity, START_TIMESTAMP, PROVIDER_ID_AZURE_VMSS);
    InstanceInfo instanceInfo = k8sNodeInfoTasklet.process(k8sNodeEventMessage);
    Resource infoResource = instanceInfo.getResource();
    Map<String, String> metaData = instanceInfo.getMetaData();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceInfo.getInstanceType()).isEqualTo(InstanceType.K8S_NODE);
    assertThat(instanceInfo.getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceInfo.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(infoResource.getCpuUnits()).isEqualTo(1024.0);
    assertThat(infoResource.getMemoryMb()).isEqualTo(1.0);
    assertThat(metaData.get(InstanceMetaDataConstants.REGION)).isEqualTo(InstanceMetaDataConstants.REGION);
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER)).isEqualTo(AZURE.name());
    assertThat(metaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID))
        .isEqualTo(PROVIDER_ID_AZURE_VMSS.toLowerCase());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnCloudProviderInstance() {
    String providerId = "aws:///eu-west-2c/i-072ecaefff88547de";
    String cloudProviderInstanceId = k8sNodeInfoTasklet.getCloudProviderInstanceId(providerId, CloudProvider.AWS);
    assertThat(cloudProviderInstanceId).isEqualTo("i-072ecaefff88547de");
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void shouldReturnCloudProviderInstanceAzureVMSS() {
    String cloudProviderInstanceId = k8sNodeInfoTasklet.getCloudProviderInstanceId(PROVIDER_ID_AZURE_VMSS, AZURE);
    assertThat(cloudProviderInstanceId).isEqualTo(PROVIDER_ID_AZURE_VMSS.toLowerCase());
  }

  @Test
  @Owner(developers = NIKUNJ)
  @Category(UnitTests.class)
  public void shouldReturnCloudProviderInstanceAzureVM() {
    String cloudProviderInstanceId = k8sNodeInfoTasklet.getCloudProviderInstanceId(PROVIDER_ID_AZURE_VM, AZURE);
    assertThat(cloudProviderInstanceId).isEqualTo(PROVIDER_ID_AZURE_VM.toLowerCase());
  }

  private Quantity getQuantity(long amount, String unit) {
    return Quantity.newBuilder().setAmount(amount).setUnit(unit).build();
  }

  private PublishedMessage getK8sNodeInfoMessage(String nodeUid, String nodeName, String cloudProviderId,
      String accountId, String clusterName, String clusterId, Map<String, String> label, Map<String, Quantity> resource,
      Timestamp timestamp, String providerId) {
    NodeInfo nodeInfo = NodeInfo.newBuilder()
                            .setNodeUid(nodeUid)
                            .setNodeName(nodeName)
                            .setCloudProviderId(cloudProviderId)
                            .setClusterId(clusterId)
                            .setClusterName(clusterName)
                            .setCreationTime(timestamp)
                            .setProviderId(providerId)
                            .putAllLabels(label)
                            .putAllAllocatableResource(resource)
                            .build();
    return getPublishedMessage(accountId, nodeInfo);
  }

  private PublishedMessage getK8sNodeEventMessage(
      String nodeUid, String cloudProviderId, String accountId, EventType eventType, Timestamp timestamp) {
    NodeEvent nodeEvent = NodeEvent.newBuilder()
                              .setNodeUid(nodeUid)
                              .setCloudProviderId(cloudProviderId)
                              .setType(eventType)
                              .setTimestamp(timestamp)
                              .build();
    return getPublishedMessage(accountId, nodeEvent);
  }

  private PublishedMessage getPublishedMessage(String accountId, Message message) {
    Any payload = Any.pack(message);
    return PublishedMessage.builder()
        .type(message.getClass().getName())
        .data(payload.toByteArray())
        .accountId(accountId)
        .build();
  }
}
