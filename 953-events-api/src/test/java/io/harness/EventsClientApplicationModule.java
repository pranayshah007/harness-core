/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.lock.PersistentLocker;
import io.harness.lock.redis.RedisPersistentLocker;
import io.harness.redis.RedisConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;

public class EventsClientApplicationModule extends AbstractModule {
  private final EventsClientApplicationConfiguration appConfig;

  public EventsClientApplicationModule(EventsClientApplicationConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Provides
  @Singleton
  PersistentLocker persistentLocker(Provider<RedisPersistentLocker> redisPersistentLockerProvider) {
    return redisPersistentLockerProvider.get();
  }

  protected void configure() {
    bind(RedisConfig.class).annotatedWith(Names.named("lock")).toInstance(this.appConfig.getRedisLockConfig());
  }

  @Provides
  @Singleton
  NoOpProducer getNoOpProducer() {
    return new NoOpProducer("dummy_topic_name");
  }

  @Provides
  @Singleton
  RedissonClient getRedissonClient() {
    RedisConfig redisConfig = this.appConfig.getEventsFrameworkConfiguration().getRedisConfig();
    Config config = new Config();
    if (!redisConfig.isSentinel()) {
      config.useSingleServer().setAddress(redisConfig.getRedisUrl());
    } else {
      config.useSentinelServers().setMasterName(redisConfig.getMasterName());
      for (String sentinelUrl : redisConfig.getSentinelUrls()) {
        config.useSentinelServers().addSentinelAddress(sentinelUrl);
      }
      config.useSentinelServers().setReadMode(ReadMode.valueOf(redisConfig.getReadMode().name()));
    }
    config.setNettyThreads(redisConfig.getNettyThreads());
    config.setUseScriptCache(redisConfig.isUseScriptCache());
    return Redisson.create(config);
  }
}
