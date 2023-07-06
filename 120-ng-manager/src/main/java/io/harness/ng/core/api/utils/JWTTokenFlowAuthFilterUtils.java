/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.api.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_INPUT_SET;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.UNEXPECTED;
import static io.harness.exception.WingsException.USER;
import static io.harness.token.TokenValidationHelper.apiKeyOrTokenDelimiterRegex;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.ng.core.api.cache.JwtTokenPublicKeysJsonData;
import io.harness.ng.core.api.cache.JwtTokenScimAccountSettingsData;
import io.harness.ng.core.api.cache.JwtTokenServiceAccountData;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.serviceaccount.ServiceAccountDTO;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.json.JSONException;
import org.json.JSONObject;

@Singleton
@Slf4j
@OwnedBy(PL)
public class JWTTokenFlowAuthFilterUtils {
  private final String ISSUER_HARNESS_STRING_CONSTANT = "Harness Inc";

  public static final String JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY = "jwtTokenPublicKeysJsonDataCache";
  public static final String JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY = "jwtTokenServiceAccountDataCache";
  public static final String JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY = "jwtTokenScimSettingsDataCache";

  @Inject private ServiceAccountService serviceAccountService;
  @Inject private SettingsService settingsService;
  @Inject
  @Named(JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY)
  private Cache<String, JwtTokenPublicKeysJsonData> jwtTokenPublicKeysJsonCache;

  @Inject
  @Named(JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY)
  private Cache<String, JwtTokenServiceAccountData> jwtTokenServiceAccountCache;

  @Inject
  @Named(JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY)
  private Cache<String, JwtTokenScimAccountSettingsData> jwtTokenScimSettingCache;

  public TokenDTO handleSCIMJwtTokenFlow(String accountIdentifier, String jwtToken) {
    List<SettingDTO> settingsResponse = getSettingListResponseByAccountForSCIMAndJWT(accountIdentifier);
    final Map<String, String> settingValuesMap =
        getScimJwtTokenSettingConfigurationValuesFromDTOList(settingsResponse, accountIdentifier);
    validateJwtTokenAndMatchClaimKeyValueInternal(accountIdentifier, jwtToken, settingValuesMap);

    ServiceAccountDTO serviceAccountDTO = getServiceAccountByIdAndAccountId(
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER),
        accountIdentifier);

