/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import io.harness.eventsframework.consumer.Message;
import io.harness.logging.AutoLogContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RedisTraceConsumer implements Runnable {
  protected boolean handleMessage(Message message) {
    try (AutoLogContext autoLogContext = new AutoLogContext(
             message.getMessage().getMetadataMap(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      return processMessage(message);
    } catch (Exception ex) {
      // This is not evicted from events framework so that it can be processed
      // by other consumer if the error is a runtime error
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  protected abstract boolean processMessage(Message message);
}
