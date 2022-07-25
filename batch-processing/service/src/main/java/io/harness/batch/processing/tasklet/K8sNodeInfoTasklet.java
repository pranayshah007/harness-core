/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getInstanceCategory;
import static io.harness.ccm.commons.entities.k8s.K8sWorkload.encodeDotsInKey;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.CloudProviderService;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.batch.processing.service.intfc.InstanceResourceService;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.NodeInfo;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@OwnedBy(HarnessTeam.CE)
@Slf4j
public class K8sNodeInfoTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private CloudProviderService cloudProviderService;
  @Autowired private InstanceResourceService instanceResourceService;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private FeatureFlagService featureFlagService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_NODE_INFO;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    List<PublishedMessage> publishedMessageList;
    do {
      publishedMessageList = publishedMessageReader.getNext();
      List<InstanceInfo> instanceInfoList = publishedMessageList.stream()
                                                .map(this::processNodeInfoMessage)
                                                .filter(x -> x.getAccountId() != null)
                                                .collect(Collectors.toList());

      instanceDataBulkWriteService.upsertInstanceInfo(
          instanceInfoList.stream()
              .filter(x -> x.getMetaData().containsKey(InstanceMetaDataConstants.INSTANCE_CATEGORY))
              .collect(Collectors.toList()));

      if (featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_AGGREGATE, jobConstants.getAccountId())) {
        instanceInfoTimescaleDAO.insertIntoNodeInfo(instanceInfoList);
      }
    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  public InstanceInfo processNodeInfoMessage(PublishedMessage publishedMessage) {
    try {
      return process(publishedMessage);
    } catch (Exception ex) {
      log.error("K8sNodeInfoTasklet Exception ", ex);
    }
    return InstanceInfo.builder().metaData(Collections.emptyMap()).build();
  }

  public InstanceInfo process(PublishedMessage publishedMessage) {
    NodeInfo nodeInfo = (NodeInfo) publishedMessage.getMessage();
    String accountId = publishedMessage.getAccountId();
    String clusterId = nodeInfo.getClusterId();
    String nodeUid = nodeInfo.getNodeUid();

    Map<String, String> labelsMap = nodeInfo.getLabelsMap();
    Map<String, String> metaData = new HashMap<>();
    CloudProvider k8SCloudProvider =
        cloudProviderService.getK8SCloudProvider(nodeInfo.getCloudProviderId(), nodeInfo.getProviderId());
    String cloudProviderInstanceId = getCloudProviderInstanceId(nodeInfo.getProviderId(), k8SCloudProvider);
    if (CloudProvider.UNKNOWN == k8SCloudProvider) {
      log.warn("Node cloud provide is  not  present for nodeName {} and clusterId {}", nodeInfo.getNodeName(),
          nodeInfo.getClusterId());
      return InstanceInfo.builder().metaData(metaData).build();
    }
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, k8SCloudProvider.name());
    metaData.put(InstanceMetaDataConstants.REGION, labelsMap.get(K8sCCMConstants.REGION));
    metaData.put(InstanceMetaDataConstants.ZONE, labelsMap.get(K8sCCMConstants.ZONE));
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    metaData.put(InstanceMetaDataConstants.NODE_NAME, nodeInfo.getNodeName());
    metaData.put(InstanceMetaDataConstants.INSTANCE_FAMILY, labelsMap.get(K8sCCMConstants.INSTANCE_FAMILY));
    metaData.put(InstanceMetaDataConstants.OPERATING_SYSTEM, labelsMap.get(K8sCCMConstants.OPERATING_SYSTEM));
    metaData.put(InstanceMetaDataConstants.NODE_UID, nodeUid);
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, cloudProviderInstanceId);
    metaData.put(InstanceMetaDataConstants.INSTANCE_CATEGORY,
        getInstanceCategory(k8SCloudProvider, labelsMap, accountId).name());
    metaData.put(InstanceMetaDataConstants.POD_CAPACITY,
        String.valueOf(K8sResourceUtils.getPodCapacity(nodeInfo.getAllocatableResourceMap())));
    if (null != labelsMap.get(K8sCCMConstants.COMPUTE_TYPE)) {
      metaData.put(InstanceMetaDataConstants.COMPUTE_TYPE, labelsMap.get(K8sCCMConstants.COMPUTE_TYPE));
    }
    InstanceMetaDataUtils.populateNodePoolNameFromLabel(labelsMap, metaData);

    Resource allocatableResource = K8sResourceUtils.getResource(nodeInfo.getAllocatableResourceMap());
    Resource totalResource = allocatableResource;
    List<CloudProvider> cloudProviders = cloudProviderService.getFirstClassSupportedCloudProviders();
    String computeType =
        InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.COMPUTE_TYPE, metaData);
    if (cloudProviders.contains(k8SCloudProvider) && !K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType)) {
      Resource computeVMResource = instanceResourceService.getComputeVMResource(
          InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.INSTANCE_FAMILY, metaData),
          InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.REGION, metaData),
          k8SCloudProvider);
      if (null != computeVMResource) {
        totalResource = computeVMResource;
      }
    }

    return InstanceInfo.builder()
        .accountId(accountId)
        .settingId(nodeInfo.getCloudProviderId())
        .instanceId(nodeUid)
        .clusterId(clusterId)
        .cloudProviderInstanceId(cloudProviderInstanceId)
        .clusterName(nodeInfo.getClusterName())
        .instanceName(nodeInfo.getNodeName())
        .instanceType(InstanceType.K8S_NODE)
        .instanceState(InstanceState.RUNNING)
        .usageStartTime(HTimestamps.toInstant(nodeInfo.getCreationTime()))
        .resource(totalResource)
        .allocatableResource(allocatableResource)
        .labels(encodeDotsInKey(labelsMap))
        .metaData(metaData)
        .build();
  }

  @VisibleForTesting
  public String getCloudProviderInstanceId(String providerId, CloudProvider k8SCloudProvider) {
    if (null == providerId) {
      return "";
    }
    if (k8SCloudProvider == CloudProvider.AZURE) {
      // ProviderID:
      // azure:///subscriptions/20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0/resourceGroups/mc_ce_dev-resourcegroup_cetest1_eastus/providers/Microsoft.Compute/virtualMachines/aks-agentpool-41737416-1
      // ProviderID:
      // azure:///subscriptions/20d6a917-99fa-4b1b-9b2e-a3d624e9dcf0/resourceGroups/mc_ce_dev-resourcegroup_ce-dev-cluster2_eastus/providers/Microsoft.Compute/virtualMachineScaleSets/aks-agentpool-14257926-vmss/virtualMachines/1
      return providerId.toLowerCase();
    } else {
      return providerId.substring(providerId.lastIndexOf('/') + 1);
    }
  }
}
