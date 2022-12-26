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
import static io.harness.auditevent.streaming.entities.BatchStatus.IN_PROGRESS;
import static io.harness.auditevent.streaming.entities.BatchStatus.READY;
import static io.harness.auditevent.streaming.entities.BatchStatus.SUCCESS;

import static org.springframework.data.domain.Sort.Direction.ASC;

import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.AuditEventRepository;
import io.harness.auditevent.streaming.entities.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.entities.StreamingBatch.StreamingBatchBuilder;
import io.harness.auditevent.streaming.entities.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.publishers.StreamingPublisher;
import io.harness.auditevent.streaming.publishers.StreamingPublisherUtils;
import io.harness.auditevent.streaming.services.AuditEventStreamingService;
import io.harness.auditevent.streaming.services.BatchProcessorService;
import io.harness.auditevent.streaming.services.StreamingBatchService;
import io.harness.exception.UnknownEnumTypeException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditEventStreamingServiceImpl implements AuditEventStreamingService {
  private final BatchProcessorService batchProcessorService;
  private final StreamingBatchService streamingBatchService;
  private final AuditEventRepository auditEventRepository;
  private final Map<String, StreamingPublisher> streamingPublisherMap;

  @Autowired
  public AuditEventStreamingServiceImpl(BatchProcessorService batchProcessorService,
      StreamingBatchService streamingBatchService, AuditEventRepository auditEventRepository,
      Map<String, StreamingPublisher> streamingPublisherMap) {
    this.batchProcessorService = batchProcessorService;
    this.streamingBatchService = streamingBatchService;
    this.auditEventRepository = auditEventRepository;
    this.streamingPublisherMap = streamingPublisherMap;
  }

  @Override
  public StreamingBatch stream(StreamingDestination streamingDestination, JobParameters jobParameters) {
    StreamingBatch streamingBatch =
        getLastStreamingBatch(streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY));
    if (streamingBatch.getStatus().equals(BatchStatus.IN_PROGRESS)) {
      log.warn(getFullLogMessage("The batch is still in progress. Skipping.", streamingBatch));
      return streamingBatch;
    }
    while (true) {
      List<AuditEvent> auditEvents = auditEventRepository.loadAuditEvents(
          getCriteriaToFetchAuditEvents(streamingBatch, streamingDestination), Sort.by(ASC, createdAt));
      if (auditEvents.isEmpty()) {
        log.info(getFullLogMessage("No more records found.", streamingBatch));
        streamingBatch.setStatus(SUCCESS);
        streamingBatchService.update(streamingBatch.getAccountIdentifier(), streamingBatch);
        break;
      } else {
        List<OutgoingAuditMessage> outgoingAuditMessages = batchProcessorService.processAuditEvent(auditEvents);
        StreamingPublisher streamingPublisher =
            StreamingPublisherUtils.getStreamingPublisher(streamingDestination.getType(), streamingPublisherMap);
        boolean successResult = streamingPublisher.publish(streamingDestination, outgoingAuditMessages);
        streamingBatch = updateBatch(streamingBatch, auditEvents, successResult);
        log.info(getFullLogMessage(String.format("Published [%s] messages.", auditEvents.size()), streamingBatch));
      }
    }
    return streamingBatch;
  }

  private String getFullLogMessage(String message, StreamingBatch streamingBatch) {
    return String.format("%s [streamingBatchId=%s] [streamingDestination=%s] [accountIdentifier=%s]", message,
        streamingBatch.getId(), streamingBatch.getStreamingDestinationIdentifier(),
        streamingBatch.getAccountIdentifier());
  }

  /**
   * Returns the last streaming batch for the given streamingDestination. If no streaming is batch found then it creates
   * a new batch and returns it. If last batch was a {@link BatchStatus#SUCCESS SUCCESS} then it creates next batch
   * based on previous batch and returns it. If last batch {@link BatchStatus#FAILED FAILED} then it increments the
   * retry count and return the batch.
   * @param streamingDestination {@link StreamingDestination StreamingDestination} for which batch is to be returned
   * @param timestamp Uses this timestamp as {@link StreamingBatch#setEndTime(Long) endTime} when creating a new {@link
   *     StreamingBatch StreamingBatch}
   * @return {@link StreamingBatch StreamingBatch}
   */
  private StreamingBatch getLastStreamingBatch(StreamingDestination streamingDestination, Long timestamp) {
    Optional<StreamingBatch> lastStreamingBatch = streamingBatchService.getLatest(
        streamingDestination.getAccountIdentifier(), streamingDestination.getIdentifier());
    if (lastStreamingBatch.map(batch -> batch.getStatus().equals(IN_PROGRESS)).orElse(true)) {
      return createInitialBatch(streamingDestination, timestamp);
    }
    @NotNull BatchStatus status = lastStreamingBatch.get().getStatus();
    StreamingBatch streamingBatch;
    switch (status) {
      case READY:
      case IN_PROGRESS:
        streamingBatch = lastStreamingBatch.get();
        break;
      case SUCCESS:
        streamingBatch = createNextBatch(lastStreamingBatch.get(), streamingDestination, timestamp);
        break;
      default:
        throw new UnknownEnumTypeException("batch status", status.name());
    }
    return streamingBatch;
  }

  private StreamingBatch createInitialBatch(StreamingDestination streamingDestination, Long timestamp) {
    StreamingBatch streamingBatch = newBatchBuilder(streamingDestination, timestamp)
                                        .startTime(streamingDestination.getLastStatusChangedAt())
                                        .build();
    return streamingBatchService.update(streamingDestination.getAccountIdentifier(), streamingBatch);
  }

  private StreamingBatch createNextBatch(
      StreamingBatch previousStreamingBatch, StreamingDestination streamingDestination, Long timestamp) {
    StreamingBatch streamingBatch =
        newBatchBuilder(streamingDestination, timestamp).startTime(previousStreamingBatch.getEndTime()).build();
    return streamingBatchService.update(streamingDestination.getAccountIdentifier(), streamingBatch);
  }

  private StreamingBatchBuilder newBatchBuilder(StreamingDestination streamingDestination, Long timestamp) {
    return StreamingBatch.builder()
        .accountIdentifier(streamingDestination.getAccountIdentifier())
        .streamingDestinationIdentifier(streamingDestination.getIdentifier())
        .status(READY)
        .endTime(timestamp);
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

  private StreamingBatch updateBatch(StreamingBatch streamingBatch, List<AuditEvent> auditEvents, boolean result) {
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
