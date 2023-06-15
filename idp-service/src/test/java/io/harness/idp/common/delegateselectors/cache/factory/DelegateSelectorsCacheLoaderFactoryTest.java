/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.factory;

import static org.junit.Assert.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.common.delegateselectors.cache.connector.ConnectorCacheLoader;
import io.harness.idp.common.delegateselectors.cache.plugins.PluginCacheLoader;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DelegateSelectorsCacheLoaderFactoryTest {
  private AutoCloseable openMocks;
  @InjectMocks private DelegateSelectorsCacheLoaderFactory factory;

  @Mock private ConnectorCacheLoader connectorCacheLoader;

  @Mock private PluginCacheLoader pluginCacheLoader;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    factory = new DelegateSelectorsCacheLoaderFactory(connectorCacheLoader, pluginCacheLoader);
  }

  @Test
  public void testGetCacheLoaders() {
    List<DelegateSelectorsCacheLoader> expectedCacheLoaders = new ArrayList<>();
    expectedCacheLoaders.add(connectorCacheLoader);
    expectedCacheLoaders.add(pluginCacheLoader);

    List<DelegateSelectorsCacheLoader> result = factory.getCacheLoaders();

    assertEquals(expectedCacheLoaders, result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
