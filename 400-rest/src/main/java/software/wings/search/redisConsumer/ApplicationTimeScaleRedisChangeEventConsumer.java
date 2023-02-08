/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.search.redisConsumer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.APPLICATION_TIMESCALE_REDIS_CHANGE_EVENT_CONSUMER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class ApplicationTimeScaleRedisChangeEventConsumer extends DebeziumAbstractRedisConsumerCg {
  @Inject
  public ApplicationTimeScaleRedisChangeEventConsumer(
      @Named(APPLICATION_TIMESCALE_REDIS_CHANGE_EVENT_CONSUMER) Consumer redisConsumer, QueueController queueController,
      ApplicationTimeScalaRedisChangeEventHandler applicationTimeScalaRedisChangeEventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache) {
    super(redisConsumer, queueController, applicationTimeScalaRedisChangeEventHandler, eventsCache);
  }
}
