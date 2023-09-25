/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;

import java.time.Duration;
import java.util.List;
import org.redisson.api.RSetCache;

@OwnedBy(PL)
public interface Consumer {
  List<Message> read(Duration maxWaitTime);
  void acknowledge(String... messageId);
  void shutdown();

  int getBatchSize();

  RSetCache<String> getMessageCache();
}
