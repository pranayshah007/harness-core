/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateToken;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(DEL)
@Slf4j
public class DelegateTokenCacheHelper {
  // cache key is delegateId
  private final Cache<String, DelegateToken> delegateTokenCache =
      Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(3, TimeUnit.MINUTES).build();

  public void setDelegateToken(String delegateId, DelegateToken delegateToken) {
    if (delegateId != null && delegateTokenCache != null) {
      delegateTokenCache.put(delegateId, delegateToken);
    } else {
      log.debug(
          "Not able to set delegateToken in cache, either delegateId is null or delegateTokenCache is null. Value of delegateId {}",
          delegateId);
    }
  }

  public DelegateToken getDelegateToken(String delegateId) {
    if (delegateId != null && delegateTokenCache != null) {
      return delegateTokenCache.getIfPresent(delegateId);
    }
    log.debug(
        "Not able to get delegateToken from cache, either delegateId is null or delegateTokenCache is null. Value of delegateId {}",
        delegateId);
    return null;
  }

  public void removeDelegateToken(String delegateId) {
    if (delegateId != null && delegateTokenCache != null) {
      delegateTokenCache.invalidate(delegateId);
    } else {
      log.debug(
          "Not able to remove delegateToken from cache, either delegateId is null or delegateTokenCache is null. Value of delegateId {}",
          delegateId);
    }
  }
}
