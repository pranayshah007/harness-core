/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.token;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.hash.HashUtils;
import io.harness.ng.core.dto.TokenDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class TokenValidationHelper {
  public static final String apiKeyOrTokenDelimiterRegex = "\\.";

  public static final String API_TOKEN_PASSWORD_HASH_CACHE_KEY = "apiTokenPasswordHashCacheKey";

  @Inject @Named(API_TOKEN_PASSWORD_HASH_CACHE_KEY) private Cache<String, String> apiTokenPasswordHashCache;

  private void checkIfApiKeyHasExpired(String tokenId, TokenDTO tokenDTO) {
    if (!tokenDTO.isValid()) {
      if (apiTokenPasswordHashCache.containsKey(tokenId)) {
        log.warn("NG_API_KEY_TOKEN: Removing from cache [{}], key: {} having accountId: {}",
            API_TOKEN_PASSWORD_HASH_CACHE_KEY, tokenId, tokenDTO.getAccountIdentifier());
        apiTokenPasswordHashCache.remove(tokenId);
      }
      throw new InvalidRequestException(
          "Incoming API token " + tokenDTO.getName() + String.format(" has expired %s", tokenId));
    }
  }

  private void checkIfPrefixMatches(String[] splitToken, TokenDTO tokenDTO, String tokenId) {
    if (!tokenDTO.getApiKeyType().getValue().equals(splitToken[0])) {
      String message = "Invalid prefix for API token";
      log.warn(message);
      throw new InvalidRequestException(String.format("Invalid API token %s: %s", tokenId, message));
    }
  }

  private void checkIFRawPasswordMatches(
      String[] splitToken, String tokenId, TokenDTO tokenDTO, boolean usePasswordHashCache) {
    String errMessage = String.format("Invalid API token %s: %s", tokenId, "Password not matching for API key token");
    if (usePasswordHashCache && apiTokenPasswordHashCache.containsKey(tokenId)) {
      log.info(
          "NG_API_KEY_TOKEN: [CACHE_HIT] on [{}] cache for password hash matching of api key token: {} for account: {}",
          API_TOKEN_PASSWORD_HASH_CACHE_KEY, tokenId, tokenDTO.getAccountIdentifier());
      matchPasswordsHashInCache(splitToken, tokenId, errMessage);
    } else {
      matchRawPasswordsNotFoundInCache(splitToken, tokenId, tokenDTO, errMessage, usePasswordHashCache);
    }
  }

  private void matchPasswordsHashInCache(String[] splitToken, String tokenId, String errMessage) {
    if (isOldApiKeyToken(splitToken)) {
      final String sha256HashOfToken = HashUtils.calculateSha256(splitToken[2]);
      matchTokenPwdHash(sha256HashOfToken, tokenId, errMessage);
    } else if (isNewApiKeyToken(splitToken)) {
      final String sha256HashOfToken = HashUtils.calculateSha256(splitToken[3]);
      matchTokenPwdHash(sha256HashOfToken, tokenId, errMessage);
    }
  }

  private void matchTokenPwdHash(String sha256HashOfTokenPwd, String tokenId, String errMessage) {
    if (isNotEmpty(sha256HashOfTokenPwd) && !sha256HashOfTokenPwd.equals(apiTokenPasswordHashCache.get(tokenId))) {
      log.warn(errMessage);
      throw new InvalidRequestException(errMessage);
    }
  }

  private void matchRawPasswordsNotFoundInCache(
      String[] splitToken, String tokenId, TokenDTO tokenDTO, String errMessage, boolean usePasswordHashCache) {
    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder($2A, 10);
    if (isOldApiKeyToken(splitToken)) {
      matchRawPasswordsValue(
          splitToken[2], tokenId, tokenDTO, errMessage, usePasswordHashCache, bCryptPasswordEncoder, "old format");
    } else if (isNewApiKeyToken(splitToken)) {
      matchRawPasswordsValue(
          splitToken[3], tokenId, tokenDTO, errMessage, usePasswordHashCache, bCryptPasswordEncoder, "new format");
    }
  }

  private void matchRawPasswordsValue(String splitTokenValue, String tokenId, TokenDTO tokenDTO, String errMessage,
      boolean usePasswordHashCache, BCryptPasswordEncoder bCryptPasswordEncoder, String tokenFormat) {
    if (!bCryptPasswordEncoder.matches(splitTokenValue, tokenDTO.getEncodedPassword())) {
      log.warn(errMessage);
      throw new InvalidRequestException(errMessage);
    } else {
      if (usePasswordHashCache) {
        log.info(
            "NG_API_KEY_TOKEN: [CACHE_MISS] on [{}] cache for {} api key token: {} on account: {}, adding to cache",
            API_TOKEN_PASSWORD_HASH_CACHE_KEY, tokenFormat, tokenId, tokenDTO.getAccountIdentifier());
        apiTokenPasswordHashCache.put(tokenId, HashUtils.calculateSha256(splitTokenValue));
      }
    }
  }

  private void checkIfAccountIdInTokenMatches(String[] splitToken, TokenDTO tokenDTO, String tokenId) {
    if (isNewApiKeyToken(splitToken) && !splitToken[1].equals(tokenDTO.getAccountIdentifier())) {
      throw new InvalidRequestException(String.format("Invalid accountId in token %s", tokenId));
    }
  }

  private void checkIfAccountIdMatches(String accountIdentifier, TokenDTO tokenDTO, String tokenId) {
    if (!accountIdentifier.equals(tokenDTO.getAccountIdentifier())) {
      throw new InvalidRequestException(String.format("Invalid account token access %s", tokenId));
    }
  }

  public void validateToken(
      TokenDTO tokenDTO, String accountIdentifier, String tokenId, String apiKey, boolean useTokenPasswordHashCache) {
    if (tokenDTO != null) {
      String[] splitToken = getApiKeyTokenComponents(apiKey);
      checkIfAccountIdMatches(accountIdentifier, tokenDTO, tokenId);
      checkIfAccountIdInTokenMatches(splitToken, tokenDTO, tokenId);
      checkIfPrefixMatches(splitToken, tokenDTO, tokenId);
      checkIFRawPasswordMatches(splitToken, tokenId, tokenDTO, useTokenPasswordHashCache);
      checkIfApiKeyHasExpired(tokenId, tokenDTO);
    } else {
      throw new InvalidRequestException(String.format("Invalid API token %s: Token not found", tokenId));
    }
  }

  private String[] getApiKeyTokenComponents(String apiKey) {
    return apiKey.split(apiKeyOrTokenDelimiterRegex);
  }

  private boolean isOldApiKeyToken(String[] splitToken) {
    return splitToken.length == 3;
  }

  private boolean isNewApiKeyToken(String[] splitToken) {
    return splitToken.length == 4;
  }

  public String parseApiKeyToken(String apiKey) {
    String message = "Invalid API Token: Token is Empty";
    if (EmptyPredicate.isEmpty(apiKey)) {
      log.warn(message);
      throw new InvalidRequestException(message);
    }
    String[] splitToken = getApiKeyTokenComponents(apiKey);
    if (EmptyPredicate.isEmpty(splitToken)) {
      log.warn(message);
      throw new InvalidRequestException(message);
    }
    if (!(isOldApiKeyToken(splitToken) || isNewApiKeyToken(splitToken))) {
      message = "Token length not matching for API token";
      log.warn(message);
      throw new InvalidRequestException(String.format("Invalid API Token: %s", message));
    }
    return isOldApiKeyToken(splitToken) ? splitToken[1] : splitToken[2];
  }
}
