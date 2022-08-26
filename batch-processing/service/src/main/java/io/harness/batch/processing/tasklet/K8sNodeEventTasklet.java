/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceEvent.EventType;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.service.intfc.InstanceInfoTimescaleDAO;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.NodeEvent;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class K8sNodeEventTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired protected InstanceDataService instanceDataService;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;
  @Autowired private InstanceInfoTimescaleDAO instanceInfoTimescaleDAO;
  @Autowired private FeatureFlagService featureFlagService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);

    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_NODE_EVENT;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    List<PublishedMessage> publishedMessageList;
    do {
      publishedMessageList = publishedMessageReader.getNext();
      List<InstanceEvent> instanceEventList = publishedMessageList.stream()
                                                  .map(this::processNodeEventMessage)
                                                  .filter(instanceInfo -> null != instanceInfo.getAccountId())
                                                  .collect(Collectors.toList());

      instanceDataBulkWriteService.updateInstanceEvent(instanceEventList);
      if (featureFlagService.isEnabled(FeatureName.NODE_RECOMMENDATION_AGGREGATE, jobConstants.getAccountId())) {
        // we are not using START event now-a-days.
        instanceInfoTimescaleDAO.updateNodeStopEvent(
            instanceEventList.stream().filter(e -> EventType.STOP.equals(e.getType())).collect(Collectors.toList()));
      }
    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  public InstanceEvent processNodeEventMessage(PublishedMessage publishedMessage) {
    try {
      return process(publishedMessage);
    } catch (Exception ex) {
      log.error("K8sNodeEventTasklet Exception ", ex);
    }
    return InstanceEvent.builder().build();
  }

  public InstanceEvent process(PublishedMessage publishedMessage) {
    NodeEvent nodeEvent = (NodeEvent) publishedMessage.getMessage();
    EventType type;
    switch (nodeEvent.getType()) {
      case EVENT_TYPE_START:
        type = EventType.START;
        break;
      case EVENT_TYPE_STOP:
        type = EventType.STOP;
        break;
      default:
        type = EventType.UNKNOWN;
        break;
    }

    return InstanceEvent.builder()
        .accountId(publishedMessage.getAccountId())
        .cloudProviderId(nodeEvent.getCloudProviderId())
        .clusterId(nodeEvent.getClusterId())
        .instanceId(nodeEvent.getNodeUid())
        .instanceName(nodeEvent.getNodeName())
        .type(type)
        .instanceType(InstanceType.K8S_NODE)
        .timestamp(HTimestamps.toInstant(nodeEvent.getTimestamp()))
        .build();
  }
}
