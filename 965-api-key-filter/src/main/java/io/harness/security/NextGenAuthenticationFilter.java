/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.ScimAPI;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.remote.TokenClient;
import io.harness.util.JWTTokenFlowAuthFilterUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class NextGenAuthenticationFilter extends JWTAuthenticationFilter {
  public static final String X_API_KEY = "X-Api-Key";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String delimiter = "\\.";

  private final TokenClient tokenClient;
  @Context @Setter @VisibleForTesting private ResourceInfo resourceInfo;

  public NextGenAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      @Named("PRIVILEGED") TokenClient tokenClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.tokenClient = tokenClient;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (!super.testRequestPredicate(containerRequestContext)) {
      // Predicate testing failed with the current request context
      return;
    }
    boolean isScimCall = isScimAPI();
    Optional<String> apiKeyOptional =
        isScimCall ? getApiKeyForScim(containerRequestContext) : getApiKeyFromHeaders(containerRequestContext);

    if (apiKeyOptional.isPresent()) {
      Optional<String> accountIdentifierOptional = getAccountIdentifierFrom(containerRequestContext);
      if (accountIdentifierOptional.isEmpty()) {
        throw new InvalidRequestException("Account detail is not present in the request");
      }
      String accountIdentifier = accountIdentifierOptional.get();
      if (isJWTTokenTypeCheck(accountIdentifier, apiKeyOptional.get())) {
        validateApiKeyForJwt(accountIdentifier, apiKeyOptional.get(), isScimCall);
      } else {
        validateApiKey(accountIdentifier, apiKeyOptional.get());
      }
    } else {
      super.filter(containerRequestContext);
    }
  }

  private boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  private void validateApiKey(String accountIdentifier, String apiKey) {
    String[] splitToken = apiKey.split(delimiter);
    checkIfTokenLengthMatches(splitToken);
    if (EmptyPredicate.isNotEmpty(splitToken)) {
      String tokenId = isOldApiKeyToken(splitToken) ? splitToken[1] : splitToken[2];
      TokenDTO tokenDTO = NGRestUtils.getResponse(tokenClient.getToken(tokenId));

      if (tokenDTO != null) {
        checkIfAccountIdMatches(accountIdentifier, tokenDTO, tokenId);
        checkIfAccountIdInTokenMatches(splitToken, tokenDTO, tokenId);
        checkIfPrefixMatches(splitToken, tokenDTO, tokenId);
        checkIFRawPasswordMatches(splitToken, tokenId, tokenDTO);
        checkIfApiKeyHasExpired(tokenId, tokenDTO);
        Principal principal = getPrincipal(tokenDTO);
        io.harness.security.SecurityContextBuilder.setContext(principal);
        SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      } else {
        logAndThrowTokenException(String.format("Invalid API token %s: Token not found", tokenId), INVALID_TOKEN);
      }
    } else {
      logAndThrowTokenException("Invalid API token: Token is Empty", INVALID_TOKEN);
    }
  }

  private void validateApiKeyForJwt(String accountIdentifier, String apiKey, boolean isScimCall) {
    if (isScimCall) {
      handleSCIMJwtTokenFlow(accountIdentifier, apiKey);
    } else {
      logAndThrowTokenException(
          "NG_SCIM_JWT: Invalid API call: Externally issued JWT token can be only used for making SCIM API calls. Account id for API called: "
              + accountIdentifier,
          INVALID_REQUEST);
    }
  }

  private Optional<String> getApiKeyFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(X_API_KEY);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private Optional<String> getApiKeyForScim(ContainerRequestContext containerRequestContext) {
    String apiKey = getBearerToken(containerRequestContext.getHeaderString(AUTHORIZATION_HEADER));
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private String getBearerToken(String authorizationHeader) {
    String bearerPrefix = "Bearer ";
    if (!authorizationHeader.contains(bearerPrefix)) {
      throw new UnauthorizedException("Bearer prefix not found", USER);
    }
    return authorizationHeader.substring(bearerPrefix.length()).trim();
  }

  private Optional<String> getAccountIdentifierFrom(ContainerRequestContext containerRequestContext) {
    String accountIdentifier = containerRequestContext.getHeaderString(ACCOUNT_HEADER);

    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    return StringUtils.isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }

  private Principal getPrincipal(TokenDTO tokenDTO) {
    Principal principal = null;
    if (tokenDTO.getApiKeyType() == ApiKeyType.SERVICE_ACCOUNT) {
      principal = new ServiceAccountPrincipal(
          tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(), tokenDTO.getAccountIdentifier());
    }
    if (tokenDTO.getApiKeyType() == ApiKeyType.USER) {
      principal = new UserPrincipal(
          tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(), tokenDTO.getAccountIdentifier());
    }
    return principal;
  }

  private void checkIfApiKeyHasExpired(String tokenId, TokenDTO tokenDTO) {
    if (!tokenDTO.isValid()) {
      logAndThrowTokenException(
          String.format("Incoming API token %s has expired. Token id: %s", tokenDTO.getName(), tokenId), EXPIRED_TOKEN);
    }
  }

  private void checkIfPrefixMatches(String[] splitToken, TokenDTO tokenDTO, String tokenId) {
    if (!tokenDTO.getApiKeyType().getValue().equals(splitToken[0])) {
      String message = "Invalid prefix for API token";
      logAndThrowTokenException(String.format("Invalid API token %s: %s", tokenId, message), INVALID_TOKEN);
    }
  }

  private void checkIFRawPasswordMatches(String[] splitToken, String tokenId, TokenDTO tokenDTO) {
    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder($2A, 10);
    if (splitToken.length == 3 && !bCryptPasswordEncoder.matches(splitToken[2], tokenDTO.getEncodedPassword())) {
      String message = "Raw password not matching for API token";
      logAndThrowTokenException(String.format("Invalid API token %s: %s", tokenId, message), INVALID_TOKEN);
    } else if (splitToken.length == 4 && !bCryptPasswordEncoder.matches(splitToken[3], tokenDTO.getEncodedPassword())) {
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

  private void checkIfTokenLengthMatches(String[] splitToken) {
    if (!(isOldApiKeyToken(splitToken) || isNewApiKeyToken(splitToken))) {
      String message = "Token length not matching for API token";
      logAndThrowTokenException(String.format("Invalid API Token: %s", message), INVALID_TOKEN);
    }
  }

  private void logAndThrowTokenException(String errorMessage, ErrorCode errorCode) {
    log.error(errorMessage);
    throw new InvalidRequestException(errorMessage, errorCode, USER);
  }

  private boolean isOldApiKeyToken(String[] splitToken) {
    return splitToken.length == 3;
  }

  private boolean isNewApiKeyToken(String[] splitToken) {
    return splitToken.length == 4;
  }

  private Principal getPrincipalFromServiceAccountDto(ServiceAccountDTO serviceAccountDto) {
    return new ServiceAccountPrincipal(serviceAccountDto.getIdentifier(), serviceAccountDto.getEmail(),
        serviceAccountDto.getEmail(), serviceAccountDto.getAccountIdentifier());
  }

  private void handleSCIMJwtTokenFlow(String accountIdentifier, String jwtToken) {
    List<SettingResponseDTO> settingsResponse =
        JWTTokenFlowAuthFilterUtils.getSettingListResponseByAccountForSCIMAndJWT(accountIdentifier, tokenClient);
    final Map<String, String> settingValuesMap =
        JWTTokenFlowAuthFilterUtils.getScimJwtTokenSettingConfigurationValuesFromDTOList(
            settingsResponse, accountIdentifier);

    String publicKeysJsonString = JWTTokenFlowAuthFilterUtils.getPublicKeysJsonStringFromUrl(accountIdentifier,
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER));
    JWTTokenFlowAuthFilterUtils.validateJwtTokenAndMatchClaimKeyValue(jwtToken,
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER),
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER), publicKeysJsonString,
        accountIdentifier);

    ServiceAccountDTO serviceAccountDTO = JWTTokenFlowAuthFilterUtils.getServiceAccountByIdAndAccountId(
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER),
        accountIdentifier, tokenClient);

    if (null != serviceAccountDTO) {
      log.info(String.format(
          "NG_SCIM_JWT: Service account details successfully fetched for account: %s, with ServiceAccount Id: %s",
          accountIdentifier, serviceAccountDTO.getIdentifier()));

      // set ServicePrincipal in SecurityContext for Harness internal authorization
      Principal servicePrincipal = getPrincipalFromServiceAccountDto(serviceAccountDTO);
      io.harness.security.SecurityContextBuilder.setContext(servicePrincipal);
      SourcePrincipalContextBuilder.setSourcePrincipal(servicePrincipal);

      log.info(String.format(
          "NG_SCIM_JWT: Security context set with ServicePrincipal id: %s, for SCIM request using externally issued JWT token in account: %s",
          servicePrincipal.getName(), accountIdentifier));
    }
  }

  private boolean isJWTTokenTypeCheck(String accountIdentifier, String token) {
    String[] splitToken = token.split(delimiter);
    return splitToken.length == 3 && JWTTokenFlowAuthFilterUtils.isJWTTokenType(splitToken, accountIdentifier);
  }
}
