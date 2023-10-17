/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.jwks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.entities.OidcJwks.OidcJwksKeys;
import io.harness.persistence.HPersistence;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.PL)
public class OidcJwksUtility {
  private static final Duration CACHE_EXPIRATION = Duration.ofHours(12);
  @Inject private HPersistence persistence;

  private LoadingCache<String, io.harness.oidc.entities.OidcJwks> jwksCache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(CACHE_EXPIRATION.toMillis(), TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, io.harness.oidc.entities.OidcJwks>() {
            @Override
            public io.harness.oidc.entities.OidcJwks load(String accountId) throws Exception {
              return getJwksKeysFromDb(accountId);
            }
          });

  public io.harness.oidc.entities.OidcJwks getJwksKeys(String accountId) {
    try {
      return jwksCache.getUnchecked(accountId);
    } catch (CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  public OidcJwks getJwksKeysFromDb(String accountId) {
    if (isNotEmpty(accountId)) {
      return persistence.createQuery(io.harness.oidc.entities.OidcJwks.class)
          .filter(OidcJwksKeys.accountId, accountId)
          .get();
    }
    return null;
  }

  public void saveOidcJwks(OidcJwks oidcJwks) {
    if (!isNull(oidcJwks) && isNotEmpty(oidcJwks.getAccountId())) {
      persistence.save(oidcJwks);
      jwksCache.put(oidcJwks.getAccountId(), oidcJwks);
    }
  }
}
