/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.audit.entities.AuditEvent.AuditEventKeys.ACCOUNT_IDENTIFIER_KEY;
import static io.harness.audit.entities.AuditEvent.AuditEventKeys.createdAt;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.JOB_START_TIME_PARAMETER_KEY;
import static io.harness.auditevent.streaming.entities.BatchStatus.SUCCESS;

import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.AuditEventRepository;
import io.harness.auditevent.streaming.BatchConfig;
import io.harness.auditevent.streaming.entities.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.publishers.StreamingPublisher;
import io.harness.auditevent.streaming.publishers.StreamingPublisherUtils;
import io.harness.auditevent.streaming.services.AuditEventStreamingService;
import io.harness.auditevent.streaming.services.BatchProcessorService;
import io.harness.auditevent.streaming.services.StreamingBatchService;

import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditEventStreamingServiceImpl implements AuditEventStreamingService {
  private final BatchProcessorService batchProcessorService;
  private final StreamingBatchService streamingBatchService;
  private final AuditEventRepository auditEventRepository;
  private final BatchConfig batchConfig;
  private final MongoTemplate template;
  private final Map<String, StreamingPublisher> streamingPublisherMap;

  @Autowired
  public AuditEventStreamingServiceImpl(BatchProcessorService batchProcessorService,
      StreamingBatchService streamingBatchService, AuditEventRepository auditEventRepository, BatchConfig batchConfig,
      MongoTemplate template, Map<String, StreamingPublisher> streamingPublisherMap) {
    this.batchProcessorService = batchProcessorService;
    this.streamingBatchService = streamingBatchService;
    this.auditEventRepository = auditEventRepository;
    this.batchConfig = batchConfig;
    this.template = template;
    this.streamingPublisherMap = streamingPublisherMap;
  }

  @Override
  public StreamingBatch stream(StreamingDestination streamingDestination, JobParameters jobParameters) {
    StreamingBatch streamingBatch = streamingBatchService.getLastStreamingBatch(
        streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY));
    if (streamingBatch.getStatus().equals(BatchStatus.IN_PROGRESS)) {
      log.warn(getFullLogMessage("The batch is still in progress. Skipping.", streamingBatch));
      return streamingBatch;
    }
    MongoCursor<Document> auditEventMongoCursor = auditEventRepository.loadAuditEvents(
        getCriteriaToFetchAuditEvents(streamingBatch, streamingDestination), Sorts.ascending(createdAt));
    if (!auditEventMongoCursor.hasNext()) {
      log.info(getFullLogMessage("No more records found.", streamingBatch));
      streamingBatch.setStatus(SUCCESS);
      return streamingBatchService.update(streamingBatch.getAccountIdentifier(), streamingBatch);
    } else {
      return streamInternal(streamingBatch, streamingDestination, auditEventMongoCursor);
    }
  }

  private StreamingBatch streamInternal(StreamingBatch streamingBatch, StreamingDestination streamingDestination,
      MongoCursor<Document> auditEventMongoCursor) {
    boolean successResult = false;
    while (auditEventMongoCursor.hasNext()) {
      List<AuditEvent> auditEvents = getAuditEventsChunk(auditEventMongoCursor);
      List<OutgoingAuditMessage> outgoingAuditMessages = batchProcessorService.processAuditEvent(auditEvents);
      StreamingPublisher streamingPublisher =
          StreamingPublisherUtils.getStreamingPublisher(streamingDestination.getType(), streamingPublisherMap);
      successResult = streamingPublisher.publish(streamingDestination, outgoingAuditMessages);
      streamingBatch = updateBatchByResult(streamingBatch, auditEvents, successResult);
      if (!successResult) {
        break;
      }
      log.info(getFullLogMessage(String.format("Published [%s] messages.", auditEvents.size()), streamingBatch));
    }
    if (successResult) {
      streamingBatch.setStatus(SUCCESS);
      streamingBatch = streamingBatchService.update(streamingBatch.getAccountIdentifier(), streamingBatch);
    }
    return streamingBatch;
  }

  private List<AuditEvent> getAuditEventsChunk(MongoCursor<Document> auditEventMongoCursor) {
    List<AuditEvent> auditEvents = new ArrayList<>();
    int count = 0;
    while (auditEventMongoCursor.hasNext() && count < batchConfig.getLimit()) {
      auditEvents.add(template.getConverter().read(AuditEvent.class, auditEventMongoCursor.next()));
      count++;
    }
    return auditEvents;
  }

  private String getFullLogMessage(String message, StreamingBatch streamingBatch) {
    return String.format("%s [streamingBatchId=%s] [streamingDestination=%s] [accountIdentifier=%s]", message,
        streamingBatch.getId(), streamingBatch.getStreamingDestinationIdentifier(),
        streamingBatch.getAccountIdentifier());
  }

  private Criteria getCriteriaToFetchAuditEvents(
      StreamingBatch streamingBatch, StreamingDestination streamingDestination) {
    long startTime = (streamingBatch.getLastSuccessfulRecordTimestamp() != null)
        ? streamingBatch.getLastSuccessfulRecordTimestamp()
        : streamingBatch.getStartTime();
    return Criteria.where(ACCOUNT_IDENTIFIER_KEY)
        .is(streamingDestination.getAccountIdentifier())
        .and(createdAt)
        .gt(startTime)
        .lte(streamingBatch.getEndTime());
  }

  private StreamingBatch updateBatchByResult(
      StreamingBatch streamingBatch, List<AuditEvent> auditEvents, boolean result) {
    if (result) {
      Long lastSuccessfulRecordTimestamp = auditEvents.get(auditEvents.size() - 1).getCreatedAt();
      long numberOfRecordsPublished = auditEvents.size()
          + (streamingBatch.getNumberOfRecordsPublished() == null ? 0 : streamingBatch.getNumberOfRecordsPublished());
      streamingBatch.setLastSuccessfulRecordTimestamp(lastSuccessfulRecordTimestamp);
      streamingBatch.setNumberOfRecordsPublished(numberOfRecordsPublished);
    }
    return streamingBatchService.update(streamingBatch.getAccountIdentifier(), streamingBatch);
  }
}
