/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.memory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.common.delegateselectors.cache.factory.DelegateSelectorsCacheLoaderFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DelegateSelectorsInMemoryCache implements DelegateSelectorsCache {
  private static final long EXPIRY_IN_HOURS = 1;
  private static final long MAX_CACHE_SIZE = 1000;
  @Inject private DelegateSelectorsCacheLoaderFactory factory;
  LoadingCache<String, Map<String, Set<String>>> cache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_CACHE_SIZE)
          .expireAfterWrite(EXPIRY_IN_HOURS, TimeUnit.HOURS)
          .build(new CacheLoader<>() {
            @Override
            public Map<String, Set<String>> load(@NotNull String accountIdentifier) {
              Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
              for (DelegateSelectorsCacheLoader cacheLoader : factory.getCacheLoaders()) {
                hostDelegateSelectors.putAll(cacheLoader.load(accountIdentifier));
              }
              return hostDelegateSelectors;
            }
          });

  @Override
  public Set<String> get(String accountIdentifier, String host) {
    Map<String, Set<String>> hostDelegateSelectMap = null;
    try {
      hostDelegateSelectMap = cache.get(accountIdentifier);
    } catch (ExecutionException e) {
      log.error("Error in fetching delegate selectors cache. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
    Set<String> delegateSelectors = hostDelegateSelectMap.get(host);
    if (delegateSelectors == null) {
      delegateSelectors = new HashSet<>();
    }
    return delegateSelectors;
  }

  @Override
  public void put(String accountIdentifier, String host, Set<String> delegateSelectors) {
    Map<String, Set<String>> hostDelegateSelectorMap = null;
    try {
      hostDelegateSelectorMap = cache.get(accountIdentifier);
    } catch (ExecutionException e) {
      log.error("Error in updating delegate selectors cache. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
    hostDelegateSelectorMap.put(host, delegateSelectors);
    cache.put(accountIdentifier, hostDelegateSelectorMap);
  }

  @Override
  public void remove(String accountIdentifier, Set<String> hosts) {
    Map<String, Set<String>> hostDelegateSelectorMap = null;
    try {
      hostDelegateSelectorMap = cache.get(accountIdentifier);
    } catch (ExecutionException e) {
      log.error("Error in removing entry from delegate selectors cache. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
    hosts.forEach(hostDelegateSelectorMap::remove);
    if (hostDelegateSelectorMap.isEmpty()) {
      cache.invalidate(accountIdentifier);
    } else {
      cache.put(accountIdentifier, hostDelegateSelectorMap);
    }
  }
}
