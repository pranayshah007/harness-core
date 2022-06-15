/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.tasklet.reader.PublishedMessageReader;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.PVEvent;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class K8sPVEventTasklet implements Tasklet {
  @Autowired private BatchMainConfig config;
  @Autowired protected InstanceDataDao instanceDataDao;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private InstanceDataBulkWriteService instanceDataBulkWriteService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
    final JobConstants jobConstants = new CCMJobConstants(chunkContext);
    int batchSize = config.getBatchQueryConfig().getQueryBatchSize();

    String messageType = EventTypeConstants.K8S_PV_EVENT;
    List<PublishedMessage> publishedMessageList;
    PublishedMessageReader publishedMessageReader =
        new PublishedMessageReader(publishedMessageDao, jobConstants.getAccountId(), messageType,
            jobConstants.getJobStartTime(), jobConstants.getJobEndTime(), batchSize);
    do {
      publishedMessageList = publishedMessageReader.getNext();
      // change logger to debug in future
      log.info("Processing publishedMessage of size: {}", publishedMessageList.size());
      List<InstanceEvent> instanceEventList = publishedMessageList.stream()
                                                  .map(this::processPVEventMessage)
                                                  .filter(instanceEvent -> null != instanceEvent.getAccountId())
                                                  .collect(Collectors.toList());

      instanceDataBulkWriteService.updateInstanceEvent(instanceEventList);

    } while (publishedMessageList.size() == batchSize);
    return null;
  }

  public InstanceEvent processPVEventMessage(PublishedMessage publishedMessage) {
    try {
      return process(publishedMessage);
    } catch (Exception ex) {
      log.error("K8sPVEventTasklet Exception ", ex);
    }
    return InstanceEvent.builder().build();
  }

  private InstanceEvent process(PublishedMessage publishedMessage) {
    PVEvent pvEvent = (PVEvent) publishedMessage.getMessage();
    InstanceEvent.EventType type = null;
    switch (pvEvent.getEventType()) {
      case EVENT_TYPE_START:
        type = InstanceEvent.EventType.START;
        break;
      case EVENT_TYPE_STOP:
        type = InstanceEvent.EventType.STOP;
        break;
      case EVENT_TYPE_EXPANSION:
        // not implemented yet
        return InstanceEvent.builder().build();
      default:
        break;
    }

    return InstanceEvent.builder()
        .accountId(publishedMessage.getAccountId())
        .cloudProviderId(pvEvent.getCloudProviderId())
        .clusterId(pvEvent.getClusterId())
        .instanceId(pvEvent.getPvUid())
        .instanceName(pvEvent.getPvName())
        .type(type)
        .instanceType(InstanceType.K8S_PV)
        .timestamp(HTimestamps.toInstant(pvEvent.getTimestamp()))
        .build();
  }
}
