/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.eventsframework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.CDNG_ORCHESTRATION_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_CONFIG_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_FULL_SYNC_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM_MAX_TOPIC_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.INSTANCE_STATS;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSES_REDIS_EVENT_CONSUMER;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER_CD;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.PIPELINE_ORCHESTRATION_EVENT_TOPIC;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM_MAX_TOPIC_SIZE;
import static io.harness.pms.events.PmsEventFrameworkConstants.MAX_PROCESSING_TIME_SECONDS;

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
import io.harness.pms.redisConsumer.DebeziumConsumersConfig;
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

@OwnedBy(PL)
@AllArgsConstructor
public class EventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final DebeziumConsumersConfig debeziumConsumersConfigs;

  @Override
  protected void configure() {
    RedisConfig redisConfig = this.eventsFrameworkConfiguration.getRedisConfig();
    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.NG_ACCOUNT_SETUP))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.GIT_CONFIG_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.GIT_FULL_SYNC_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_EVENTS_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_PUSH_EVENT_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_PR_EVENT_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_BRANCH_HOOK_EVENT_STREAM))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(GIT_CONFIG_STREAM))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(GIT_FULL_SYNC_STREAM))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INSTANCE_STATS))
          .toInstance(NoOpProducer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(INSTANCE_STATS))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
      bind(Consumer.class)
          .annotatedWith(Names.named(CDNG_ORCHESTRATION_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_CRUD, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, NG_MANAGER.getServiceId(), redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_CRUD_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.NG_ACCOUNT_SETUP))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_CRUD, "NG_ACCOUNT_SETUP_GROUP", redissonClient,
              EventsFrameworkConstants.NG_ACCOUNT_SETUP_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.NG_ACCOUNT_SETUP_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.USERMEMBERSHIP, NG_MANAGER.getServiceId(),
              redissonClient, EventsFrameworkConstants.USERMEMBERSHIP_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.USERMEMBERSHIP_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(GitAwareRedisProducer.of(EventsFrameworkConstants.SETUP_USAGE, redissonClient,
              EventsFrameworkConstants.ENTITY_CRUD_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.SETUP_USAGE, NG_MANAGER.getServiceId(), redissonClient,
              EventsFrameworkConstants.SETUP_USAGE_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.SETUP_USAGE_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.ENTITY_ACTIVITY, redissonClient,
              EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.ENTITY_ACTIVITY, NG_MANAGER.getServiceId(),
              redissonClient, EventsFrameworkConstants.ENTITY_ACTIVITY_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.ENTITY_ACTIVITY_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      // todo(abhinav): move this to git sync manager if it is carved out.
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.GIT_CONFIG_STREAM))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.GIT_CONFIG_STREAM, redissonClient,
              EventsFrameworkConstants.GIT_CONFIG_STREAM_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_FULL_SYNC_STREAM))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.GIT_FULL_SYNC_STREAM, redissonClient,
              EventsFrameworkConstants.FULL_SYNC_STREAM_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.USERMEMBERSHIP))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.USERMEMBERSHIP, redissonClient,
              EventsFrameworkConstants.USER_MEMBERSHIP_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(WEBHOOK_EVENTS_STREAM))
          .toInstance(RedisProducer.of(WEBHOOK_EVENTS_STREAM, redissonClient, WEBHOOK_EVENTS_STREAM_MAX_TOPIC_SIZE,
              NG_MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.POLLING_EVENTS_STREAM))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.POLLING_EVENTS_STREAM, redissonClient,
              EventsFrameworkConstants.POLLING_EVENTS_STREAM_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_PUSH_EVENT_STREAM))
          .toInstance(RedisProducer.of(GIT_PUSH_EVENT_STREAM, redissonClient, GIT_PUSH_EVENT_STREAM_MAX_TOPIC_SIZE,
              NG_MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_PR_EVENT_STREAM))
          .toInstance(RedisProducer.of(GIT_PR_EVENT_STREAM, redissonClient, GIT_PR_EVENT_STREAM_MAX_TOPIC_SIZE,
              NG_MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.SAML_AUTHORIZATION_ASSERTION, NG_MANAGER.getServiceId(),
              redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.LDAP_GROUP_SYNC))
          .toInstance(RedisConsumer.of(EventsFrameworkConstants.LDAP_GROUP_SYNC, NG_MANAGER.getServiceId(),
              redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(GIT_BRANCH_HOOK_EVENT_STREAM))
          .toInstance(RedisProducer.of(GIT_BRANCH_HOOK_EVENT_STREAM, redissonClient,
              GIT_BRANCH_HOOK_EVENT_STREAM_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(GIT_CONFIG_STREAM))
          .toInstance(RedisConsumer.of(GIT_CONFIG_STREAM, NG_MANAGER.getServiceId(), redissonClient,
              EventsFrameworkConstants.GIT_CONFIG_STREAM_PROCESSING_TIME,
              EventsFrameworkConstants.GIT_CONFIG_STREAM_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(GIT_FULL_SYNC_STREAM))
          .toInstance(RedisConsumer.of(GIT_FULL_SYNC_STREAM, NG_MANAGER.getServiceId(), redissonClient,
              EventsFrameworkConstants.FULL_SYNC_STREAM_PROCESSING_TIME,
              EventsFrameworkConstants.FULL_SYNC_STREAM_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(INSTANCE_STATS))
          .toInstance(RedisProducer.of(INSTANCE_STATS, redissonClient, EventsFrameworkConstants.DEFAULT_TOPIC_SIZE,
              NG_MANAGER.getServiceId(), redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(INSTANCE_STATS))
          .toInstance(RedisConsumer.of(INSTANCE_STATS, NG_MANAGER.getServiceId(), redissonClient,
              EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME, EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
      bind(Producer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT))
          .toInstance(RedisProducer.of(EventsFrameworkConstants.CD_DEPLOYMENT_EVENT, redissonClient,
              EventsFrameworkConstants.CD_DEPLOYMENT_EVENT_MAX_TOPIC_SIZE, NG_MANAGER.getServiceId(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER_CD))
          .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getTopic(),
              NG_MANAGER.getServiceId(), redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getBatchSize(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(MODULE_LICENSES_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumersConfigs.getModuleLicensesStreaming().getTopic(),
              NG_MANAGER.getServiceId(), redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              debeziumConsumersConfigs.getPlanExecutionsSummaryStreaming().getBatchSize(),
              redisConfig.getEnvNamespace()));
      bind(Consumer.class)
          .annotatedWith(Names.named(CDNG_ORCHESTRATION_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(PIPELINE_ORCHESTRATION_EVENT_TOPIC, NG_MANAGER.getServiceId(), redissonClient,
              java.time.Duration.ofSeconds(MAX_PROCESSING_TIME_SECONDS), PIPELINE_ORCHESTRATION_EVENT_BATCH_SIZE,
              redisConfig.getEnvNamespace()));
    }
  }

  @Provides
  @Singleton
  @Named("debeziumEventsCache")
  public Cache<String, Long> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("debeziumEventsCache", String.class, Long.class,
        AccessedExpiryPolicy.factoryOf(Duration.ONE_HOUR), versionInfoManager.getVersionInfo().getBuildNo());
  }
}
