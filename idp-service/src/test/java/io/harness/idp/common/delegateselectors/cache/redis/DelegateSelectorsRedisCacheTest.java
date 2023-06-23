/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.redis;

import static io.harness.idp.common.delegateselectors.cache.redis.DelegateSelectorsRedisCache.CACHE_NAME;
import static io.harness.idp.common.delegateselectors.cache.redis.DelegateSelectorsRedisCache.EXPIRY_IN_HOURS;
import static io.harness.redis.RedisReadMode.SLAVE;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.delegateselectors.cache.connector.ConnectorCacheLoader;
import io.harness.idp.common.delegateselectors.cache.factory.DelegateSelectorsCacheLoaderFactory;
import io.harness.idp.common.delegateselectors.cache.plugins.PluginCacheLoader;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

public class DelegateSelectorsRedisCacheTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  public static final String GITHUB_HOST = "github.com";
  public static final String CLUSTER_HOST = "127.0.0.1";
  public static final String DELEGATE_SELECTOR1 = "d1";
  public static final String DELEGATE_SELECTOR2 = "d2";
  private AutoCloseable openMocks;
  @Mock private DelegateSelectorsCacheLoaderFactory factory;
  @Mock private ConnectorCacheLoader connectorCacheLoader;
  @Mock private PluginCacheLoader pluginCacheLoader;
  @Mock private ResourceLocker resourceLocker;
  private RedissonClient client;
  RMapCache cache = mock(RMapCache.class);
  private DelegateSelectorsRedisCache delegateSelectorsRedisCache;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    mockStatic(RedissonClientFactory.class);
    RedisConfig config = mock(RedisConfig.class);
    when(config.isSentinel()).thenReturn(true);
    when(config.getReadMode()).thenReturn(SLAVE);
    client = mock(RedissonClient.class);
    when(RedissonClientFactory.getClient(any())).thenReturn(client);
    when(client.getMapCache(CACHE_NAME)).thenReturn(cache);
    when(factory.getCacheLoaders()).thenReturn(Arrays.asList(connectorCacheLoader, pluginCacheLoader));

    Map<String, Set<String>> connectorHostDelegateSelectors = new HashMap<>();
    connectorHostDelegateSelectors.put(
        GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    when(connectorCacheLoader.load(ACCOUNT_IDENTIFIER)).thenReturn(connectorHostDelegateSelectors);
    Map<String, Set<String>> pluginHostDelegateSelectors = new HashMap<>();
    pluginHostDelegateSelectors.put(CLUSTER_HOST, Collections.singleton(DELEGATE_SELECTOR1));
    when(pluginCacheLoader.load(ACCOUNT_IDENTIFIER)).thenReturn(pluginHostDelegateSelectors);

    delegateSelectorsRedisCache = new DelegateSelectorsRedisCache(config, factory, resourceLocker);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGet() throws ExecutionException, InterruptedException {
    // when cache is empty
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(false);
    Set<String> delegateSelectors = delegateSelectorsRedisCache.get(ACCOUNT_IDENTIFIER, GITHUB_HOST);

    verify(cache).put(eq(ACCOUNT_IDENTIFIER), any(), eq(EXPIRY_IN_HOURS), eq(TimeUnit.HOURS));
    verify(resourceLocker).acquireLock(anyString());

    assertEquals(2, delegateSelectors.size());
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR1));
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR2));

    // when cache has data for this account
    Map<String, Set<String>> accountData = new HashMap<>();
    accountData.put(GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    accountData.put(CLUSTER_HOST, Collections.singleton(DELEGATE_SELECTOR1));
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(true);
    when(cache.get(ACCOUNT_IDENTIFIER)).thenReturn(accountData);

    delegateSelectors = delegateSelectorsRedisCache.get(ACCOUNT_IDENTIFIER, CLUSTER_HOST);

    assertEquals(1, delegateSelectors.size());
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR1));

    // unknown host
    delegateSelectors = delegateSelectorsRedisCache.get(ACCOUNT_IDENTIFIER, "random_host.com");

    assertEquals(0, delegateSelectors.size());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetException() throws InterruptedException {
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(false);
    when(resourceLocker.acquireLock(anyString())).thenThrow(InterruptedException.class);

    delegateSelectorsRedisCache.get(ACCOUNT_IDENTIFIER, CLUSTER_HOST);

    verify(cache, times(0)).put(eq(ACCOUNT_IDENTIFIER), any(), eq(EXPIRY_IN_HOURS), eq(TimeUnit.HOURS));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPut() throws InterruptedException {
    Map<String, Set<String>> accountData = new HashMap<>();
    accountData.put(GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(true);
    when(cache.getOrDefault(ACCOUNT_IDENTIFIER, new HashMap<>())).thenReturn(accountData);

    delegateSelectorsRedisCache.put(
        ACCOUNT_IDENTIFIER, CLUSTER_HOST, new HashSet<>(Collections.singleton(DELEGATE_SELECTOR1)));

    accountData.put(CLUSTER_HOST, Collections.singleton(DELEGATE_SELECTOR1));
    verify(cache).put(ACCOUNT_IDENTIFIER, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);
    verify(resourceLocker).acquireLock(anyString());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPutException() throws InterruptedException {
    when(resourceLocker.acquireLock(anyString())).thenThrow(InterruptedException.class);
    when(cache.getOrDefault(ACCOUNT_IDENTIFIER, new HashMap<>())).thenReturn(new HashMap<>());

    delegateSelectorsRedisCache.put(
        ACCOUNT_IDENTIFIER, CLUSTER_HOST, new HashSet<>(Collections.singleton(DELEGATE_SELECTOR1)));

    verify(cache, times(0)).put(eq(ACCOUNT_IDENTIFIER), any(), eq(EXPIRY_IN_HOURS), eq(TimeUnit.HOURS));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testRemove() throws InterruptedException {
    Map<String, Set<String>> accountData = new HashMap<>();
    accountData.put(GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    accountData.put(CLUSTER_HOST, Collections.singleton(DELEGATE_SELECTOR1));
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(true);
    when(cache.get(ACCOUNT_IDENTIFIER)).thenReturn(accountData);

    delegateSelectorsRedisCache.remove(ACCOUNT_IDENTIFIER, Collections.singleton(CLUSTER_HOST));

    accountData.remove(CLUSTER_HOST);
    verify(cache).put(ACCOUNT_IDENTIFIER, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);

    // empty for this account
    accountData.remove(GITHUB_HOST);
    delegateSelectorsRedisCache.remove(ACCOUNT_IDENTIFIER, Collections.singleton(CLUSTER_HOST));
    verify(cache).put(ACCOUNT_IDENTIFIER, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);
    verify(resourceLocker, times(2)).acquireLock(anyString());
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testRemoveException() throws InterruptedException {
    Map<String, Set<String>> accountData = new HashMap<>();
    accountData.put(GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    when(cache.containsKey(ACCOUNT_IDENTIFIER)).thenReturn(true);
    when(cache.get(ACCOUNT_IDENTIFIER)).thenReturn(accountData);
    when(resourceLocker.acquireLock(anyString())).thenThrow(InterruptedException.class);

    delegateSelectorsRedisCache.remove(ACCOUNT_IDENTIFIER, new HashSet<>(Collections.singleton(GITHUB_HOST)));

    verify(cache, times(0)).put(eq(ACCOUNT_IDENTIFIER), any(), eq(EXPIRY_IN_HOURS), eq(TimeUnit.HOURS));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
