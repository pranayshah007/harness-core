/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_CONSUMER;
import static io.harness.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_PRODUCER;
import static io.harness.OrchestrationEventsFrameworkConstants.PARTIAL_PLAN_EVENT_CONSUMER;
import static io.harness.OrchestrationEventsFrameworkConstants.SDK_RESPONSE_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.INITIATE_NODE_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.INITIATE_NODE_EVENT_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.INITIATE_NODE_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PARTIAL_PLAN_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PARTIAL_PLAN_RESPONSE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_SDK_RESPONSE_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.SDK_RESPONSE_EVENT_BATCH_SIZE;
import static io.harness.pms.events.PmsEventFrameworkConstants.MAX_PROCESSING_TIME_SECONDS;

import io.harness.events.PmsRedissonClientFactory;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.time.Duration;
import org.redisson.api.RedissonClient;

public class OrchestrationEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;

  public OrchestrationEventsFrameworkModule(EventsFrameworkConfiguration eventsFrameworkConfiguration) {
    this.eventsFrameworkConfiguration = eventsFrameworkConfiguration;
  }

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(PARTIAL_PLAN_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(ORCHESTRATION_LOG))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
    } else {
      RedissonClient redissonClient = PmsRedissonClientFactory.getRedisClient(redisConfig);

      bind(Consumer.class)
          .annotatedWith(Names.named(SDK_RESPONSE_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_SDK_RESPONSE_EVENT_TOPIC, PIPELINE_SERVICE.getServiceId(),
              redissonClient, Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), SDK_RESPONSE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PARTIAL_PLAN_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_PARTIAL_PLAN_RESPONSE, PIPELINE_SERVICE.getServiceId(), redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PARTIAL_PLAN_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      // Trigger Node Consumer and producer
      bind(Producer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_PRODUCER))
          .toInstance(RedisProducer.of(INITIATE_NODE_EVENT_TOPIC, redissonClient, INITIATE_NODE_EVENT_MAX_TOPIC_SIZE,
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(INITIATE_NODE_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(INITIATE_NODE_EVENT_TOPIC, PIPELINE_SERVICE.getServiceId(), redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), INITIATE_NODE_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(ORCHESTRATION_LOG))
          .toInstance(RedisProducer.of(ORCHESTRATION_LOG, redissonClient, ORCHESTRATION_LOG_MAX_TOPIC_SIZE,
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
    }
  }
}
