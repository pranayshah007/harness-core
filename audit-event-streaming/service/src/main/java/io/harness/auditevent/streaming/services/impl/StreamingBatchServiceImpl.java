/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import io.harness.auditevent.streaming.entities.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.entities.StreamingBatch.StreamingBatchKeys;
import io.harness.auditevent.streaming.repositories.StreamingBatchRepository;
import io.harness.auditevent.streaming.services.StreamingBatchService;
import io.harness.exception.InvalidRequestException;

import java.util.List;
import java.util.Optional;
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
    return Optional.of(streamingBatchRepository.findOne(criteria, sort));
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
}
