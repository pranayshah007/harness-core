/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.factory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.common.delegateselectors.cache.connector.ConnectorCacheLoader;
import io.harness.idp.common.delegateselectors.cache.plugins.PluginCacheLoader;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class DelegateSelectorsCacheLoaderFactory {
  private ConnectorCacheLoader connectorCacheLoader;
  private PluginCacheLoader pluginCacheLoader;

  public List<DelegateSelectorsCacheLoader> getCacheLoaders() {
    List<DelegateSelectorsCacheLoader> cacheLoaders = new ArrayList<>();
    cacheLoaders.add(connectorCacheLoader);
    cacheLoaders.add(pluginCacheLoader);
    return cacheLoaders;
  }
}
