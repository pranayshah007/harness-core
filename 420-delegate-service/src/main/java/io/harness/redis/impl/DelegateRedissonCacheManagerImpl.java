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
import io.harness.redis.intfc.DelegateRedissonCacheManager;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.TimeUnit;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;

@OwnedBy(DEL)
public class DelegateRedissonCacheManagerImpl implements DelegateRedissonCacheManager {
  @Inject @Named("redissonClient") RedissonClient redissonClient;
  RedisConfig redisConfig;
  // https://github.com/redisson/redisson/blob/master/redisson/src/main/java/org/redisson/api/LocalCachedMapOptions.java#L183
  LocalCachedMapOptions options =
      LocalCachedMapOptions
          .defaults()
          // cacheSize : If cache size is 0 then local cache is unbounded.
          .cacheSize(10000)
          // evictionPolicyOptions: LFU, LRU, SOFT, WEAK, NONE
          // LFU - Counts how often an item was requested. Those that are used least often are discarded first.
          // LRU - Discards the least recently used items first
          // SOFT - Uses weak references, entries are removed by GC
          // WEAK - Uses soft references, entries are removed by GC
          // NONE - No eviction
          .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
          // Defines max idle time in milliseconds of each map entry in local cache.
          // If value equals to <code>0</code> then timeout is not applied
          .maxIdle(0)
          // Defines time to live of each map entry in local cache.
          // If value equals to <code>0</code> then timeout is not applied
          .timeToLive(60, TimeUnit.SECONDS)
          // ReconnectionStrategy: Used to load missed updates during any connection failures to Redis.
          // Options: CLEAR, NONE, LOAD
          // CLEAR - Clear local cache if map instance has been disconnected for a while.
          // LOAD - Store invalidated entry hash in invalidation log for 10 minutes
          //        Cache keys for stored invalidated entry hashes will be removed
          //        if LocalCachedMap instance has been disconnected less than 10 minutes
          //        or whole cache will be cleaned otherwise.
          // NONE - Default. No reconnection handling
          .reconnectionStrategy(LocalCachedMapOptions.ReconnectionStrategy.NONE)
          // Used to synchronize local cache changes.
          // Follow sync strategies are available:
          // INVALIDATE - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
          // UPDATE - Update cache entry across all LocalCachedMap instances on map entry change
          // NONE - No synchronizations on map changes
          .syncStrategy(LocalCachedMapOptions.SyncStrategy.INVALIDATE);

  @Inject
  public DelegateRedissonCacheManagerImpl(
      @Named("redissonClient") RedissonClient redissonClient, RedisConfig redisConfig) {
    this.redissonClient = redissonClient;
    this.redisConfig = redisConfig;
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
    return redissonClient.getLocalCachedMap(cacheName, options);
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, String keyPrefix) {
    return null;
  }
}
