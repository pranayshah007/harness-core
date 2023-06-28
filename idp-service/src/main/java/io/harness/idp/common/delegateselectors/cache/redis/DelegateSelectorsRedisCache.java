/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common.delegateselectors.cache.redis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCacheLoader;
import io.harness.idp.common.delegateselectors.cache.factory.DelegateSelectorsCacheLoaderFactory;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.lock.AcquiredLock;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DelegateSelectorsRedisCache implements DelegateSelectorsCache {
  static final String CACHE_NAME = "idp-account-host-delegate-selectors-cache";
  private static final String LOCK_NAME_FORMAT = "CACHE_%s";
  private final RedissonClient redisson;
  static final long EXPIRY_IN_HOURS = 1;
  private final DelegateSelectorsCacheLoaderFactory factory;
  private final ResourceLocker resourceLocker;

  @Inject
  public DelegateSelectorsRedisCache(@Named("lock") RedisConfig redisConfig,
      DelegateSelectorsCacheLoaderFactory factory, ResourceLocker resourceLocker) {
    this.factory = factory;
    this.resourceLocker = resourceLocker;
    redisson = RedissonClientFactory.getClient(redisConfig);
  }

  @Override
  public Set<String> get(String accountIdentifier, String host) {
    RMapCache<String, Map<String, Set<String>>> cache = redisson.getMapCache(CACHE_NAME);
    Set<String> delegateSelectors;
    if (!cache.containsKey(accountIdentifier)) {
      Map<String, Set<String>> accountData = loadCache(accountIdentifier);
      try (AcquiredLock lock = resourceLocker.acquireLock(String.format(LOCK_NAME_FORMAT, accountIdentifier))) {
        cache.put(accountIdentifier, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);
      } catch (InterruptedException e) {
        log.error("Error while acquiring/releasing lock for account {}", accountIdentifier, e);
        throw new RuntimeException(e.getMessage(), e);
      }
      delegateSelectors = accountData.get(host);
    } else {
      delegateSelectors = cache.get(accountIdentifier).get(host);
    }
    return delegateSelectors != null ? delegateSelectors : new HashSet<>();
  }

  @Override
  public void put(String accountIdentifier, String host, Set<String> delegateSelectors) {
    RMapCache<String, Map<String, Set<String>>> cache = redisson.getMapCache(CACHE_NAME);
    Map<String, Set<String>> accountData = cache.getOrDefault(accountIdentifier, new HashMap<>());
    accountData.put(host, delegateSelectors);
    try (AcquiredLock lock = resourceLocker.acquireLock(String.format(LOCK_NAME_FORMAT, accountIdentifier))) {
      cache.put(accountIdentifier, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);
    } catch (InterruptedException e) {
      log.error("Error while acquiring/releasing lock for account {}", accountIdentifier, e);
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public void remove(String accountIdentifier, Set<String> hosts) {
    RMapCache<String, Map<String, Set<String>>> cache = redisson.getMapCache(CACHE_NAME);
    if (cache.containsKey(accountIdentifier)) {
      Map<String, Set<String>> accountData = cache.get(accountIdentifier);
      hosts.forEach(accountData::remove);
      try (AcquiredLock lock = resourceLocker.acquireLock(String.format(LOCK_NAME_FORMAT, accountIdentifier))) {
        if (accountData.isEmpty()) {
          cache.remove(accountIdentifier);
        } else {
          cache.put(accountIdentifier, accountData, EXPIRY_IN_HOURS, TimeUnit.HOURS);
        }
      } catch (InterruptedException e) {
        log.error("Error while acquiring/releasing lock for account {}", accountIdentifier, e);
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }

  private Map<String, Set<String>> loadCache(String accountIdentifier) {
    Map<String, Set<String>> hostDelegateSelectors = new HashMap<>();
    for (DelegateSelectorsCacheLoader cacheLoader : factory.getCacheLoaders()) {
      hostDelegateSelectors.putAll(cacheLoader.load(accountIdentifier));
    }
    return hostDelegateSelectors;
  }
}
