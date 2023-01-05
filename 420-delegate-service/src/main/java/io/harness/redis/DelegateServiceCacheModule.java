/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.redis;

import io.harness.redis.impl.DelegateRedissonCacheManagerImpl;
import io.harness.redis.intfc.DelegateRedissonCacheManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.redisson.api.RedissonClient;

public class DelegateServiceCacheModule extends AbstractModule {
  private RedisConfig redisConfig;
  private RedissonClient redissonClient;

  @Provides
  @Singleton
  public DelegateRedissonCacheManager getDelegateServiceCacheManager(
      @Named("redissonClient") RedissonClient redissonClient, RedisConfig redisConfig) {
    this.redissonClient = redissonClient;
    this.redisConfig = redisConfig;
    return new DelegateRedissonCacheManagerImpl(redissonClient, redisConfig);
  }
}
