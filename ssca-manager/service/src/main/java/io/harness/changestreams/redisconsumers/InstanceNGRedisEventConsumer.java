/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changestreams.redisconsumers;

import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_NG_SSCA_REDIS_EVENT_CONSUMER;

import io.harness.changestreams.eventhandlers.InstanceNGRedisEventHandler;
import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import javax.cache.Cache;
import javax.inject.Named;

public class InstanceNGRedisEventConsumer extends DebeziumAbstractRedisConsumer {
  @Inject
  public InstanceNGRedisEventConsumer(@Named(INSTANCE_NG_SSCA_REDIS_EVENT_CONSUMER) Consumer redisConsumer,
      QueueController queueController, InstanceNGRedisEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
  }
}
