/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonKryoCodec;
import io.harness.redis.intfc.DelegateRedissonCacheManager;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;

@OwnedBy(DEL)
@Slf4j
public class DelegateRedissonCacheManagerImpl implements DelegateRedissonCacheManager {
  @Inject @Named("redissonClient") RedissonClient redissonClient;
  private RedisConfig redisConfig;

  @Inject
  public DelegateRedissonCacheManagerImpl(
      @Named("redissonClient") RedissonClient redissonClient, RedisConfig redisConfig) {
    this.redissonClient = redissonClient;
    this.redisConfig = redisConfig;
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, LocalCachedMapOptions<K, V> localCachedMapOptions) {
    return redissonClient.getLocalCachedMap(cacheName, new RedissonKryoCodec(), localCachedMapOptions);
  }

  @Override
  public Long redissonCounter(String cacheName, CounterOperation cacheCounterOperation) {
    if (redissonClient.getAtomicLong(cacheName) == null) {
      log.info("First time adding to redis {}", cacheName);
      return redissonClient.getAtomicLong(cacheName).addAndGet(0);
    }
    long val = 0;
    switch (cacheCounterOperation) {
      case GET:
        log.info(
            "TaskCountCache: get Counter value : {} for {}", cacheName, redissonClient.getAtomicLong(cacheName).get());
        return redissonClient.getAtomicLong(cacheName).get();
      case INCREMENT:
        val = redissonClient.getAtomicLong(cacheName).incrementAndGet();
        log.info("TaskCountCache: After Increment counter value {}", val);
        return val;
      case DECREMENT:
        if (redissonClient.getAtomicLong(cacheName).get() < 0) {
          redissonClient.getAtomicLong(cacheName).set(0);
          log.info("TaskCountCache: Should not come here");
          return val;
        }
        val = redissonClient.getAtomicLong(cacheName).decrementAndGet();
        log.info("TaskCountCache: After Decrement counter value {}", val);
        return val;
      default:
        return null;
    }
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, String keyPrefix) {
    return null;
  }
}
