/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.overviewLandingPage;

import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER;

import io.harness.debezium.redisconsumer.DebeziumAbstractRedisConsumer;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.cache.Cache;

public class PipelineExecutionSummaryRedisEventConsumerSnapshot extends DebeziumAbstractRedisConsumer {
  @Inject
  public PipelineExecutionSummaryRedisEventConsumerSnapshot(
      @Named(PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER) Consumer redisConsumer,
      QueueController queueController, PipelineExecutionSummaryChangeEventHandler eventHandler,
      @Named("debeziumEventsCache") Cache<String, Long> eventsCache) {
    super(redisConsumer, queueController, eventHandler, eventsCache);
  }
}
