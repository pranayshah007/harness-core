/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.auditevent.streaming.entities.BatchStatus.IN_PROGRESS;
import static io.harness.auditevent.streaming.entities.BatchStatus.READY;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.entities.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.entities.StreamingBatch.StreamingBatchKeys;
import io.harness.auditevent.streaming.repositories.StreamingBatchRepository;
import io.harness.auditevent.streaming.services.StreamingBatchService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class StreamingBatchServiceImpl implements StreamingBatchService {
  private StreamingBatchRepository streamingBatchRepository;

  @Autowired
  public StreamingBatchServiceImpl(StreamingBatchRepository streamingBatchRepository) {
    this.streamingBatchRepository = streamingBatchRepository;
  }

  @Override
  public Optional<StreamingBatch> get(
      String accountIdentifier, String streamingDestinationIdentifier, List<BatchStatus> batchStatusList) {
    return streamingBatchRepository.findStreamingBatchByAccountIdentifierAndStreamingDestinationIdentifierAndStatusIn(
        accountIdentifier, streamingDestinationIdentifier, batchStatusList);
  }

  @Override
  public Optional<StreamingBatch> getLatest(String accountIdentifier, String streamingDestinationIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(StreamingBatchKeys.streamingDestinationIdentifier)
                            .is(streamingDestinationIdentifier);
    Sort sort = Sort.by(Sort.Direction.DESC, StreamingBatchKeys.endTime);
    return Optional.ofNullable(streamingBatchRepository.findOne(criteria, sort));
  }

  @Override
  public StreamingBatch update(String accountIdentifier, StreamingBatch streamingBatch) {
    if (!accountIdentifier.equals(streamingBatch.getAccountIdentifier())) {
      throw new InvalidRequestException(
          String.format("Account identifier mismatch. Passed: [%s] but expected [%s] for batch id: [%s]",
              accountIdentifier, streamingBatch.getAccountIdentifier(), streamingBatch.getId()));
    }
    return streamingBatchRepository.save(streamingBatch);
  }

  @Override
  public StreamingBatch getLastStreamingBatch(StreamingDestination streamingDestination, Long timestamp) {
    Optional<StreamingBatch> lastStreamingBatch =
        getLatest(streamingDestination.getAccountIdentifier(), streamingDestination.getIdentifier());
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
    // TODO: Add total number of records
    return update(streamingDestination.getAccountIdentifier(), streamingBatch);
  }

  private StreamingBatch createNextBatch(
      StreamingBatch previousStreamingBatch, StreamingDestination streamingDestination, Long timestamp) {
    StreamingBatch streamingBatch =
        newBatchBuilder(streamingDestination, timestamp).startTime(previousStreamingBatch.getEndTime()).build();
    // TODO: Add total number of records
    return update(streamingDestination.getAccountIdentifier(), streamingBatch);
  }

  private StreamingBatch.StreamingBatchBuilder newBatchBuilder(
      StreamingDestination streamingDestination, Long timestamp) {
    return StreamingBatch.builder()
        .accountIdentifier(streamingDestination.getAccountIdentifier())
        .streamingDestinationIdentifier(streamingDestination.getIdentifier())
        .status(READY)
        .endTime(timestamp);
  }
}
