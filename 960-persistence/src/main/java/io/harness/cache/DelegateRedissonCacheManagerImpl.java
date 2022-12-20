package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.redis.RedisConfig;

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
  LocalCachedMapOptions options = LocalCachedMapOptions.defaults()
                                      .cacheSize(10000)
                                      .evictionPolicy(LocalCachedMapOptions.EvictionPolicy.LRU)
                                      .maxIdle(10, TimeUnit.SECONDS)
                                      .timeToLive(60, TimeUnit.SECONDS);

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
