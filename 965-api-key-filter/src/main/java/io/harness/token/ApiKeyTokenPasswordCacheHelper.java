/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.token;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(PL)
@Slf4j
public class ApiKeyTokenPasswordCacheHelper {
  private final Cache<String, char[]> apiKeyTokenPasswordCache =
      Caffeine.newBuilder().maximumSize(5000).expireAfterAccess(10, TimeUnit.MINUTES).build();

  public void putInCache(String tokenId, String password) {
    if (null != tokenId && null != password) {
      apiKeyTokenPasswordCache.put(tokenId, password.toCharArray());
    }
  }

  public String get(String tokenId) {
    if (null != tokenId) {
      char[] password = apiKeyTokenPasswordCache.getIfPresent(tokenId);
      return password == null ? null : String.valueOf(password);
    }
    return null;
  }

  public void invalidate(String tokenId) {
    if (null != tokenId) {
      apiKeyTokenPasswordCache.invalidate(tokenId);
    }
  }
}
