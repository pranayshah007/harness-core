/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@OwnedBy(DEL)
@Singleton
public class DelegateJWTCache {
  ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

  private final Cache<String, DelegateJWTCacheValue> delegateJWTCache =
      Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(25, TimeUnit.MINUTES).build();

  private final Cache<String, String> revokedTokenCache =
      Caffeine.newBuilder().maximumSize(100000).expireAfterWrite(23, TimeUnit.MINUTES).build();

  // if delegateTokenName is null that means delegate is not reusing the jwt.
  public void setDelegateJWTCache(
      String tokenHash, String delegateTokenName, DelegateJWTCacheValue delegateJWTCacheValue) {
    if (delegateTokenName == null) {
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
    try {
      readWriteLock.readLock().lock();
      DelegateJWTCacheValue delegateJWTCacheValue = delegateJWTCache.getIfPresent(cacheKey);
      if (delegateJWTCacheValue != null
          && revokedTokenCache.getIfPresent(delegateJWTCacheValue.getDelegateTokenName()) != null) {
        delegateJWTCache.invalidate(cacheKey);
        return new DelegateJWTCacheValue(false, 0L, delegateJWTCacheValue.getDelegateTokenName());
      }
      return delegateJWTCacheValue;
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  public void setRevokedTokenCache(String delegateTokenName, String delegateTokenId) {
    if (delegateTokenName != null) {
      revokedTokenCache.put(delegateTokenName, delegateTokenId);
    }
  }
}
