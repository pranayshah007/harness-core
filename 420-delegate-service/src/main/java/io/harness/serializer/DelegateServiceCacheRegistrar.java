package io.harness.serializer;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.harness.cache.DelegateRedissonCacheManager;
import io.harness.version.VersionInfoManager;
import org.redisson.api.RLocalCachedMap;

import javax.cache.expiry.CreatedExpiryPolicy;

import static javax.cache.expiry.Duration.THIRTY_MINUTES;

public class DelegateServiceCacheRegistrar  extends AbstractModule {
    public static final String TASK_CACHE = "taskCache1";

    @Provides
    @Named(TASK_CACHE)
    @Singleton
    public RLocalCachedMap<String, Integer> getTaskCache(
            DelegateRedissonCacheManager cacheManager, VersionInfoManager versionInfoManager) {
        return cacheManager.getCache(TASK_CACHE, String.class, Integer.class,
                CreatedExpiryPolicy.factoryOf(THIRTY_MINUTES));
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
        requireBinding(VersionInfoManager.class);
        requireBinding(DelegateRedissonCacheManager.class);
    }
}
