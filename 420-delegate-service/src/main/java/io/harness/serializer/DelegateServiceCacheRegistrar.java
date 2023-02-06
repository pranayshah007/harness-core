/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.redis.intfc.DelegateRedissonCacheManager;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;

@OwnedBy(DEL)
public class DelegateServiceCacheRegistrar extends AbstractModule {
  public static final String DELEGATE_CACHE = "delegate";
  public static final String DELEGATE_GROUP_CACHE = "delegate_group";
  public static final String DELEGATES_FROM_GROUP_CACHE = "delegates_from_group";
  private static final Integer CACHE_SIZE = 10000;

  @Provides
  @Named(DELEGATE_CACHE)
  @Singleton
  public RLocalCachedMap<String, Delegate> getDelegateCache(DelegateRedissonCacheManager cacheManager) {
    return cacheManager.getCache(DELEGATE_CACHE, String.class, Delegate.class, getLocalCachedMapOptions(1));
  }

  @Provides
  @Named(DELEGATE_GROUP_CACHE)
  @Singleton
  public RLocalCachedMap<String, DelegateGroup> getDelegateGroupCache(DelegateRedissonCacheManager cacheManager) {
    return cacheManager.getCache(DELEGATE_GROUP_CACHE, String.class, DelegateGroup.class, getLocalCachedMapOptions(30));
  }

  @Provides
  @Named(DELEGATES_FROM_GROUP_CACHE)
  @Singleton
  public RLocalCachedMap<String, List<Delegate>> getDelegatesFromGroupCache(DelegateRedissonCacheManager cacheManager) {
    return cacheManager.getCache(DELEGATES_FROM_GROUP_CACHE, String.class, List.class, getLocalCachedMapOptions(30));
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bindCaches();
  }
  private void bindCaches() {
    MapBinder<String, RLocalCachedMap<?, ?>> rmapBinder =
        MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<RLocalCachedMap<?, ?>>() {});
    rmapBinder.addBinding(DELEGATE_CACHE).to(Key.get(new TypeLiteral<RLocalCachedMap<String, Delegate>>() {
    }, Names.named(DELEGATE_CACHE)));
    rmapBinder.addBinding(DELEGATE_GROUP_CACHE).to(Key.get(new TypeLiteral<RLocalCachedMap<String, DelegateGroup>>() {
    }, Names.named(DELEGATE_GROUP_CACHE)));
    rmapBinder.addBinding(DELEGATES_FROM_GROUP_CACHE)
        .to(Key.get(
            new TypeLiteral<RLocalCachedMap<String, List<Delegate>>>() {}, Names.named(DELEGATES_FROM_GROUP_CACHE)));
  }

  public LocalCachedMapOptions getLocalCachedMapOptions(int timeToLiveInMinutes) {
    return LocalCachedMapOptions
        .defaults()
        // cacheSize : If cache size is 0 then local cache is unbounded.
        .cacheSize(CACHE_SIZE)
        // evictionPolicyOptions: LFU, LRU, SOFT, WEAK, NONE
        // LFU - Counts how often an item was requested. Those that are used least often are discarded first.
        // LRU - Discards the least recently used items first
        // SOFT - Uses weak references, entries are removed by GC
        // WEAK - Uses soft references, entries are removed by GC
        // NONE - No eviction
        .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
        // Defines max idle time in milliseconds of each map entry in local cache.
        // maxIdle parameter means that entry will be removed from local cache if it idle within specified time.
        // If value equals to <code>0</code> then timeout is not applied
        .maxIdle(0)
        // Defines time to live of each map entry in local cache.
        // If value equals to <code>0</code> then timeout is not applied
        .timeToLive(timeToLiveInMinutes, TimeUnit.MINUTES)
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
  }

  private void registerRequiredBindings() {
    requireBinding(DelegateRedissonCacheManager.class);
  }
}
