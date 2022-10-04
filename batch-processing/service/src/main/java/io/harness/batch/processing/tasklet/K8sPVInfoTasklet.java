/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.ccm.commons.entities.k8s.K8sWorkload.encodeDotsInKey;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.pricing.data.PVTypeCloudProviderMap;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.PVInfo;

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

@Slf4j
public class K8sPVInfoTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;

  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_PV_INFO;
    List<PublishedMessage> publishedMessageList;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    do {
      publishedMessageList = publishedMessageReader.getNext();

      List<InstanceInfo> instanceInfoList = publishedMessageList.stream()
                                                .map(this::processPVInfoMessage)
                                                .filter(instanceInfo -> instanceInfo.getAccountId() != null)
                                                .collect(Collectors.toList());

      instanceDataBulkWriteService.upsertInstanceInfo(instanceInfoList);
    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  public InstanceInfo processPVInfoMessage(PublishedMessage publishedMessage) {
    try {
      return process(publishedMessage);
    } catch (Exception ex) {
      log.error("K8sPVInfoTasklet Exception ", ex);
    }
    return InstanceInfo.builder().metaData(Collections.emptyMap()).build();
  }

  private InstanceInfo process(PublishedMessage publishedMessage) {
    String accountId = publishedMessage.getAccountId();
    PVInfo pvInfo = (PVInfo) publishedMessage.getMessage();
    String pvUid = pvInfo.getPvUid();
    String clusterId = pvInfo.getClusterId();

    Map<String, String> metaData = new HashMap<>();
    metaData.put(InstanceMetaDataConstants.PV_TYPE, pvInfo.getPvType().name());
    metaData.put(InstanceMetaDataConstants.CLOUD_PROVIDER, PVTypeCloudProviderMap.get(pvInfo.getPvType()).name());
    metaData.put(InstanceMetaDataConstants.CLUSTER_TYPE, ClusterType.K8S.name());
    metaData.put(InstanceMetaDataConstants.CLAIM_NAMESPACE, pvInfo.getClaimNamespace());
    metaData.put(InstanceMetaDataConstants.CLAIM_NAME, pvInfo.getClaimName());

    Map<String, String> labelsMap = pvInfo.getLabelsMap();
    if (labelsMap.get(K8sCCMConstants.REGION) != null) {
      metaData.put(InstanceMetaDataConstants.REGION, labelsMap.get(K8sCCMConstants.REGION));
    }

    Map<String, String> storageClassParams = pvInfo.getStorageClassParamsMap();
    metaData.putAll(encodeDotsInKey(storageClassParams));

    StorageResource resource = K8sResourceUtils.getCapacity(pvInfo.getCapacity());

    return InstanceInfo.builder()
        .accountId(accountId)
        .settingId(pvInfo.getCloudProviderId())
        .instanceId(pvUid)
        .clusterId(clusterId)
        .clusterName(pvInfo.getClusterName())
        .instanceName(pvInfo.getPvName())
        .instanceType(InstanceType.K8S_PV)
        .instanceState(InstanceState.RUNNING)
        .usageStartTime(HTimestamps.toInstant(pvInfo.getCreationTimestamp()))
        .storageResource(resource)
        .metaData(metaData)
        .labels(encodeDotsInKey(labelsMap))
        .build();
  }
}
