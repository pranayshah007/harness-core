/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.repositories;

import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StreamingBatchRepository
    extends MongoRepository<StreamingBatch, String>, StreamingBatchRepositoryCustom {
  Optional<StreamingBatch> findStreamingBatchByAccountIdentifierAndStreamingDestinationIdentifierAndStatusIn(
      String accountIdentifier, String streamingDestinationIdentifier, List<BatchStatus> batchStatusList);
}
