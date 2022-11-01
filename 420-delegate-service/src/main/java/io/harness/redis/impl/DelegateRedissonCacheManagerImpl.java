package io.harness.redis.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.redis.intc.DelegateRedissonCacheManager;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;

import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;


public class DelegateRedissonCacheManagerImpl implements DelegateRedissonCacheManager {

    @Inject
    @Named("redissonClient") RedissonClient redissonClient;

    @Override
    public <K, V> RLocalCachedMap<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy) {
        return redissonClient.getLocalCachedMap(cacheName, LocalCachedMapOptions.defaults());
    }

    @Override
    public <K, V> RLocalCachedMap<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix) {
        return null;
    }
}
