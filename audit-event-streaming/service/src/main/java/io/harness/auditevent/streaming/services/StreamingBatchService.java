/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services;

import io.harness.auditevent.streaming.entities.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;

import java.util.List;
import java.util.Optional;

public interface StreamingBatchService {
  Optional<StreamingBatch> get(
      String accountIdentifier, String streamingDestinationIdentifier, List<BatchStatus> batchStatusList);
  Optional<StreamingBatch> getLatest(String accountIdentifier, String streamingDestinationIdentifier);
  StreamingBatch update(String accountIdentifier, StreamingBatch streamingBatch);
}
