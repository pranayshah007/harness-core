/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.analyserservice;

import static io.harness.AuthorizationServiceHeader.ANALYZER_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.QueryAnalysisMessageListener;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@OwnedBy(PIPELINE)
public class EventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC, ANALYZER_SERVICE.getServiceId(),
              redisConfig, EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE));
    }
    bind(MessageListener.class)
        .annotatedWith(Names.named(EventsFrameworkConstants.QUERY_ANALYSIS_TOPIC))
        .to(QueryAnalysisMessageListener.class);
  }
}
