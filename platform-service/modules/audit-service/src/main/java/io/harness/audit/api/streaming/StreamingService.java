/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StreamingService {
  StreamingDestination create(String accountIdentifier, StreamingDestinationDTO streamingDestinationDTO);
  Page<StreamingDestination> list(
      String accountIdentifier, Pageable pageable, StreamingDestinationFilterProperties filterProperties);
}
