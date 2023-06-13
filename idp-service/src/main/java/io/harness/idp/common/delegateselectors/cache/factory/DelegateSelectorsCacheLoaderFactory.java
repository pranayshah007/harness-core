/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.factory;

import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.common.delegateselectors.cache.connector.ConnectorCacheLoader;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DelegateSelectorsCacheLoaderFactory {
  private ConnectorCacheLoader connectorCacheLoader;

  public List<DelegateSelectorsCacheLoader> getCacheLoaders() {
    List<DelegateSelectorsCacheLoader> cacheLoaders = new ArrayList<>();
    cacheLoaders.add(connectorCacheLoader);
    return cacheLoaders;
  }
}
