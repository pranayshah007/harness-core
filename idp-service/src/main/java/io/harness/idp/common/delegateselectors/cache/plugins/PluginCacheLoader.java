/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.plugins;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class PluginCacheLoader implements DelegateSelectorsCacheLoader {
  private PluginsProxyInfoRepository pluginsProxyInfoRepository;

  @Override
  public Map<String, Set<String>> load(String accountIdentifier) {
    Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
    List<PluginsProxyInfoEntity> pluginsProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifier(accountIdentifier);
    pluginsProxies.forEach(pluginsProxy -> {
      if (pluginsProxy.getProxy()) {
        hostDelegateSelectors.put(pluginsProxy.getHost(), new HashSet<>(pluginsProxy.getDelegateSelectors()));
      }
    });
    return hostDelegateSelectors;
  }
}
