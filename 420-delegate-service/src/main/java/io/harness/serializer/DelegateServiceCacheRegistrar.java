/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.redis.intfc.DelegateRedissonCacheManager;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.atomic.AtomicInteger;
import org.redisson.api.RLocalCachedMap;

@OwnedBy(DEL)
public class DelegateServiceCacheRegistrar extends AbstractModule {
  public static final String TASK_CACHE = "delegate_task";

  @Provides
  @Named(TASK_CACHE)
  @Singleton
  public RLocalCachedMap<String, AtomicInteger> getTaskCache(DelegateRedissonCacheManager cacheManager) {
    return cacheManager.getCache(TASK_CACHE, String.class, AtomicInteger.class);
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bindCaches();
  }
  private void bindCaches() {
    MapBinder<String, RLocalCachedMap<?, ?>> rmapBinder =
        MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<RLocalCachedMap<?, ?>>() {});
    rmapBinder.addBinding(TASK_CACHE).to(Key.get(new TypeLiteral<RLocalCachedMap<String, AtomicInteger>>() {
    }, Names.named(TASK_CACHE)));
  }

  private void registerRequiredBindings() {
    requireBinding(DelegateRedissonCacheManager.class);
  }
}
