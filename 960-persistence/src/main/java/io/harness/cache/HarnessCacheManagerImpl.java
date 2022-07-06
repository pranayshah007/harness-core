/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;

import java.util.Optional;
import java.util.stream.Stream;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.ExpiryPolicy;

@OwnedBy(PL)
public class HarnessCacheManagerImpl implements HarnessCacheManager {
  private final CacheManager cacheManager;
  private final Optional<CacheManager> enterpriseRedisCacheManagerOptional;
  private final CacheConfig cacheConfig;
  static final String CACHE_PREFIX = "hCache";

  HarnessCacheManagerImpl(
      CacheManager cacheManager, Optional<CacheManager> enterpriseRedisCacheManagerOptional, CacheConfig cacheConfig) {
    this.cacheManager = cacheManager;
    this.enterpriseRedisCacheManagerOptional = enterpriseRedisCacheManagerOptional;
    this.cacheConfig = cacheConfig;
  }

  @Override
  public <K, V> Cache<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy) {
    return getCacheInternal(cacheName, keyType, valueType, expiryPolicy, false);
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType,
      Factory<ExpiryPolicy> expiryPolicy, boolean enterpriseRedis) {
    return getCacheInternal(cacheName, keyType, valueType, expiryPolicy, enterpriseRedis);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K, V> Cache<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix) {
    Cache<VersionedKey<K>, V> jCache =
        getCacheInternal(cacheName, (Class) VersionedKey.class, valueType, expiryPolicy, false);
    return new VersionedCache<>(jCache, keyPrefix);
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType,
      Factory<ExpiryPolicy> expiryPolicy, String keyPrefix, boolean enterpriseRedis) {
    Cache<VersionedKey<K>, V> jCache =
        getCacheInternal(cacheName, (Class) VersionedKey.class, valueType, expiryPolicy, enterpriseRedis);
    return new VersionedCache<>(jCache, keyPrefix);
  }

  private <K, V> Cache<K, V> getCacheInternal(String cacheName, Class<K> keyType, Class<V> valueType,
      Factory<ExpiryPolicy> expiryPolicy, boolean enterpriseRedis) {
    if (isCacheDisabled(cacheName)) {
      return new NoOpCache<>();
    }
    String cacheNamespace = isEmpty(cacheConfig.getCacheNamespace())
        ? CACHE_PREFIX
        : cacheConfig.getCacheNamespace().concat("/").concat(CACHE_PREFIX);
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    MutableConfiguration<K, V> jCacheConfiguration = new MutableConfiguration<>();
    jCacheConfiguration.setTypes(keyType, valueType);
    jCacheConfiguration.setStoreByValue(cacheConfig.getCacheBackend() != CAFFEINE);
    jCacheConfiguration.setExpiryPolicyFactory(expiryPolicy);
    jCacheConfiguration.setStatisticsEnabled(true);
    jCacheConfiguration.setManagementEnabled(true);

    CacheManager manager = (enterpriseRedis && enterpriseRedisCacheManagerOptional.isPresent())
        ? enterpriseRedisCacheManagerOptional.get()
        : cacheManager;

    try {
      return Optional.ofNullable(manager.getCache(internalCacheName, keyType, valueType))
          .orElseGet(() -> manager.createCache(internalCacheName, jCacheConfiguration));
    } catch (CacheException ce) {
      if (isCacheExistsError(ce, internalCacheName)) {
        return manager.getCache(internalCacheName, keyType, valueType);
      }
      throw ce;
    }
  }

  private boolean isCacheExistsError(CacheException ce, String cacheName) {
    return ce.getMessage().equalsIgnoreCase("Cache " + cacheName + " already exists")
        || ce.getMessage().equalsIgnoreCase("A cache named " + cacheName + " already exists.");
  }

  private boolean isCacheDisabled(String cacheName) {
    if (isEmpty(cacheConfig.getDisabledCaches())) {
      return false;
    }
    Optional<String> disabledCacheName =
        Stream.of(cacheName.split(":")).filter(value -> cacheConfig.getDisabledCaches().contains(value)).findFirst();
    return disabledCacheName.isPresent();
  }
}
