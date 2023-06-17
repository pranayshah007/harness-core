/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_TOKEN_JWT_CACHE;

import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLocalCachedMap;

@OwnedBy(DEL)
@Singleton
public class DelegateJWTCache {
  @Inject @Named("enableRedisForDelegateService") private boolean enableRedisForDelegateService;
  @Inject @Named(DELEGATE_TOKEN_JWT_CACHE) RLocalCachedMap<String, DelegateJWTCacheValue> delegateTokenJWTRedisCache;
  ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final Cache<String, DelegateJWTCacheValue> delegateJWTCache =
      Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(25, TimeUnit.MINUTES).build();

  // if delegateTokenName is null that means delegate is not reusing the jwt.
  public void setDelegateJWTCache(
      String tokenHash, String delegateTokenName, DelegateJWTCacheValue delegateJWTCacheValue) {
    if (delegateTokenName == null) {
      return;
    }
    if (enableRedisForDelegateService) {
      setDelegateTokenJWTRedisCache(tokenHash, delegateJWTCacheValue);
      return;
    }
    try {
      readWriteLock.writeLock().lock();
      delegateJWTCache.put(tokenHash, delegateJWTCacheValue);
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  public DelegateJWTCacheValue getDelegateJWTCache(String cacheKey) {
    if (enableRedisForDelegateService) {
      return delegateTokenJWTRedisCache.get(cacheKey);
    }

    try {
      readWriteLock.readLock().lock();
      return delegateJWTCache.getIfPresent(cacheKey);
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  public void invalidateJWTTokenCache(@NotNull String delegateTokenName, @NotNull String accountId) {
    if (enableRedisForDelegateService) {
      delegateTokenJWTRedisCache.entrySet()
          .stream()
          .filter(ent
              -> delegateTokenName.equals(ent.getValue().getDelegateTokenName())
                  && accountId.equals(ent.getValue().getAccountId()))
          .forEach(
              delegateJWTCacheValueEntry -> delegateTokenJWTRedisCache.fastRemove(delegateJWTCacheValueEntry.getKey()));
    }
  }

  private void setDelegateTokenJWTRedisCache(String tokenHash, DelegateJWTCacheValue delegateJWTCacheValue) {
    delegateTokenJWTRedisCache.putIfAbsent(tokenHash, delegateJWTCacheValue);
  }
}
