/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.jwks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.KID_LENGTH;

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
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class OidcJwksUtility {
  private static final Duration CACHE_EXPIRATION = Duration.ofHours(12);
  @Inject private HPersistence persistence;

  private LoadingCache<String, OidcJwks> jwksCache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(CACHE_EXPIRATION.toMillis(), TimeUnit.MILLISECONDS)
          .build(new CacheLoader<String, OidcJwks>() {
            @Override
            public OidcJwks load(String accountId) throws Exception {
              return getJwksKeysFromDb(accountId);
            }
          });

  /**
   * Utility method to get the JWKS keys which includes the RSA public /
   * private key pairs and JWKS key identifier for the given accountId.
   *
   * If the JWKS keys does not exist for this accountId then this method will invoke
   * the method to generate the JWKS keys and store them for the given accountId.
   *
   * @param accountId accountId for which JWKS keys have to be provided
   * @return JWKS keys
   */
  public OidcJwks getJwksKeys(String accountId) {
    try {
      OidcJwks oidcJwks = jwksCache.getUnchecked(accountId);
      if (oidcJwks == null) {
        // TODO: OidcJwks for this accountId is not created yet. Create it.
        String kid = generateOidcIdTokenKid();
      }
      return oidcJwks;
    } catch (CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  /**
   * Utility method to get the JWKS keys from DB.
   *
   * @param accountId accountId for which to fetch the JWKS keys.
   * @return JWKS keys
   */
  public OidcJwks getJwksKeysFromDb(String accountId) {
    if (isNotEmpty(accountId)) {
      return persistence.createQuery(OidcJwks.class).filter(OidcJwksKeys.accountId, accountId).get();
    }
    return null;
  }

  public void saveOidcJwks(OidcJwks oidcJwks) {
    if (!isNull(oidcJwks) && isNotEmpty(oidcJwks.getAccountId())) {
      persistence.save(oidcJwks);
      jwksCache.put(oidcJwks.getAccountId(), oidcJwks);
    }
  }

  /**
   * Utility method to generate the key identifier for the JWKS keys.
   *
   * @return key identifier
   */
  public String generateOidcIdTokenKid() {
    // Check if the KID is already generated for the given accountId or not

    // Generate random bytes
    byte[] randomBytes = new byte[KID_LENGTH];
    new SecureRandom().nextBytes(randomBytes);

    // Encode the random bytes as a Base64 URL-encoded string
    String kid = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

    // Optionally, remove any characters that might cause issues (e.g., '+', '/')
    kid = kid.replace('+', '-').replace('/', '_');

    return kid;
  }
}
