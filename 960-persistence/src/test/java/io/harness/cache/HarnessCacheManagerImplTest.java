/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.cache.CacheBackend.REDIS;
import static io.harness.cache.HarnessCacheManagerImpl.CACHE_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.Optional;
import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class HarnessCacheManagerImplTest extends CategoryTest {
  @Captor private ArgumentCaptor<MutableConfiguration<String, Integer>> configCaptor;
  private static final String DISABLED_CACHE_NAME = "disabledCache";
  private HarnessCacheManager harnessCacheManager;
  private CacheManager cacheManager;
  private String cacheNamespace;

  @Before
  public void setup() {
    initMocks(this);
    cacheManager = mock(CacheManager.class);
    CacheConfig cacheConfig = CacheConfig.builder()
                                  .cacheBackend(REDIS)
                                  .cacheNamespace("test")
                                  .disabledCaches(Collections.singleton(DISABLED_CACHE_NAME))
                                  .build();
    this.harnessCacheManager = new HarnessCacheManagerImpl(cacheManager, Optional.empty(), cacheConfig);
    this.cacheNamespace = isEmpty(cacheConfig.getCacheNamespace())
        ? CACHE_PREFIX
        : cacheConfig.getCacheNamespace().concat("/").concat(CACHE_PREFIX);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getCache_shouldReturnCache() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    when(cacheManager.getCache(internalCacheName, String.class, Object.class)).thenReturn(new NoOpCache<>());
    Cache<String, Object> cache = harnessCacheManager.getCache(
        cacheName, String.class, Object.class, AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES));
    assertThat(cache).isNotNull();
    verify(cacheManager, times(1)).getCache(internalCacheName, String.class, Object.class);
    verify(cacheManager, times(0)).createCache(any(), any());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getVersionedCache_shouldPass() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    when(cacheManager.getCache(internalCacheName, VersionedKey.class, Object.class)).thenReturn(new NoOpCache<>());
    Cache<String, Object> cache = harnessCacheManager.getCache(
        cacheName, String.class, Object.class, AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES), "version");
    assertThat(cache).isNotNull();
    verify(cacheManager, times(1)).getCache(internalCacheName, VersionedKey.class, Object.class);
    verify(cacheManager, times(0)).createCache(any(), any());
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getVersionedCache_shouldFail_NullCache() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    when(cacheManager.getCache(internalCacheName, VersionedKey.class, Object.class)).thenReturn(null);
    Cache<String, Object> cache = harnessCacheManager.getCache(
        cacheName, String.class, Object.class, AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES), "version");
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getVersionedCache_shouldFail_EmptyPrefix() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    when(cacheManager.getCache(internalCacheName, VersionedKey.class, Object.class)).thenReturn(null);
    Cache<String, Object> cache = harnessCacheManager.getCache(
        cacheName, String.class, Object.class, AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES), "");
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getCache_shouldCreateCache() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    Factory<ExpiryPolicy> expiryPolicy = AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES);
    when(cacheManager.getCache(internalCacheName, String.class, Integer.class)).thenReturn(null);
    when(cacheManager.createCache(eq(internalCacheName), any(MutableConfiguration.class)))
        .thenReturn(new NoOpCache<>());
    Cache<String, Integer> cache = harnessCacheManager.getCache(cacheName, String.class, Integer.class, expiryPolicy);
    assertThat(cache).isNotNull();
    verify(cacheManager, times(1)).getCache(internalCacheName, String.class, Integer.class);
    verify(cacheManager, times(1)).createCache(eq(internalCacheName), configCaptor.capture());

    MutableConfiguration<String, Integer> cacheConfiguration = configCaptor.getValue();

    assertThat(cacheConfiguration).isNotNull();
    assertThat(cacheConfiguration.getKeyType()).isEqualTo(String.class);
    assertThat(cacheConfiguration.getValueType()).isEqualTo(Integer.class);
    assertThat(cacheConfiguration.isStoreByValue()).isTrue();
    assertThat(cacheConfiguration.getExpiryPolicyFactory()).isEqualTo(expiryPolicy);
    assertThat(cacheConfiguration.isManagementEnabled()).isTrue();
    assertThat(cacheConfiguration.isStatisticsEnabled()).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getCache_shouldReturnCacheAfterError_Hazelcast() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    Factory<ExpiryPolicy> expiryPolicy = AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES);
    when(cacheManager.getCache(internalCacheName, String.class, Integer.class))
        .thenReturn(null)
        .thenReturn(new NoOpCache<>());
    when(cacheManager.createCache(eq(internalCacheName), any(MutableConfiguration.class)))
        .thenThrow(new CacheException("A cache named " + internalCacheName + " already exists."));
    Cache<String, Integer> cache = harnessCacheManager.getCache(cacheName, String.class, Integer.class, expiryPolicy);
    assertThat(cache).isNotNull();
    verify(cacheManager, times(2)).getCache(internalCacheName, String.class, Integer.class);
    verify(cacheManager, times(1)).createCache(eq(internalCacheName), any(MutableConfiguration.class));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getCache_shouldReturnCacheAfterError_RedisAndCaffeine() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    Factory<ExpiryPolicy> expiryPolicy = AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES);
    when(cacheManager.getCache(internalCacheName, String.class, Integer.class))
        .thenReturn(null)
        .thenReturn(new NoOpCache<>());
    when(cacheManager.createCache(eq(internalCacheName), any(MutableConfiguration.class)))
        .thenThrow(new CacheException("Cache " + internalCacheName + " already exists"));
    Cache<String, Integer> cache = harnessCacheManager.getCache(cacheName, String.class, Integer.class, expiryPolicy);
    assertThat(cache).isNotNull();
    verify(cacheManager, times(2)).getCache(internalCacheName, String.class, Integer.class);
    verify(cacheManager, times(1)).createCache(eq(internalCacheName), any(MutableConfiguration.class));
  }

  @Test(expected = CacheException.class)
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getCache_shouldThrowError() {
    String cacheName = "testCache";
    String internalCacheName = String.format("%s/%s", cacheNamespace, cacheName);
    Factory<ExpiryPolicy> expiryPolicy = AccessedExpiryPolicy.factoryOf(Duration.TEN_MINUTES);
    when(cacheManager.getCache(internalCacheName, String.class, Integer.class))
        .thenReturn(null)
        .thenReturn(new NoOpCache<>());
    when(cacheManager.createCache(eq(internalCacheName), any(MutableConfiguration.class)))
        .thenThrow(new CacheException("Connection failed"));
    Cache<String, Integer> cache = harnessCacheManager.getCache(cacheName, String.class, Integer.class, expiryPolicy);
    assertThat(cache).isNotNull();
    verify(cacheManager, times(1)).getCache(internalCacheName, String.class, Integer.class);
    verify(cacheManager, times(1)).createCache(eq(internalCacheName), any(MutableConfiguration.class));
  }
}
