/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.redis;

import io.harness.redis.impl.DelegateRedissonCacheManagerImpl;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.serializer.DelegateServiceCacheRegistrar;
import io.harness.service.impl.DelegateCacheImpl;
import io.harness.service.intfc.DelegateCache;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.redisson.api.RedissonClient;

public class DelegateServiceCacheModule extends AbstractModule {
  private RedisConfig redisConfig;
  private RedissonClient redissonClient;
  private boolean enableRedisForDelegateService;

  public DelegateServiceCacheModule(RedisConfig redisConfig, boolean enableRedisForDelegateService) {
    this.redisConfig = redisConfig;
    this.enableRedisForDelegateService = enableRedisForDelegateService;
  }

  @Provides
  @Singleton
  @Named("enableRedisForDelegateService")
  boolean isEnableRedisForDelegateService() {
    return enableRedisForDelegateService;
  }

  @Provides
  @Singleton
  @Named("redissonClient")
  RedissonClient redissonClient() {
    return RedissonClientFactory.getClient(redisConfig);
  }

  @Provides
  @Singleton
  public DelegateRedissonCacheManager getDelegateServiceCacheManager(RedisConfig redisConfig) {
    this.redissonClient = redissonClient();
    this.redisConfig = redisConfig;
    return new DelegateRedissonCacheManagerImpl(redissonClient, redisConfig);
  }

  @Override
  protected void configure() {
    install(new DelegateServiceCacheRegistrar());
    bind(DelegateCache.class).to(DelegateCacheImpl.class);
  }
}
