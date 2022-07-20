/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.writer.EventWriter;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.payloads.Lifecycle.EventType;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.K8SClusterSyncEvent;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class K8SSyncEventTasklet extends EventWriter implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private FeatureFlagService featureFlagService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    if (config.getBatchQueryConfig().isSyncJobDisabled()) {
      return null;
    }
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_SYNC_EVENT;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    List<PublishedMessage> publishedMessageList;
    do {
      publishedMessageList = publishedMessageReader.getNext();
      List<Lifecycle> lifecycleList = processK8SSyncEventMessage(publishedMessageList);

      instanceDataBulkWriteService.updateLifecycle(lifecycleList);

      if (featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_AGGREGATE, jobConstants.getAccountId())) {
        updateInactiveInstancesInTimescale(jobConstants, publishedMessageList);
      }

    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  private void updateInactiveInstancesInTimescale(JobConstants jobConstants, List<PublishedMessage> publishedMessages) {
    for (PublishedMessage publishedMessage : publishedMessages) {
      try {
        K8SClusterSyncEvent k8SClusterSyncEvent = (K8SClusterSyncEvent) publishedMessage.getMessage();

        Instant syncEventTime = HTimestamps.toInstant(k8SClusterSyncEvent.getLastProcessedTimestamp());

        if (k8SClusterSyncEvent.getVersion() == 2) {
          instanceInfoTimescaleDAO.stopInactiveNodesAtTime(jobConstants, k8SClusterSyncEvent.getClusterId(),
              syncEventTime, new ArrayList<>(k8SClusterSyncEvent.getActiveNodeUidsMapMap().keySet()));
          instanceInfoTimescaleDAO.stopInactivePodsAtTime(jobConstants.getAccountId(),
              k8SClusterSyncEvent.getClusterId(), syncEventTime,
              new ArrayList<>(k8SClusterSyncEvent.getActivePodUidsMapMap().keySet()));
        } else {
          instanceInfoTimescaleDAO.stopInactiveNodesAtTime(jobConstants, k8SClusterSyncEvent.getClusterId(),
              syncEventTime, k8SClusterSyncEvent.getActiveNodeUidsList());
          instanceInfoTimescaleDAO.stopInactivePodsAtTime(jobConstants.getAccountId(),
              k8SClusterSyncEvent.getClusterId(), syncEventTime, k8SClusterSyncEvent.getActivePodUidsList());
        }
      } catch (Exception ex) {
        log.error("Exception while updating timescale ", ex);
      }
    }
  }

  private List<Lifecycle> processK8SSyncEventMessage(List<PublishedMessage> publishedMessages) {
    try {
      return process(publishedMessages);
    } catch (Exception ex) {
      log.error("K8SSyncEventTasklet Exception ", ex);
    }
    return new ArrayList<>();
  }

  public List<Lifecycle> process(List<PublishedMessage> publishedMessages) {
    log.info("Published batch size is K8SSyncEventTasklet {} ", publishedMessages.size());
    List<Lifecycle> lifecycleList = new ArrayList<>();
    for (PublishedMessage publishedMessage : publishedMessages) {
      try {
        K8SClusterSyncEvent k8SClusterSyncEvent = (K8SClusterSyncEvent) publishedMessage.getMessage();
        log.info("K8S sync event {} ", k8SClusterSyncEvent);

        String accountId = publishedMessage.getAccountId();
        String clusterId = k8SClusterSyncEvent.getClusterId();
        Timestamp lastProcessedTimestamp = k8SClusterSyncEvent.getLastProcessedTimestamp();
        Set<String> activeInstanceIds =
            fetchActiveInstanceAtTime(accountId, clusterId, HTimestamps.toInstant(lastProcessedTimestamp));

        log.info("Active K8S instances before {} time {}", lastProcessedTimestamp, activeInstanceIds.size());

        Set<String> activeInstanceArns = new HashSet<>();

        if (k8SClusterSyncEvent.getVersion() == 2) {
          Set<String> nodeUids = k8SClusterSyncEvent.getActiveNodeUidsMapMap().keySet();
          log.info("Active nodeUids are {}", nodeUids.toString());
          activeInstanceArns.addAll(nodeUids);
          activeInstanceArns.addAll(k8SClusterSyncEvent.getActivePodUidsMapMap().keySet());
          activeInstanceArns.addAll(k8SClusterSyncEvent.getActivePvUidsMapMap().keySet());
        } else {
          activeInstanceArns.addAll(k8SClusterSyncEvent.getActiveNodeUidsList());
          activeInstanceArns.addAll(k8SClusterSyncEvent.getActivePodUidsList());
          activeInstanceArns.addAll(k8SClusterSyncEvent.getActivePvUidsList());
        }

        final SetView<String> inactiveInstanceArns = Sets.difference(activeInstanceIds, activeInstanceArns);

        log.info("Inactive K8S instance arns {}", inactiveInstanceArns.toString());

        lifecycleList.addAll(
            inactiveInstanceArns.stream()
                .map(instanceId
                    -> createLifecycle(instanceId, clusterId, lastProcessedTimestamp, EventType.EVENT_TYPE_STOP))
                .collect(Collectors.toList()));

        // instances which are actually active but in instanceData collection it is either stopped or doesn't exists.
        final SetView<String> missedActiveInstanceArns = Sets.difference(activeInstanceArns, activeInstanceIds);
        log.info("Missed K8S instance arns {}", missedActiveInstanceArns.toString());

        lifecycleList.addAll(
            missedActiveInstanceArns.stream()
                .map(instanceId
                    -> createLifecycle(instanceId, clusterId, lastProcessedTimestamp, EventType.EVENT_TYPE_START))
                .collect(Collectors.toList()));

      } catch (Exception ex) {
        log.error("Exception while processing message", ex);
      }
    }
    return lifecycleList;
  }

  private Lifecycle createLifecycle(
      String instanceId, String clusterId, Timestamp lastProcessedTimestamp, final EventType eventType) {
    return Lifecycle.newBuilder()
        .setInstanceId(instanceId)
        .setClusterId(clusterId)
        .setType(eventType)
        .setTimestamp(lastProcessedTimestamp)
        .build();
  }
}
