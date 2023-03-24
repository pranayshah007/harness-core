/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;

import java.util.List;

public interface StreamingDestinationService {
  List<StreamingDestination> list(String accountIdentifier, StreamingDestinationFilterProperties filterProperties);
  void disableStreamingDestination(StreamingDestination streamingDestination);
  List<String> distinctAccounts();
}
