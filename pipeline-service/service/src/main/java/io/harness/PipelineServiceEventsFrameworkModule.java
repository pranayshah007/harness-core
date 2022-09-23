/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_REQUEST_PAYLOAD_DETAILS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.impl.redis.RedisUtils;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@AllArgsConstructor
@OwnedBy(PIPELINE)
public class PipelineServiceEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final PipelineRedisEventsConfig pipelineRedisEventsConfig;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_REQUEST_PAYLOAD_DETAILS))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));

      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedisUtils.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(GitAwareRedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redissonClient,
              pipelineRedisEventsConfig.getSetupUsage().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_PRODUCER))
          .toInstance(GitAwareRedisProducer.of(EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC, redissonClient,
              pipelineRedisEventsConfig.getPlanNotifyEvent().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_REQUEST_PAYLOAD_DETAILS))
          .toInstance(RedisProducer.of(WEBHOOK_REQUEST_PAYLOAD_DETAILS, redissonClient,
              pipelineRedisEventsConfig.getWebhookPayloadDetails().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
              pipelineRedisEventsConfig.getEntityCrud().getMaxTopicSize(), PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(GitAwareRedisProducer.of(EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT, redissonClient,
              pipelineRedisEventsConfig.getOrchestrationNotifyEvent().getMaxTopicSize(),
              PIPELINE_SERVICE.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, PIPELINE_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.POLLING_EVENTS_STREAM, PIPELINE_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.POLLING_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.POLLING_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM, PIPELINE_SERVICE.getServiceId(),
              redissonClient, EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PLAN_NOTIFY_EVENT_TOPIC))
          .toInstance(RedisConsumer.of(PLAN_NOTIFY_EVENT_TOPIC, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PMS_ORCHESTRATION_NOTIFY_EVENT))
          .toInstance(RedisConsumer.of(PMS_ORCHESTRATION_NOTIFY_EVENT, PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT_BATCH_SIZE, redisConfig.getEnvNamespace()));
    }
  }
}