    if (null != serviceAccountDTO) {
      log.info(String.format(
          "NG_SCIM_JWT: Service account details successfully fetched for account: %s, with ServiceAccount Id: %s",
          accountIdentifier, serviceAccountDTO.getIdentifier()));

      return getTokenDtoFromServiceAccountDto(serviceAccountDTO);
    } else {
      final String errorMessage =
          String.format("NG_SCIM_JWT: Error getting ServiceAccount details for account: %s, with ServiceAccount Id: %s",
              accountIdentifier,
              settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER));
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage, null, INVALID_INPUT_SET, USER);
    }
  }

  public boolean isJWTTokenType(String apiKey, String accountIdentifier) {
    final String[] splitToken = apiKey.split(apiKeyOrTokenDelimiterRegex);
    if (splitToken.length == 3
        && !(ApiKeyType.USER.getValue().equalsIgnoreCase(splitToken[0])
            || ApiKeyType.SERVICE_ACCOUNT.getValue().equalsIgnoreCase(splitToken[0]))) {
      try {
        JSONObject header =
            new JSONObject(new String(Base64.getUrlDecoder().decode(splitToken[0]), StandardCharsets.UTF_8));
        String tokenType = header.getString(HeaderParameterNames.TYPE);
        return isNotEmpty(tokenType) && tokenType.toLowerCase().contains("jwt");
      } catch (JSONException jExc) {
        logAndThrowJwtTokenExceptionWithCause(
            String.format(
                "NG_SCIM_JWT: Cannot parse and construct a valid 'header' segment from supplied JWT token for request to account: %s",
                accountIdentifier),
            INVALID_TOKEN, jExc);
      }
    }
    return false;
  }

  @VisibleForTesting
  ServiceAccountDTO getServiceAccountByIdAndAccountId(final String identifier, final String accountIdentifier) {
    final String trimmedServiceAccountId = identifier.trim();
    try {
      if (jwtTokenServiceAccountCache.containsKey(accountIdentifier)) {
        JwtTokenServiceAccountData serviceAccountData = jwtTokenServiceAccountCache.get(accountIdentifier);
        if (isNotEmpty(serviceAccountData.getServiceAccountId())
            && serviceAccountData.getServiceAccountId().equals(trimmedServiceAccountId)) {
          log.info("NG_SCIM_JWT: [CACHE_HIT] on [{}] cache, for account: {}", JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY,
              accountIdentifier);
          return serviceAccountData.getServiceAccountDTO();
        } else {
          // invalidate the cache, suggests publicKeysUrl has changed
          log.warn("NG_SCIM_JWT: [CACHE_MISS] Invalidating cache: [{}], for account key entry: {}",
              JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY, accountIdentifier);
          jwtTokenPublicKeysJsonCache.remove(accountIdentifier);
        }
      }
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache [{}] not available for read or remove operation",
          JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY, e);
    }
    return getJwtServiceAccountDtoInternal(trimmedServiceAccountId, accountIdentifier);
  }

  private void logAndThrowJwtTokenExceptionWithCause(String errorMessage, ErrorCode errorCode, Throwable th) {
    log.error(errorMessage);
    throw new InvalidRequestException(errorMessage, th, errorCode, USER);
  }

  private void validateJwtTokenAndMatchClaimKeyValueInternal(
      final String accountIdentifier, final String jwtToken, Map<String, String> settingValuesMap) {
    validateJwtTokenAndMatchClaimKeyValue(jwtToken,
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER),
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER),
        settingValuesMap.get(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER),
        accountIdentifier);
  }

  @VisibleForTesting
  void validateJwtTokenAndMatchClaimKeyValue(String jwtToken, String toMatchClaimKey, String toMatchClaimValue,
      String publicKeyUrlString, String accountIdentifier) {
    JwtConsumer jwtConsumer = getJwtConsumerFromPublicKeysUrl(accountIdentifier, publicKeyUrlString);
    try {
      // validates 'authenticity' through signature verification and get jwtTokenClaims
      JwtContext jwtContext = jwtConsumer.process(jwtToken);
      JwtClaims jwtTokenClaims = jwtContext.getJwtClaims();
      String jwtTokenIssuer = null;
      String actualClaimValueInJwt = null;
      try {
        jwtTokenIssuer = jwtTokenClaims.getIssuer();
        actualClaimValueInJwt = jwtTokenClaims.getStringClaimValue(toMatchClaimKey);
      } catch (MalformedClaimException mce) {
        logAndThrowJwtTokenExceptionWithCause(
            String.format(
                "NG_SCIM_JWT: JWT tokens payload segment is malformed as it does not contain an required claims 'iss' or '%s' in account: %s",
                toMatchClaimKey, accountIdentifier),
            INVALID_TOKEN, mce);
      }
      checkIssuerAndMatchClaimValuesInJwtToken(
          toMatchClaimValue, accountIdentifier, jwtTokenIssuer, actualClaimValueInJwt);

      log.info(String.format(
          "NG_SCIM_JWT: JWT token validated correctly, and the claims value also matched with configured account setting values in account: %s. Allowing SCIM request using externally issued JWT token",
          accountIdentifier));
    } catch (InvalidJwtException exc) {
      if (isNotEmpty(exc.getMessage())
          && exc.getMessage().contains("The JWT is no longer valid - the evaluation time")) {
        logAndThrowJwtTokenExceptionWithCause(
            String.format(
                "NG_SCIM_JWT: JWT token used for SCIM APIs requests on account: %s, has expired", accountIdentifier),
            EXPIRED_TOKEN, null);
      }
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: JWT token's signature could not be verified or required claims are malformed for SCIM API requests on account: %s",
              accountIdentifier),
          INVALID_TOKEN, exc);
    }
  }

  private void checkIssuerAndMatchClaimValuesInJwtToken(
      String toMatchClaimValue, String accountIdentifier, String jwtTokenIssuer, String actualClaimValueInJwt) {
    if (ISSUER_HARNESS_STRING_CONSTANT.equals(jwtTokenIssuer)) {
      logAndThrowJwtTokenExceptionWithCause(
          "NG_SCIM_JWT: Invalid API call: Only externally issued OAuth JWT token can be used for SCIM APIs, 'Harness Inc' issued JWT token cannot be used",
          UNEXPECTED, null);
    }
    if (!(isNotEmpty(actualClaimValueInJwt) && actualClaimValueInJwt.trim().equals(toMatchClaimValue.trim()))) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: JWT token validated correctly, but the claims value did not match configured setting value in account: %s",
              accountIdentifier),
          INVALID_INPUT_SET, null);
    }
  }

  @VisibleForTesting
  JwtConsumer getJwtConsumerFromPublicKeysUrl(String accountIdentifier, String publicKeysUrlSettingStr) {
    final String trimmedPublicKeyUrlString = publicKeysUrlSettingStr.trim();
    try {
      if (jwtTokenPublicKeysJsonCache.containsKey(accountIdentifier)) {
        JwtTokenPublicKeysJsonData jwtTokenDetailsCache = jwtTokenPublicKeysJsonCache.get(accountIdentifier);
        if (isNotEmpty(jwtTokenDetailsCache.getPublicKeysUrl())
            && jwtTokenDetailsCache.getPublicKeysUrl().equals(trimmedPublicKeyUrlString)) {
          log.info("NG_SCIM_JWT: [CACHE_HIT] on [{}] cache, for account: {}", JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY,
              accountIdentifier);
          return getJwtConsumerFromExpandedPublicKeysJson(
              accountIdentifier, jwtTokenDetailsCache.getPublicKeysDetailJson());
        } else {
          // invalidate the cache, suggests publicKeysUrl has changed
          log.warn("NG_SCIM_JWT: [CACHE_MISS] Invalidating cache: [{}], for account key entry: {}",
              JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY, accountIdentifier);
          jwtTokenPublicKeysJsonCache.remove(accountIdentifier);
        }
      }
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache [{}] not available for read or remove operation",
          JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY, e);
    }
    return getJwtConsumerInternal(trimmedPublicKeyUrlString, accountIdentifier);
  }

  @VisibleForTesting
  List<SettingDTO> getSettingListResponseByAccountForSCIMAndJWT(final String accountIdentifier) {
    try {
      if (jwtTokenScimSettingCache.containsKey(accountIdentifier)) {
        log.info("NG_SCIM_JWT: [CACHE_HIT] on [{}] cache, for account: {}", JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY,
            accountIdentifier);
        return jwtTokenScimSettingCache.get(accountIdentifier)
            .getScimSettingsValue()
            .stream()
            .map(SettingResponseDTO::getSetting)
            .collect(Collectors.toList());
      }
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache [{}] not available for read", JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY, e);
    }

    List<SettingResponseDTO> settingsResponse = settingsService.list(accountIdentifier, null, null,
        SettingCategory.SCIM, SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_GROUP_IDENTIFIER, false);

    if (isEmpty(settingsResponse)) {
      // No account settings configuration found, so cannot process request coming with JWT token
      logAndThrowJwtTokenExceptionWithCause(
          String.format("NG_SCIM_JWT: SCIM JWT token NG account settings value not configured at account [%s]",
              accountIdentifier),
          UNEXPECTED, null);
    }

    try {
      jwtTokenScimSettingCache.put(
          accountIdentifier, JwtTokenScimAccountSettingsData.builder().scimSettingsValue(settingsResponse).build());
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache add operation failed on cache [{}]", JWT_TOKEN_SCIM_SETTINGS_DATA_CACHE_KEY, e);
    }
    return settingsResponse.stream().map(SettingResponseDTO::getSetting).collect(Collectors.toList());
  }

  private Map<String, String> getScimJwtTokenSettingConfigurationValuesFromDTOList(
      List<SettingDTO> settingsResponse, final String accountIdentifier) {
    Map<String, String> settingsMap =
        settingsResponse.stream().collect(Collectors.toMap(SettingDTO::getIdentifier, SettingDTO::getValue));

    if (!(settingsMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER)
            && settingsMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER)
            && settingsMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_URL_IDENTIFIER)
            && settingsMap.containsKey(SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER))) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: Some or all values for SCIM JWT token configuration at NG account settings are not populated in account [%s]",
              accountIdentifier),
          UNEXPECTED, null);
    }

    return settingsMap;
  }

  private JwtConsumer getJwtConsumerInternal(String trimmedPublicKeyUrlString, String accountIdentifier) {
    String publicKeyDetailsJsonString = null;
    try {
      publicKeyDetailsJsonString = Http.getResponseStringFromUrl(trimmedPublicKeyUrlString, 10, 10);
    } catch (IOException ioExc) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format("NG_SCIM_JWT: Error connecting to public keys URL: %s, or URL not reachable for account %s: ",
              trimmedPublicKeyUrlString, accountIdentifier),
          INVALID_INPUT_SET, ioExc);
    }
    try {
      jwtTokenPublicKeysJsonCache.put(accountIdentifier,
          JwtTokenPublicKeysJsonData.builder()
              .publicKeysDetailJson(publicKeyDetailsJsonString)
              .publicKeysUrl(trimmedPublicKeyUrlString)
              .build());
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache add operation failed on cache [{}]", JWT_TOKEN_PUBLIC_KEYS_JSON_DATA_CACHE_KEY, e);
    }
    return getJwtConsumerFromExpandedPublicKeysJson(accountIdentifier, publicKeyDetailsJsonString);
  }

  private JwtConsumer getJwtConsumerFromExpandedPublicKeysJson(
      String accountIdentifier, String publicKeyDetailsJsonString) {
    final int clockSkewAllowedInSeconds = 30; // 30 seconds is the allowed clockSkew in flow
    JsonWebKeySet jsonWebKeySet = null;
    try {
      jsonWebKeySet = new JsonWebKeySet(publicKeyDetailsJsonString);
    } catch (JoseException jse) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: Cannot construct valid json key set from the public keys for requests to account: %s",
              accountIdentifier),
          INVALID_TOKEN, jse);
    }

    VerificationKeyResolver verificationKeyResolver = new JwksVerificationKeyResolver(jsonWebKeySet.getJsonWebKeys());

    return new JwtConsumerBuilder()
        .setRequireExpirationTime()
        .setAllowedClockSkewInSeconds(clockSkewAllowedInSeconds)
        .setSkipDefaultAudienceValidation() // skip audience check
        .setVerificationKeyResolver(verificationKeyResolver)
        .build();
  }

  private ServiceAccountDTO getJwtServiceAccountDtoInternal(final String id, final String accountId) {
    ServiceAccountDTO fetchedDto = serviceAccountService.getServiceAccountDTO(accountId, id);
    try {
      jwtTokenServiceAccountCache.put(
          accountId, JwtTokenServiceAccountData.builder().serviceAccountId(id).serviceAccountDTO(fetchedDto).build());
    } catch (Exception e) {
      // just log and proceed
      log.warn("NG_SCIM_JWT: Cache add operation failed on cache [{}]", JWT_TOKEN_SERVICE_ACCOUNT_DATA_CACHE_KEY, e);
    }
    return fetchedDto;
  }

  @VisibleForTesting
  TokenDTO getTokenDtoFromServiceAccountDto(ServiceAccountDTO serviceAccountDto) {
    final String JWT_SCIM_DUMMY_TOKEN_ID = "JWT_SCIM_DUMMY_ID_";
    return TokenDTO.builder()
        .accountIdentifier(serviceAccountDto.getAccountIdentifier())
        .identifier(JWT_SCIM_DUMMY_TOKEN_ID + serviceAccountDto.getIdentifier())
        .parentIdentifier(serviceAccountDto.getIdentifier())
        .name(serviceAccountDto.getName())
        .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
        .email(serviceAccountDto.getEmail())
        .username(serviceAccountDto.getName())
        .encodedPassword(null)
        .build();
  }
}
