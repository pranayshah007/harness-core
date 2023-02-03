/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;

import java.util.List;
import java.util.Optional;

public interface StreamingBatchService {
  Optional<StreamingBatch> get(
      String accountIdentifier, String streamingDestinationIdentifier, List<BatchStatus> batchStatusList);

  Optional<StreamingBatch> getLatest(String accountIdentifier, String streamingDestinationIdentifier);

  StreamingBatch update(String accountIdentifier, StreamingBatch streamingBatch);

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
  StreamingBatch getLastStreamingBatch(StreamingDestination streamingDestination, Long timestamp);
}
