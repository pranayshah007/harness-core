/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.plugins;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.rule.Owner;

import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class PluginCacheLoaderTest extends CategoryTest {
  private static final String DELEGATE_SELECTOR1 = "selector1";
  private static final String DELEGATE_SELECTOR2 = "selector2";
  private static final String HOST1 = "host1";
  private static final String HOST2 = "host2";
  private static final String HOST3 = "host3";
  private AutoCloseable openMocks;
  @InjectMocks private PluginCacheLoader cacheLoader;

  @Mock private PluginsProxyInfoRepository pluginsProxyInfoRepository;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testLoad() {
    String accountIdentifier = "exampleAccount";

    PluginsProxyInfoEntity pluginsProxyInfoEntity1 =
        PluginsProxyInfoEntity.builder()
            .accountIdentifier(accountIdentifier)
            .proxy(true)
            .host(HOST1)
            .delegateSelectors(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2))
            .build();

    PluginsProxyInfoEntity pluginsProxyInfoEntity2 =
        PluginsProxyInfoEntity.builder()
            .accountIdentifier(accountIdentifier)
            .proxy(true)
            .host(HOST2)
            .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR1))
            .build();

    PluginsProxyInfoEntity pluginsProxyInfoEntity3 =
        PluginsProxyInfoEntity.builder()
            .accountIdentifier(accountIdentifier)
            .proxy(false)
            .host(HOST3)
            .delegateSelectors(Collections.singletonList(DELEGATE_SELECTOR2))
            .build();

    when(pluginsProxyInfoRepository.findAllByAccountIdentifier(accountIdentifier))
        .thenReturn(Arrays.asList(pluginsProxyInfoEntity1, pluginsProxyInfoEntity2, pluginsProxyInfoEntity3));

    Map<String, Set<String>> expectedHostDelegateSelectors = new HashMap<>();
    expectedHostDelegateSelectors.put(HOST1, new HashSet<>(Arrays.asList(DELEGATE_SELECTOR1, DELEGATE_SELECTOR2)));
    expectedHostDelegateSelectors.put(HOST2, new HashSet<>(Collections.singletonList(DELEGATE_SELECTOR1)));

    Map<String, Set<String>> result = cacheLoader.load(accountIdentifier);

    assertEquals(expectedHostDelegateSelectors, result);
    verify(pluginsProxyInfoRepository).findAllByAccountIdentifier(accountIdentifier);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
