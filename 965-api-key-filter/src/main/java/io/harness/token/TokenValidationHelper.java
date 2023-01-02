/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.token;

import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.TokenDTO;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class TokenValidationHelper {
  public static final String apiKeyOrTokenDelimiterRegex = "\\.";

  private void checkIfApiKeyHasExpired(String tokenId, TokenDTO tokenDTO) {
    if (!tokenDTO.isValid()) {
      logAndThrowTokenException(String.format("Incoming API token %s has expired. Token id: %s", tokenDTO.getName(), tokenId), EXPIRED_TOKEN);
    }
  }

  private void logAndThrowTokenException(String errorMessage, ErrorCode errorCode) {
    log.error(errorMessage);
    throw new InvalidRequestException(errorMessage, errorCode, USER);
  }

  private void checkIfPrefixMatches(String[] splitToken, TokenDTO tokenDTO, String tokenId) {
    if (!tokenDTO.getApiKeyType().getValue().equals(splitToken[0])) {
      String message = "Invalid prefix for API token";
      logAndThrowTokenException(String.format("Invalid API token %s: %s", tokenId, message), INVALID_TOKEN);
    }
  }

  private void checkIFRawPasswordMatches(String[] splitToken, String tokenId, TokenDTO tokenDTO) {
    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder($2A, 10);
    if (isOldApiKeyToken(splitToken) && !bCryptPasswordEncoder.matches(splitToken[2], tokenDTO.getEncodedPassword())) {
      String message = "Raw password not matching for API token";
      logAndThrowTokenException(String.format("Invalid API token %s: %s", tokenId, message), INVALID_TOKEN);
    } else if (isNewApiKeyToken(splitToken) && !bCryptPasswordEncoder.matches(splitToken[3], tokenDTO.getEncodedPassword())) {
      String message = "Raw password not matching for new API token format";
      logAndThrowTokenException(String.format("Invalid API token %s: %s", tokenId, message), INVALID_TOKEN);
    }
  }

  private void checkIfAccountIdInTokenMatches(String[] splitToken, TokenDTO tokenDTO, String tokenId) {
    if (isNewApiKeyToken(splitToken) && !splitToken[1].equals(tokenDTO.getAccountIdentifier())) {
      logAndThrowTokenException(String.format("Invalid accountId in token %s", tokenId), INVALID_TOKEN);
    }
  }

  private void checkIfAccountIdMatches(String accountIdentifier, TokenDTO tokenDTO, String tokenId) {
    if (!accountIdentifier.equals(tokenDTO.getAccountIdentifier())) {
      logAndThrowTokenException(String.format("Invalid account token access %s", tokenId), INVALID_TOKEN);
    }
  }

  public void validateToken(TokenDTO tokenDTO, String accountIdentifier, String tokenId, String apiKey) {
    if (tokenDTO != null) {
      String[] splitToken = getApiKeyTokenComponents(apiKey);
      checkIfAccountIdMatches(accountIdentifier, tokenDTO, tokenId);
      checkIfAccountIdInTokenMatches(splitToken, tokenDTO, tokenId);
      checkIfPrefixMatches(splitToken, tokenDTO, tokenId);
      checkIFRawPasswordMatches(splitToken, tokenId, tokenDTO);
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
      logAndThrowTokenException(String.format("Invalid API Token: %s", message), INVALID_TOKEN);
    }
    return isOldApiKeyToken(splitToken) ? splitToken[1] : splitToken[2];
  }
}
