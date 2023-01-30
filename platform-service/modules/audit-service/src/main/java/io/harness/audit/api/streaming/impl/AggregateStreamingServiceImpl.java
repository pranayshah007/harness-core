/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys.createdAt;
import static io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys.status;

import io.harness.audit.api.streaming.AggregateStreamingService;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.repositories.streaming.StreamingBatchRepository;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys;
import io.harness.spec.server.audit.v1.model.FailureInfoCard;
import io.harness.spec.server.audit.v1.model.LastStreamedCard;
import io.harness.spec.server.audit.v1.model.StatusWiseCount;
import io.harness.spec.server.audit.v1.model.StreamingDestinationCards;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class AggregateStreamingServiceImpl implements AggregateStreamingService {
  private final StreamingDestinationRepository streamingDestinationRepository;
  private final StreamingBatchRepository streamingBatchRepository;

  @Inject
  public AggregateStreamingServiceImpl(StreamingDestinationRepository streamingDestinationRepository,
      StreamingBatchRepository streamingBatchRepository) {
    this.streamingDestinationRepository = streamingDestinationRepository;
    this.streamingBatchRepository = streamingBatchRepository;
  }

  @Override
  public StreamingDestinationCards getStreamingDestinationCards(String accountIdentifier) {
    StreamingDestinationCards streamingDestinationCards = new StreamingDestinationCards();

    List<StatusWiseCount> statusWiseCounts = getStatusWiseCounts(accountIdentifier);
    streamingDestinationCards.countByStatusCard(statusWiseCounts);

    Optional<StreamingBatchDTO> latestSuccessfulBatch = getLatestSuccessfulBatch(accountIdentifier);
    if (latestSuccessfulBatch.isPresent()) {
      LastStreamedCard lastStreamedCard = new LastStreamedCard();
      lastStreamedCard.lastStreamedAt(latestSuccessfulBatch.get().getLastStreamedAt());
      streamingDestinationCards.lastStreamedCard(lastStreamedCard);
    }

    long count = getFailedBatchCount(accountIdentifier);
    FailureInfoCard failureInfoCard = new FailureInfoCard();
    failureInfoCard.count(count);
    streamingDestinationCards.failureInfoCard(failureInfoCard);
    return streamingDestinationCards;
  }

  private List<StatusWiseCount> getStatusWiseCounts(String accountIdentifier) {
    return streamingDestinationRepository.countByStatus(
        Criteria.where(StreamingDestinationKeys.accountIdentifier).is(accountIdentifier));
  }

  private Optional<StreamingBatchDTO> getLatestSuccessfulBatch(String accountIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchDTOKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(status)
                            .is(BatchStatus.SUCCESS);
    Sort sort = Sort.by(Sort.Direction.DESC, createdAt);
    return Optional.ofNullable(streamingBatchRepository.findOne(criteria, sort));
  }

  private long getFailedBatchCount(String accountIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchDTOKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(status)
                            .is(BatchStatus.FAILED);
    return streamingBatchRepository.count(criteria);
  }
}
