/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.memory;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.delegateselectors.cache.connector.ConnectorCacheLoader;
import io.harness.idp.common.delegateselectors.cache.factory.DelegateSelectorsCacheLoaderFactory;
import io.harness.idp.common.delegateselectors.cache.plugins.PluginCacheLoader;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateSelectorsInMemoryCacheTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  public static final String GITHUB_HOST = "github.com";
  public static final String CLUSTER_HOST = "127.0.0.1";
  public static final String DELEGATE_SELECTOR1 = "d1";
  public static final String DELEGATE_SELECTOR2 = "d2";
  private AutoCloseable openMocks;
  @InjectMocks private DelegateSelectorsInMemoryCache delegateSelectorsInMemoryCache;
  @Mock private DelegateSelectorsCacheLoaderFactory factory;
  @Mock private ConnectorCacheLoader connectorCacheLoader;
  @Mock private PluginCacheLoader pluginCacheLoader;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(factory.getCacheLoaders()).thenReturn(Arrays.asList(connectorCacheLoader, pluginCacheLoader));

    Map<String, Set<String>> connectorHostDelegateSelectors = new HashMap<>();
    connectorHostDelegateSelectors.put(
        GITHUB_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    when(connectorCacheLoader.load(ACCOUNT_IDENTIFIER)).thenReturn(connectorHostDelegateSelectors);

    Map<String, Set<String>> pluginHostDelegateSelectors = new HashMap<>();
    pluginHostDelegateSelectors.put(CLUSTER_HOST, Collections.singleton(DELEGATE_SELECTOR1));
    when(pluginCacheLoader.load(ACCOUNT_IDENTIFIER)).thenReturn(pluginHostDelegateSelectors);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAndRemove() throws ExecutionException {
    Set<String> delegateSelectors = delegateSelectorsInMemoryCache.get(ACCOUNT_IDENTIFIER, GITHUB_HOST);
    assertEquals(2, delegateSelectors.size());
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR1));
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR2));

    delegateSelectors = delegateSelectorsInMemoryCache.get(ACCOUNT_IDENTIFIER, CLUSTER_HOST);
    assertEquals(1, delegateSelectors.size());
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR1));

    delegateSelectors = delegateSelectorsInMemoryCache.get(ACCOUNT_IDENTIFIER, "random_host.com");
    assertEquals(0, delegateSelectors.size());

    delegateSelectorsInMemoryCache.remove(ACCOUNT_IDENTIFIER, Collections.singleton(CLUSTER_HOST));
    delegateSelectors = delegateSelectorsInMemoryCache.get(ACCOUNT_IDENTIFIER, CLUSTER_HOST);
    assertEquals(0, delegateSelectors.size());

    delegateSelectorsInMemoryCache.remove(ACCOUNT_IDENTIFIER, Collections.singleton(GITHUB_HOST));
    assertEquals(0, delegateSelectorsInMemoryCache.cache.size());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testPut() throws ExecutionException {
    delegateSelectorsInMemoryCache.put(
        ACCOUNT_IDENTIFIER, CLUSTER_HOST, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    Set<String> delegateSelectors = delegateSelectorsInMemoryCache.get(ACCOUNT_IDENTIFIER, GITHUB_HOST);
    assertEquals(2, delegateSelectors.size());
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR1));
    assertTrue(delegateSelectors.contains(DELEGATE_SELECTOR2));
  }

  @After
  public void tearDown() throws Exception {
    delegateSelectorsInMemoryCache.cache.invalidateAll();
    openMocks.close();
  }
}
