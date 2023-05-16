/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.utils.delegateselectors;

import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.service.GitIntegrationService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

@Singleton
public class DelegateSelectorsInMemoryCache implements DelegateSelectorsCache {
  private static final long EXPIRY_IN_HOURS = 1;
  private static final long MAX_CACHE_SIZE = 1000;
  @Inject private GitIntegrationService gitIntegrationService;
  LoadingCache<String, Map<String, Set<String>>> cache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHE_SIZE)
          .expireAfterWrite(EXPIRY_IN_HOURS, TimeUnit.HOURS)
          .build(new CacheLoader<>() {
            @Override
            public Map<String, Set<String>> load(@NotNull String accountIdentifier) {
              Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
              List<CatalogConnectorEntity> catalogConnectors =
                  gitIntegrationService.getAllConnectorDetails(accountIdentifier);
              catalogConnectors.forEach(catalogConnector
                  -> hostDelegateSelectors.put(catalogConnector.getHost(), catalogConnector.getDelegateSelectors()));
              return hostDelegateSelectors;
            }
          });

  @Override
  public Set<String> get(String accountIdentifier, String host) throws ExecutionException {
    Map<String, Set<String>> hostDelegateSelectMap = cache.get(accountIdentifier);
    Set<String> delegateSelectors = hostDelegateSelectMap.get(host);
    if (delegateSelectors == null) {
      delegateSelectors = new HashSet<>();
    }
    return delegateSelectors;
  }

  @Override
  public void put(String accountIdentifier, String host, Set<String> delegateSelectors) throws ExecutionException {
    Map<String, Set<String>> hostDelegateSelectorMap = cache.get(accountIdentifier);
    hostDelegateSelectorMap.put(host, delegateSelectors);
    cache.put(accountIdentifier, hostDelegateSelectorMap);
  }
}
