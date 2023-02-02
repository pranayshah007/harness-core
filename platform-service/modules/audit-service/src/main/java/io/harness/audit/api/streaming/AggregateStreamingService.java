/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming;

import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.spec.server.audit.v1.model.StreamingDestinationAggregateDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationCards;

import java.util.List;
import org.springframework.data.domain.Pageable;

public interface AggregateStreamingService {
  StreamingDestinationCards getStreamingDestinationCards(String accountIdentifier);

  List<StreamingDestinationAggregateDTO> getAggregatedList(
      String accountIdentifier, Pageable pageable, StreamingDestinationFilterProperties filterProperties);
}
