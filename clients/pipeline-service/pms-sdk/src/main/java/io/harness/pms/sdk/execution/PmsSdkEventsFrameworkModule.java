/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.execution;

import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_FACILITATOR_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_INTERRUPT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_ADVISE_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_RESUME_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_NODE_START_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_PROGRESS_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC;
import static io.harness.pms.events.PmsEventFrameworkConstants.MAX_PROCESSING_TIME_SECONDS;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_FACILITATOR_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_INTERRUPT_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_NODE_ADVISE_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_NODE_RESUME_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_NODE_START_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_ORCHESTRATION_EVENT_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_PROGRESS_CONSUMER;
import static io.harness.pms.sdk.execution.events.PmsSdkEventFrameworkConstants.PT_START_PLAN_CREATION_EVENT_CONSUMER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.time.Duration;
import org.redisson.api.RedissonClient;
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkEventsFrameworkModule extends AbstractModule {
  private static PmsSdkEventsFrameworkModule instance;

  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final String serviceName;

  public static PmsSdkEventsFrameworkModule getInstance(EventsFrameworkConfiguration config, String serviceName) {
    if (instance == null) {
      instance = new PmsSdkEventsFrameworkModule(config, serviceName);
    }
    return instance;
  }

  private PmsSdkEventsFrameworkModule(EventsFrameworkConfiguration config, String serviceName) {
    this.eventsFrameworkConfiguration = config;
    this.serviceName = serviceName;
  }

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_INTERRUPT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_ORCHESTRATION_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      // facilitator
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_FACILITATOR_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_START_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_PROGRESS_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_ADVISE_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_RESUME_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_START_PLAN_CREATION_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

    } else {
      RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_INTERRUPT_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_INTERRUPT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_INTERRUPT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_ORCHESTRATION_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_ORCHESTRATION_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_ORCHESTRATION_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      // facilitator
      bind(Consumer.class)
          .annotatedWith(Names.named(PT_FACILITATOR_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_FACILITATOR_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_FACILITATOR_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_START_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_NODE_START_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_NODE_START_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_PROGRESS_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_PROGRESS_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_PROGRESS_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_ADVISE_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_NODE_ADVISE_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_NODE_ADVISE_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_NODE_RESUME_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_NODE_RESUME_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_NODE_RESUME_BATCH_SIZE,
              redisConfig.getEnvNamespace()));

      bind(Consumer.class)
          .annotatedWith(Names.named(PT_START_PLAN_CREATION_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC, serviceName, redissonClient,
              Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), START_PARTIAL_PLAN_CREATOR_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
    }
  }
}
