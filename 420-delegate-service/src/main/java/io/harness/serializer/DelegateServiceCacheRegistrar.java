package io.harness.serializer;

import static javax.cache.expiry.Duration.THIRTY_MINUTES;

import io.harness.cache.DelegateRedissonCacheManager;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import javax.cache.expiry.CreatedExpiryPolicy;
import org.redisson.api.RLocalCachedMap;

public class DelegateServiceCacheRegistrar extends AbstractModule {
  public static final String TASK_CACHE = "delegate_task";

  @Provides
  @Named(TASK_CACHE)
  @Singleton
  public RLocalCachedMap<String, Integer> getTaskCache(DelegateRedissonCacheManager cacheManager) {
    return cacheManager.getCache(
        TASK_CACHE, String.class, Integer.class, CreatedExpiryPolicy.factoryOf(THIRTY_MINUTES));
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bindCaches();
  }
  private void bindCaches() {
    MapBinder<String, RLocalCachedMap<?, ?>> rmapBinder =
        MapBinder.newMapBinder(binder(), TypeLiteral.get(String.class), new TypeLiteral<RLocalCachedMap<?, ?>>() {});
    rmapBinder.addBinding(TASK_CACHE).to(Key.get(new TypeLiteral<RLocalCachedMap<String, Integer>>() {
    }, Names.named(TASK_CACHE)));
  }

  private void registerRequiredBindings() {
    requireBinding(DelegateRedissonCacheManager.class);
  }
}
