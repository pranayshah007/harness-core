package io.harness.cache;

import io.harness.redis.RedisConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;

public class DelegateRedissonCacheManagerImpl implements DelegateRedissonCacheManager {
  @Inject @Named("redissonClient") RedissonClient redissonClient;
  RedisConfig redisConfig;

  @Inject
  public DelegateRedissonCacheManagerImpl(@Named("redissonClient") RedissonClient redissonClient, RedisConfig redisConfig) {
    this.redissonClient = redissonClient;
    this.redisConfig = redisConfig;
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy) {
    return redissonClient.getLocalCachedMap(cacheName, LocalCachedMapOptions.defaults());
  }

  @Override
  public <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix) {
    return null;
  }
}
