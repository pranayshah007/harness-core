package io.harness.cache;

import org.redisson.api.RLocalCachedMap;

import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;

public interface DelegateRedissonCacheManager {

    <K, V> RLocalCachedMap<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy);

    <K, V> RLocalCachedMap<K, V> getCache(
            String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix);

}
