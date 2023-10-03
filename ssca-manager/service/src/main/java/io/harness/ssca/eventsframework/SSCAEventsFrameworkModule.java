/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.eventsframework;

import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.authorization.AuthorizationServiceHeader.SSCA_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.changestreams.DebeziumConsumerConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
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

@OwnedBy(SSCA)
@AllArgsConstructor
public class SSCAEventsFrameworkModule extends AbstractModule {
  private final EventsFrameworkConfiguration eventsFrameworkConfiguration;
  private final DebeziumConsumerConfig debeziumConsumerConfig;

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
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INSTANCE_NG_SSCA_REDIS_EVENT_CONSUMER))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));
    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(EventsFrameworkConstants.INSTANCE_NG_SSCA_REDIS_EVENT_CONSUMER))
          .toInstance(RedisConsumer.of(debeziumConsumerConfig.getInstanceNGConsumer().getTopic(),
              SSCA_SERVICE.getServiceId(), redissonClient, EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME,
              debeziumConsumerConfig.getInstanceNGConsumer().getBatchSize(), redisConfig.getEnvNamespace()));
    }
  }
}
