package io.harness.cache;

import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;

import com.google.inject.ImplementedBy;
import org.redisson.api.RLocalCachedMap;

@ImplementedBy(DelegateRedissonCacheManagerImpl.class)
public interface DelegateRedissonCacheManager {
  <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy);

  <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix);
}
