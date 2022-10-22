/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.service.intfc;

import io.harness.event.PublishMessage;

import java.util.List;

public interface EventPublisherService {
  void publish(String accountId, String delegateId, List<PublishMessage> messagesList, int messageCount)
      throws Exception;
}
