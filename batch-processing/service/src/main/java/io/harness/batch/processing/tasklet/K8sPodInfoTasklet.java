/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;
import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.populateNodePoolNameFromLabel;
import static io.harness.ccm.commons.entities.k8s.K8sWorkload.encodeDotsInKey;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.billing.timeseries.data.PrunedInstanceData;
import io.harness.batch.processing.billing.writer.support.ClusterDataGenerationValidator;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.tasklet.support.HarnessServiceInfoFetcher;
import io.harness.batch.processing.tasklet.support.HarnessServiceInfoFetcherNG;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.beans.FeatureName;
import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.constants.CloudProvider;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.PodInfo;
import io.harness.perpetualtask.k8s.watch.Volume;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@OwnedBy(HarnessTeam.CE)
@Slf4j
public class K8sPodInfoTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private WorkloadRepository workloadRepository;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private HarnessServiceInfoFetcher harnessServiceInfoFetcher;
  @Autowired private HarnessServiceInfoFetcherNG harnessServiceInfoFetcherNG;
  @Autowired private ClusterDataGenerationValidator clusterDataGenerationValidator;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private io.harness.ccm.commons.service.intf.ClusterRecordService clusterRecordServiceNG;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String POD = "Pod";
  private static final String KUBE_SYSTEM_NAMESPACE = "kube-system";
  private static final String KUBE_PROXY_POD_PREFIX = "kube-proxy";

  private final LoadingCache<String, Boolean> clusterInfoCache = Caffeine.newBuilder()
                                                                     .recordStats()
                                                                     .expireAfterAccess(1, TimeUnit.DAYS)
                                                                     .maximumSize(1_000)
                                                                     .build(this::isCurrentGenCluster);

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_POD_INFO;
    List<PublishedMessage> publishedMessageList;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    do {
      publishedMessageList = publishedMessageReader.getNext();

      List<InstanceInfo> instanceInfoList = publishedMessageList.stream()
                                                .map(this::processPodInfoMessage)
                                                .filter(instanceInfo -> null != instanceInfo.getAccountId())
                                                .collect(Collectors.toList());

      instanceDataBulkWriteService.upsertInstanceInfo(
          instanceInfoList.stream()
              .filter(x
                  -> getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.INSTANCE_CATEGORY, x.getMetaData())
                      != null)
              .collect(Collectors.toList()));

      if (featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_AGGREGATE, jobConstants.getAccountId())) {
        instanceInfoTimescaleDAO.insertIntoPodInfo(instanceInfoList);
      }

    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  public InstanceInfo processPodInfoMessage(PublishedMessage publishedMessage) {
    try {
      return process(publishedMessage);
    } catch (Exception ex) {
      log.error("K8sPodInfoTasklet Exception ", ex);
    }
    return InstanceInfo.builder().metaData(Collections.emptyMap()).build();
  }

  public Boolean isCurrentGenCluster(String clusterId) {
    io.harness.ccm.cluster.entities.ClusterRecord clusterRecordCG;
    if (clusterId.isEmpty()) {
      return false;
    }
    clusterRecordCG = cloudToHarnessMappingService.getClusterRecord(clusterId);
    return clusterRecordCG != null;
  }

  public Boolean isNextGenCluster(String clusterId) {
    ClusterRecord clusterRecordNG;
    if (clusterId.isEmpty()) {
      return false;
    }
    clusterRecordNG = clusterRecordServiceNG.get(clusterId);
    return clusterRecordNG != null;
  }

  public InstanceInfo process(PublishedMessage publishedMessage) {
    String accountId = publishedMessage.getAccountId();
    PodInfo podInfo = (PodInfo) publishedMessage.getMessage();
    String podUid = podInfo.getPodUid();
    String clusterId = podInfo.getClusterId();
    HarnessServiceInfo harnessServiceInfo = null;
    HarnessServiceInfoNG harnessServiceInfoNG = null;
    if (!clusterDataGenerationValidator.shouldGenerateClusterData(accountId, clusterId)) {
      return InstanceInfo.builder().metaData(Collections.emptyMap()).build();
    }

    String workloadName = podInfo.getTopLevelOwner().getName();
    String workloadType = podInfo.getTopLevelOwner().getKind();
    String workloadId = podInfo.getTopLevelOwner().getUid();

    if (podInfo.getNamespace().equals(KUBE_SYSTEM_NAMESPACE)
        && podInfo.getPodName().startsWith(KUBE_PROXY_POD_PREFIX)) {
      workloadName = KUBE_PROXY_POD_PREFIX;
      workloadType = POD;
    }

    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, CloudProvider.GCP.name());
    metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_ID, podInfo.getNodeName());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    metaData.put(InstanceMetaDataConstants.NAMESPACE, podInfo.getNamespace());
    metaData.put(
        InstanceMetaDataConstants.WORKLOAD_NAME, workloadName.equals("") ? podInfo.getPodName() : workloadName);
    metaData.put(InstanceMetaDataConstants.WORKLOAD_TYPE, workloadType.equals("") ? POD : workloadType);

    if (!isEmpty(workloadId)) {
      metaData.put(InstanceMetaDataConstants.WORKLOAD_ID, workloadId);
    }

    PrunedInstanceData prunedInstanceData = instanceDataService.fetchPrunedInstanceDataWithName(
        accountId, clusterId, podInfo.getNodeName(), publishedMessage.getOccurredAt());
    InstanceType instanceType = InstanceType.K8S_POD;
    if (null != prunedInstanceData && prunedInstanceData.getInstanceId() != null) {
      Map<String, String> nodeMetaData = prunedInstanceData.getMetaData();
      metaData.put(InstanceMetaDataConstants.REGION, nodeMetaData.get(InstanceMetaDataConstants.REGION));
      metaData.put(InstanceMetaDataConstants.ZONE, nodeMetaData.get(InstanceMetaDataConstants.ZONE));
      metaData.put(
          InstanceMetaDataConstants.INSTANCE_FAMILY, nodeMetaData.get(InstanceMetaDataConstants.INSTANCE_FAMILY));
      metaData.put(
          InstanceMetaDataConstants.INSTANCE_CATEGORY, nodeMetaData.get(InstanceMetaDataConstants.INSTANCE_CATEGORY));
      metaData.put(
          InstanceMetaDataConstants.CLOUD_PROVIDER, nodeMetaData.get(InstanceMetaDataConstants.CLOUD_PROVIDER));
      metaData.put(
          InstanceMetaDataConstants.OPERATING_SYSTEM, nodeMetaData.get(InstanceMetaDataConstants.OPERATING_SYSTEM));
      metaData.put(InstanceMetaDataConstants.POD_NAME, podInfo.getPodName());
      metaData.put(InstanceMetaDataConstants.ACTUAL_PARENT_RESOURCE_ID, prunedInstanceData.getInstanceId());
      metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_CPU,
          String.valueOf(prunedInstanceData.getTotalResource().getCpuUnits()));
      metaData.put(InstanceMetaDataConstants.PARENT_RESOURCE_MEMORY,
          String.valueOf(prunedInstanceData.getTotalResource().getMemoryMb()));
      String computeType = nodeMetaData.get(InstanceMetaDataConstants.COMPUTE_TYPE);
      if (null != computeType) {
        metaData.put(InstanceMetaDataConstants.COMPUTE_TYPE, computeType);
        if (K8sCCMConstants.AWS_FARGATE_COMPUTE_TYPE.equals(computeType)) {
          instanceType = InstanceType.K8S_POD_FARGATE;
        }
      }
      if (null != prunedInstanceData.getCloudProviderInstanceId()) {
        metaData.put(
            InstanceMetaDataConstants.CLOUD_PROVIDER_INSTANCE_ID, prunedInstanceData.getCloudProviderInstanceId());
      }
      populateNodePoolNameFromLabel(nodeMetaData, metaData);
    } else {
      log.warn("Node detail not found clusterId {} node name {} podid {} podname {}", clusterId, podInfo.getNodeName(),
          podUid, podInfo.getPodName());
    }

    Map<String, String> labelsMap = podInfo.getLabelsMap();
    // Check in events db clusterrecords table vs harness db clusterrecords table and then decide which one to call.

    if (featureFlagService.isNotEnabled(FeatureName.CE_HARNESS_ENTITY_MAPPING, accountId)) {
      Boolean currentGenCluster = clusterInfoCache.get(clusterId);
      if (null != currentGenCluster && currentGenCluster) {
        harnessServiceInfo = harnessServiceInfoFetcher
                                 .fetchHarnessServiceInfo(accountId, podInfo.getCloudProviderId(),
                                     podInfo.getNamespace(), podInfo.getPodName(), labelsMap)
                                 .orElse(null);
      } else {
        // NG Cluster
        Optional<HarnessServiceInfoNG> harnessServiceInfoNG1;
        harnessServiceInfoNG1 = harnessServiceInfoFetcherNG.fetchHarnessServiceInfoNG(
            accountId, podInfo.getNamespace(), podInfo.getPodName(), labelsMap);
        if (harnessServiceInfoNG1.isPresent()) {
          harnessServiceInfoNG = harnessServiceInfoNG1.get();
        }
      }
    }

    try {
      workloadRepository.savePodWorkload(accountId, podInfo);
    } catch (Exception ex) {
      log.error("Error while saving pod workload {} {}", podInfo.getCloudProviderId(), podUid, ex);
    }

    final Resource resource = K8sResourceUtils.getResource(podInfo.getTotalResource().getRequestsMap());
    Resource resourceLimit = Resource.builder().cpuUnits(0.0).memoryMb(0.0).build();
    if (!isEmpty(podInfo.getTotalResource().getLimitsMap())) {
      resourceLimit = K8sResourceUtils.getResource(podInfo.getTotalResource().getLimitsMap());
    }

    final List<String> pvcClaimNames = podInfo.getVolumeList().stream().map(Volume::getId).collect(Collectors.toList());
    final Resource pricingResource = K8sResourceUtils.getResourceFromAnnotationMap(
        firstNonNull(podInfo.getMetadataAnnotationsMap(), Collections.emptyMap()));

    return InstanceInfo.builder()
        .accountId(accountId)
        .settingId(podInfo.getCloudProviderId())
        .instanceId(podUid)
        .clusterId(clusterId)
        .clusterName(podInfo.getClusterName())
        .instanceName(podInfo.getPodName())
        .instanceType(instanceType)
        .instanceState(InstanceState.RUNNING)
        .usageStartTime(HTimestamps.toInstant(podInfo.getCreationTimestamp()))
        .resource(resource)
        .resourceLimit(resourceLimit)
        .allocatableResource(resource)
        .pricingResource(pricingResource)
        .pvcClaimNames(pvcClaimNames)
        .metaData(metaData)
        .labels(encodeDotsInKey(labelsMap))
        .namespaceLabels(encodeDotsInKey(podInfo.getNamespaceLabelsMap()))
        .metadataAnnotations(podInfo.getMetadataAnnotationsMap())
        .harnessServiceInfo(harnessServiceInfo)
        .harnessServiceInfoNg(harnessServiceInfoNG)
        .build();
  }
}
