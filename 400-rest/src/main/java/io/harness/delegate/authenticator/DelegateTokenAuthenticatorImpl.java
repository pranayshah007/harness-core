/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.authenticator;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_AGENT_MTLS_AUTHORITY;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.manage.GlobalContextManager.initGlobalContextGuard;
import static io.harness.manage.GlobalContextManager.upsertGlobalContextRecord;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_JWT_CACHE_HIT;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_JWT_CACHE_MISS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_JWT_DECRYPTION_USING_ACCOUNT_KEY;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.agent.utils.AgentMtlsVerifier;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.utils.DelegateJWTCache;
import io.harness.delegate.utils.DelegateJWTCacheValue;
import io.harness.delegate.utils.DelegateTokenCacheHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTokenException;
import io.harness.exception.RevokedTokenException;
import io.harness.exception.WingsException;
import io.harness.globalcontex.DelegateTokenGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.security.DelegateTokenAuthenticator;

import software.wings.beans.Account;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.InvalidClaimException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import dev.morphia.query.Query;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@Singleton
@OwnedBy(DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTokenAuthenticatorImpl implements DelegateTokenAuthenticator {
  @Inject private HPersistence persistence;
  @Inject private DelegateTokenCacheHelper delegateTokenCacheHelper;
  @Inject private DelegateJWTCache delegateJWTCache;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private AgentMtlsVerifier agentMtlsVerifier;
  @Inject private DelegateSecretManager delegateSecretManager;

  private final LoadingCache<String, String> keyCache =
      Caffeine.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(accountId
              -> Optional.ofNullable(persistence.get(Account.class, accountId))
                     .map(Account::getAccountKey)
                     .orElse(null));

  // TODO: ARPIT clean this class to validate from JWTCache only and remove older delegate token cache method after 3-4
  // weeks.

  // we should set global context data only for rest calls, because we unset the global context thread only for rest
  // calls.
  // We can use delegateTokenName variable to determine whether the delegate is reusing the same jwt or not. And remove
  // it after 1-2 months.
  @Override
  public void validateDelegateToken(String accountId, String tokenString, String delegateId, String delegateTokenName,
      String agentMtlsAuthority, boolean shouldSetTokenNameInGlobalContext) {
    if (accountId == null || GLOBAL_ACCOUNT_ID.equals(accountId)) {
      throw new InvalidRequestException("Access denied", USER_ADMIN);
    }

    // Ensure that delegate connection satisfies mTLS configuration of the account
    this.validateMtlsHeader(accountId, agentMtlsAuthority);

    final String tokenHash = DigestUtils.md5Hex(tokenString);

    // we should validate from cache first and change this debug log to warn, when watcher 754xx is deployed.
    if (isEmpty(delegateTokenName)) {
      log.debug("Delegate token name is empty.");
    } else if (validateDelegateJWTFromCache(accountId, tokenHash, shouldSetTokenNameInGlobalContext)) {
      return;
    }

    EncryptedJWT encryptedJWT;
    try {
      encryptedJWT = EncryptedJWT.parse(tokenString);
    } catch (ParseException e) {
      throw new InvalidTokenException("Invalid delegate token format", USER_ADMIN);
    }

    DelegateToken delegateTokenFromCache = delegateTokenCacheHelper.getDelegateToken(delegateId);
    boolean decryptedWithTokenFromCache =
        decryptWithTokenFromCache(encryptedJWT, delegateTokenFromCache, shouldSetTokenNameInGlobalContext);

    boolean decryptedWithActiveTokenFromDB = false;
    boolean decryptedWithRevokedTokenFromDB = false;

    if (!decryptedWithTokenFromCache) {
      log.debug("Not able to decrypt with token from cache. Fetching it from db.");
      delegateTokenCacheHelper.removeDelegateToken(delegateId);
      decryptedWithActiveTokenFromDB = decryptJWTDelegateToken(
          accountId, DelegateTokenStatus.ACTIVE, encryptedJWT, delegateId, shouldSetTokenNameInGlobalContext);
      if (!decryptedWithActiveTokenFromDB) {
        decryptedWithRevokedTokenFromDB = decryptJWTDelegateToken(
            accountId, DelegateTokenStatus.REVOKED, encryptedJWT, delegateId, shouldSetTokenNameInGlobalContext);
      }
    }

    if (decryptedWithRevokedTokenFromDB
        || (decryptedWithTokenFromCache && DelegateTokenStatus.REVOKED.equals(delegateTokenFromCache.getStatus()))) {
      String delegateHostName = "";
      try {
        delegateHostName = encryptedJWT.getJWTClaimsSet().getIssuer();
      } catch (ParseException e) {
        log.warn("Couldn't parse token", e);
      }
      delegateJWTCache.setDelegateJWTCache(tokenHash, delegateTokenName, new DelegateJWTCacheValue(false, 0L, null));
      log.error("Delegate {} is using REVOKED delegate token. DelegateId: {}", delegateHostName, delegateId);
      throw new RevokedTokenException("Invalid delegate token. Delegate is using revoked token", USER_ADMIN);
    }

    if (!decryptedWithTokenFromCache && !decryptedWithActiveTokenFromDB) {
      decryptWithAccountKey(accountId, encryptedJWT);
    }

    try {
      JWTClaimsSet jwtClaimsSet = encryptedJWT.getJWTClaimsSet();
      final long expiryInMillis = jwtClaimsSet.getExpirationTime().getTime();
      if (System.currentTimeMillis() > expiryInMillis) {
        log.error("Delegate {} is using EXPIRED delegate token. DelegateId: {}", jwtClaimsSet.getIssuer(), delegateId);
        delegateJWTCache.setDelegateJWTCache(tokenHash, delegateTokenName, new DelegateJWTCacheValue(false, 0L, null));
        throw new InvalidRequestException("Unauthorized", EXPIRED_TOKEN, null);
      } else {
        delegateJWTCache.setDelegateJWTCache(tokenHash, delegateTokenName,
            new DelegateJWTCacheValue(true, expiryInMillis, getDelegateTokenNameFromGlobalContext().orElse(null)));
      }
    } catch (Exception ex) {
      delegateJWTCache.setDelegateJWTCache(tokenHash, delegateTokenName, new DelegateJWTCacheValue(false, 0L, null));
      throw new InvalidRequestException("Unauthorized", ex, EXPIRED_TOKEN, null);
    }
  }

  @Override
  public void validateDelegateAuth2Token(String accountId, String tokenString, String agentMtlsAuthority) {
    if (accountId == null || GLOBAL_ACCOUNT_ID.equals(accountId)) {
      throw new InvalidRequestException("Access denied", USER_ADMIN);
    }

    // Ensure that delegate connection satisfies mTLS configuration of the account
    this.validateMtlsHeader(accountId, agentMtlsAuthority);

    // First try to validate token with accountKey.
    if (validateDelegateAuth2TokenWithAccountKey(accountId, tokenString)) {
      return;
    }

    // TODO (Arpit): Replace this logic to validate it from tokenCache.
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .field(DelegateTokenKeys.accountId)
                                     .equal(accountId)
                                     .field(DelegateTokenKeys.status)
                                     .equal(io.harness.delegate.beans.DelegateTokenStatus.ACTIVE);

    try (HIterator<DelegateToken> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        DelegateToken delegateToken = iterator.next();
        try {
          decryptDelegateAuthV2Token(
              accountId, tokenString, delegateSecretManager.getDelegateTokenValue(delegateToken));
          return;
        } catch (Exception e) {
          log.debug("Fail to decrypt Delegate JWT using delegate token {} for the account {}", delegateToken.getName(),
              accountId);
        }
      }
      throw new InvalidRequestException("Unauthorized");
    } catch (Exception e) {
      log.error("Error occurred during fetching list of delegate tokens from db while authenticating.");
      throw new InvalidRequestException("Unauthorized");
    }
  }

  private void validateMtlsHeader(String accountId, String agentMtlsAuthority) {
    if (this.agentMtlsVerifier.isValidRequest(accountId, agentMtlsAuthority)) {
      return;
    }

    log.warn("Failed verification of agent-mtls-authority '{}' for account '{}'.", agentMtlsAuthority, accountId);
    throw new InvalidRequestException("Unauthorized", INVALID_AGENT_MTLS_AUTHORITY, null);
  }

  private boolean validateDelegateAuth2TokenWithAccountKey(String accountId, String tokenString) {
    String accountKey = null;
    try {
      accountKey = keyCache.get(accountId);
    } catch (Exception ex) {
      log.warn("Account key not found for accountId: {}", accountId, ex);
    }

    if (accountKey == null || GLOBAL_ACCOUNT_ID.equals(accountId)) {
      throw new InvalidRequestException("Access denied", USER_ADMIN);
    }
    try {
      decryptDelegateAuthV2Token(accountId, tokenString, accountKey);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void decryptDelegateAuthV2Token(String accountId, String tokenString, String delegateToken) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(delegateToken);
      JWTVerifier verifier = JWT.require(algorithm).withSubject(accountId).build();
      verifier.verify(tokenString);
    } catch (UnsupportedEncodingException | JWTDecodeException | SignatureVerificationException e) {
      throw new InvalidRequestException(
          "Invalid JWTToken received, failed to decode the token", e, INVALID_TOKEN, USER);
    } catch (InvalidClaimException e) {
      throw new InvalidRequestException("Token expired", e, EXPIRED_TOKEN, USER);
    }
  }

  private void decryptWithAccountKey(String accountId, EncryptedJWT encryptedJWT) {
    String accountKey = null;
    try {
      accountKey = keyCache.get(accountId);
    } catch (Exception ex) {
      log.warn("Account key not found for accountId: {}", accountId, ex);
    }

    if (accountKey == null || GLOBAL_ACCOUNT_ID.equals(accountId)) {
      throw new InvalidRequestException("Access denied", USER_ADMIN);
    }
    decryptDelegateToken(encryptedJWT, accountKey);
    delegateMetricsService.recordDelegateMetricsPerAccount(accountId, DELEGATE_JWT_DECRYPTION_USING_ACCOUNT_KEY);
  }

  private boolean decryptJWTDelegateToken(String accountId, DelegateTokenStatus status, EncryptedJWT encryptedJWT,
      String delegateId, boolean shouldSetTokenNameInGlobalContext) {
    long time_start = System.currentTimeMillis();
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .field(DelegateTokenKeys.accountId)
                                     .equal(accountId)
                                     .field(DelegateTokenKeys.status)
                                     .equal(status);

    boolean result = decryptDelegateTokenByQuery(
        query, accountId, status, encryptedJWT, delegateId, shouldSetTokenNameInGlobalContext);
    long time_end = System.currentTimeMillis() - time_start;
    log.debug("Delegate Token verification for accountId {} and status {} has taken {} milliseconds.", accountId,
        status.name(), time_end);
    return result;
  }

  private boolean decryptDelegateTokenByQuery(Query query, String accountId, DelegateTokenStatus status,
      EncryptedJWT encryptedJWT, String delegateId, boolean shouldSetTokenNameInGlobalContext) {
    try (HIterator<DelegateToken> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        DelegateToken delegateToken = iterator.next();
        try {
          decryptDelegateToken(encryptedJWT, delegateSecretManager.getDelegateTokenValue(delegateToken));
          if (DelegateTokenStatus.ACTIVE.equals(delegateToken.getStatus())) {
            setTokenNameInGlobalContext(shouldSetTokenNameInGlobalContext, delegateToken.getName());
          }
          delegateTokenCacheHelper.setDelegateToken(delegateId, delegateToken);
          return true;
        } catch (Exception e) {
          log.debug("Fail to decrypt Delegate JWT for delegateId: {} using delegate token {} for the account {}",
              delegateId, delegateToken.getName(), accountId);
        }
      }
      return false;
    } catch (Exception e) {
      log.error("Error occurred during fetching list of delegate tokens from db while authenticating.");
      return false;
    }
  }

  private boolean decryptWithTokenFromCache(
      EncryptedJWT encryptedJWT, DelegateToken delegateToken, boolean shouldSetTokenNameInGlobalContext) {
    if (delegateToken == null) {
      return false;
    }
    try {
      decryptDelegateToken(encryptedJWT, delegateSecretManager.getDelegateTokenValue(delegateToken));
      if (DelegateTokenStatus.ACTIVE.equals(delegateToken.getStatus())) {
        setTokenNameInGlobalContext(shouldSetTokenNameInGlobalContext, delegateToken.getName());
      }
    } catch (Exception e) {
      log.debug("Fail to decrypt Delegate JWT using delegate token {} for the account {}", delegateToken.getName(),
          delegateToken.getAccountId());
      return false;
    }
    return true;
  }

  private void decryptDelegateToken(EncryptedJWT encryptedJWT, String delegateToken) {
    byte[] encodedKey;
    try {
      encodedKey = Hex.decodeHex(delegateToken.toCharArray());
    } catch (DecoderException e) {
      throw new WingsException(DEFAULT_ERROR_CODE, USER_ADMIN, e);
    }

    JWEDecrypter decrypter;
    try {
      decrypter = new DirectDecrypter(new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES"));
    } catch (KeyLengthException e) {
      throw new WingsException(DEFAULT_ERROR_CODE, USER_ADMIN, e);
    }

    try {
      encryptedJWT.decrypt(decrypter);
    } catch (JOSEException e) {
      throw new InvalidTokenException("Invalid delegate token", USER_ADMIN);
    }
  }

  private boolean validateDelegateJWTFromCache(
      String accountId, String tokenHash, boolean shouldSetTokenNameInGlobalContext) {
    DelegateJWTCacheValue delegateJWTCacheValue = delegateJWTCache.getDelegateJWTCache(tokenHash);

    // cache miss
    if (delegateJWTCacheValue == null) {
      delegateMetricsService.recordDelegateMetricsPerAccount(accountId, DELEGATE_JWT_CACHE_MISS);
      return false;
    }

    // cache hit
    delegateMetricsService.recordDelegateMetricsPerAccount(accountId, DELEGATE_JWT_CACHE_HIT);

    if (!delegateJWTCacheValue.isValid()) {
      throw new RevokedTokenException(
          "Invalid delegate token. Delegate is using invalid or expired JWT token", USER_ADMIN);
    } else if (delegateJWTCacheValue.getExpiryInMillis() < System.currentTimeMillis()) {
      throw new InvalidRequestException("Unauthorized", EXPIRED_TOKEN, null);
    }
    setTokenNameInGlobalContext(shouldSetTokenNameInGlobalContext, delegateJWTCacheValue.getDelegateTokenName());
    return true;
  }

  private void setTokenNameInGlobalContext(boolean shouldSetTokenNameInGlobalContext, String delegateTokenName) {
    if (shouldSetTokenNameInGlobalContext && delegateTokenName != null) {
      if (!GlobalContextManager.isAvailable()) {
        initGlobalContextGuard(new GlobalContext());
      }
      upsertGlobalContextRecord(DelegateTokenGlobalContextData.builder().tokenName(delegateTokenName).build());
    }
  }

  private Optional<String> getDelegateTokenNameFromGlobalContext() {
    DelegateTokenGlobalContextData delegateTokenGlobalContextData =
        GlobalContextManager.get(DelegateTokenGlobalContextData.TOKEN_NAME);
    if (delegateTokenGlobalContextData != null) {
      return Optional.ofNullable(delegateTokenGlobalContextData.getTokenName());
    }
    return Optional.empty();
  }
}