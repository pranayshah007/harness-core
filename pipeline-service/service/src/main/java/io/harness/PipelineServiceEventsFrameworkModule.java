/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_GROUP_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.DUMMY_TOPIC_NAME;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PLAN_NOTIFY_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.PMS_ORCHESTRATION_NOTIFY_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_REQUEST_PAYLOAD_DETAILS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.eventsframework.impl.redis.GitAwareRedisProducer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.pms.event.overviewLandingPage.DebeziumConsumersConfig;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import lombok.AllArgsConstructor;
import org.redisson.api.RedissonClient;

@AllArgsConstructor
@OwnedBy(PIPELINE)
public class PipelineServiceEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final PipelineRedisEventsConfig pipelineRedisEventsConfig;
  private final DebeziumConsumersConfig debeziumConsumersConfigs;
  private final EventsFrameworkConfiguration eventsFrameworkSnapshotConfiguration;
  private final boolean shouldUseEventsFrameworkSnapshotDebezium;

  @Provides
  @Singleton
  @Named("debeziumEventsCache")
  public Cache<String, Long> debeziumEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("debeziumEventsCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(Duration.ONE_HOUR), versionInfoManager.getVersionInfo().getBuildNo());
  }

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
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
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
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM, redissonClient,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_MAX_TOPIC_SIZE, PIPELINE_SERVICE.getServiceId(),
              redisConfig.getEnvNamespace()));
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
      bind(Consumer.class)
          .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getTopic(),
              PIPELINE_SERVICE.getServiceId(), redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getBatchSize(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM,
              PIPELINE_SERVICE.getServiceId(), redissonClient,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.TRIGGER_EXECUTION_EVENTS_STREAM_BATCH_SIZE, redisConfig.getEnvNamespace()));
      if (shouldUseEventsFrameworkSnapshotDebezium) {
        RedisConfig redisConfigSnapshot = this.eventsFrameworkSnapshotConfiguration.getRedisConfig();
        RedissonClient redissonClientSnapshot = RedissonClientFactory.getClient(redisConfigSnapshot);
        bind(Consumer.class)
            .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER))
            .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getPlanExecutionsSummarySnapshot().getTopic(),
                PIPELINE_SERVICE.getServiceId(), redissonClientSnapshot,
                EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
                debeziumConsumersConfigs.getPlanExecutionsSummarySnapshot().getBatchSize(),
                redisConfig.getEnvNamespace()));
      } else {
        bind(Consumer.class)
            .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER))
            .toInstance(new NoOpConsumer(DUMMY_TOPIC_NAME, DUMMY_GROUP_NAME));
      }
    }
  }
}
