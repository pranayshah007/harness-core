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
import io.harness.oidc.rsa.OidcRsaKeyService;
import io.harness.persistence.HPersistence;
import io.harness.rsa.RsaKeyPair;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Singleton
@Slf4j
public class OidcJwksUtility {
  private static final Duration CACHE_EXPIRATION = Duration.ofHours(12);
  @Inject private HPersistence persistence;
  @Inject private OidcRsaKeyService oidcRsaKeyService;

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
      if (!isNull(oidcJwks)) {
        return oidcJwks;
      }
    } catch (CacheLoader.InvalidCacheLoadException e) {
      log.debug("Invalid Cache load exception received {}. Generating new OIDC JWKS for account {} ", e, accountId);
    }
    return generateOidcJwks(accountId);
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
    // Generate random bytes
    byte[] randomBytes = new byte[KID_LENGTH];
    new SecureRandom().nextBytes(randomBytes);

    // Encode the random bytes as a Base64 URL-encoded string
    String kid = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

    // Optionally, remove any characters that might cause issues (e.g., '+', '/')
    kid = kid.replace('+', '-').replace('/', '_');

    return kid;
  }

  /**
   * Utility method to generate new OIDC JWKS for the given accountId.
   *
   * @param accountId acocuntId for which OIDC JWKS has to be generated
   * @return newly generated OIDC JWKS
   */
  private OidcJwks generateOidcJwks(String accountId) {
    String kid = generateOidcIdTokenKid();
    RsaKeyPair rsaKeyPair = oidcRsaKeyService.generateRsaKeyPair(accountId);
    OidcJwks newOidcJwks = OidcJwks.builder().accountId(accountId).keyId(kid).rsaKeyPair(rsaKeyPair).build();
    this.saveOidcJwks(newOidcJwks);

    return newOidcJwks;
  }
}
