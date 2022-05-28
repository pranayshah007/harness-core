/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.authenticator;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_ADMIN;
import static io.harness.manage.GlobalContextManager.initGlobalContextGuard;
import static io.harness.manage.GlobalContextManager.upsertGlobalContextRecord;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.GlobalContext;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.utils.DelegateTokenCacheHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTokenException;
import io.harness.exception.RevokedTokenException;
import io.harness.exception.WingsException;
import io.harness.globalcontex.DelegateTokenGlobalContextData;
import io.harness.manage.GlobalContextManager;
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
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.mongodb.morphia.query.Query;

@Slf4j
@Singleton
@OwnedBy(DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTokenAuthenticatorImpl implements DelegateTokenAuthenticator {
  @Inject private HPersistence persistence;
  @Inject private DelegateTokenCacheHelper delegateTokenCacheHelper;

  private final LoadingCache<String, String> keyCache =
      Caffeine.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(accountId
              -> Optional.ofNullable(persistence.get(Account.class, accountId))
                     .map(Account::getAccountKey)
                     .orElse(null));

  // we should set global context data only for rest calls, because we unset the global context thread only for rest
  // calls.
  @Override
  public void validateDelegateToken(
      String accountId, String tokenString, String delegateId, boolean shouldSetTokenNameInGlobalContext) {
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
      log.error("Delegate {} is using REVOKED delegate token. DelegateId: {}", delegateHostName, delegateId);
      throw new RevokedTokenException("Invalid delegate token. Delegate is using revoked token", USER_ADMIN);
    }

    if (!decryptedWithTokenFromCache && !decryptedWithActiveTokenFromDB) {
      decryptWithAccountKey(accountId, encryptedJWT);
    }

    try {
      JWTClaimsSet jwtClaimsSet = encryptedJWT.getJWTClaimsSet();
      if (System.currentTimeMillis() > jwtClaimsSet.getExpirationTime().getTime()) {
        log.error("Delegate {} is using EXPIRED delegate token. DelegateId: {}", jwtClaimsSet.getIssuer(), delegateId);
        throw new InvalidRequestException("Unauthorized", EXPIRED_TOKEN, null);
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Unauthorized", ex, EXPIRED_TOKEN, null);
    }
  }

  @Override
  public void validateDelegateAuth2Token(String accountId, String tokenString) {
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
          if (delegateToken.isNg()) {
            decryptDelegateAuthV2Token(accountId, tokenString, decodeBase64ToString(delegateToken.getValue()));
          } else {
            decryptDelegateAuthV2Token(accountId, tokenString, delegateToken.getValue());
          }
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
          if (delegateToken.isNg()) {
            decryptDelegateToken(encryptedJWT, decodeBase64ToString(delegateToken.getValue()));
          } else {
            decryptDelegateToken(encryptedJWT, delegateToken.getValue());
          }
          setTokenNameInGlobalContext(shouldSetTokenNameInGlobalContext, delegateToken);
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
      if (delegateToken.isNg()) {
        decryptDelegateToken(encryptedJWT, decodeBase64ToString(delegateToken.getValue()));
      } else {
        decryptDelegateToken(encryptedJWT, delegateToken.getValue());
      }
      setTokenNameInGlobalContext(shouldSetTokenNameInGlobalContext, delegateToken);
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

  private void setTokenNameInGlobalContext(boolean shouldSetTokenNameInGlobalContext, DelegateToken delegateToken) {
    if (shouldSetTokenNameInGlobalContext && DelegateTokenStatus.ACTIVE.equals(delegateToken.getStatus())) {
      if (!GlobalContextManager.isAvailable()) {
        initGlobalContextGuard(new GlobalContext());
      }
      upsertGlobalContextRecord(DelegateTokenGlobalContextData.builder().tokenName(delegateToken.getName()).build());
    }
  }
}
