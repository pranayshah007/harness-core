/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_INPUT_SET;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.UNEXPECTED;
import static io.harness.exception.WingsException.USER;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;
import static software.wings.app.ManagerCacheRegistrar.TRIAL_EMAIL_CACHE;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.annotations.ScimAPI;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccountclient.remote.ServiceAccountClient;
import io.harness.token.remote.TokenClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.cache.Cache;
import javax.annotation.Priority;
import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class NextGenAuthenticationFilter extends JWTAuthenticationFilter {
  public static final String X_API_KEY = "X-Api-Key";
  public static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String delimiter = "\\.";
  private static final String ISSUER_HARNESS_CONST = "Harness Inc";

  private TokenClient tokenClient;
  private NGSettingsClient settingsClient;
  private ServiceAccountClient serviceAccountClient;
  @Context @Setter @VisibleForTesting private ResourceInfo resourceInfo;
  @Inject
  @Named(TRIAL_EMAIL_CACHE) private Cache<String,

  public NextGenAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      @Named("PRIVILEGED") TokenClient tokenClient, @Named("PRIVILEGED") NGSettingsClient settingsClient,
      @Named("PRIVILEGED") ServiceAccountClient serviceAccountClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.tokenClient = tokenClient;
    this.settingsClient = settingsClient;
    this.serviceAccountClient = serviceAccountClient;
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
      validateApiKey(accountIdentifier, apiKeyOptional.get(), isScimCall);
    } else {
      super.filter(containerRequestContext);
    }
  }

  private boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  private void validateApiKey(String accountIdentifier, String apiKey, boolean isScimCall) {
    String[] splitToken = apiKey.split(delimiter);
    checkIfTokenLengthMatches(splitToken);
    if (EmptyPredicate.isNotEmpty(splitToken)) {
      String tokenId = null;

      if (isNewApiKeyToken(splitToken)) {
        tokenId = splitToken[2];
      } else if (splitToken.length == 3) {
        boolean isJwtTokenType = isJWTTokenType(splitToken, accountIdentifier);
        if (isJwtTokenType) {
          // tokenId = null;
          if (isScimCall) {
            handleSCIMJwtTokenFlow(accountIdentifier, apiKey);
          } else {
            logAndThrowTokenException(
                "NG_SCIM_JWT: Invalid API call: Externally issued OAuth JWT token can be only used for SCIM APIs",
                UNEXPECTED);
          }
        } else {
          tokenId = splitToken[1];
        }
      }

      if (isNotEmpty(tokenId)) {
        handleAPIKeyTokenFlow(accountIdentifier, splitToken, tokenId);
      }
    } else {
      logAndThrowTokenException("Invalid API token: Token is Empty", INVALID_TOKEN);
    }
  }

  private void handleAPIKeyTokenFlow(String accountIdentifier, String[] splitToken, String apiTokenId) {
    TokenDTO tokenDTO = NGRestUtils.getResponse(tokenClient.getToken(apiTokenId));
    if (tokenDTO != null) {
      checkIfAccountIdMatches(accountIdentifier, tokenDTO, apiTokenId);
      checkIfAccountIdInTokenMatches(splitToken, tokenDTO, apiTokenId);
      checkIfPrefixMatches(splitToken, tokenDTO, apiTokenId);
      checkIFRawPasswordMatches(splitToken, apiTokenId, tokenDTO);
      checkIfApiKeyHasExpired(apiTokenId, tokenDTO);
      Principal principal = getPrincipal(tokenDTO);
      io.harness.security.SecurityContextBuilder.setContext(principal);
      SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    } else {
      logAndThrowTokenException(String.format("Invalid API token %s: Token not found", apiTokenId), INVALID_TOKEN);
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

  private boolean isJWTTokenType(String[] splitToken, String accountIdentifier) {
    if (splitToken.length == 3) {
      if (!("pat".equalsIgnoreCase(splitToken[0]) || "sat".equalsIgnoreCase(splitToken[0]))) {
        try {
          JSONObject header =
              new JSONObject(new String(Base64.getUrlDecoder().decode(splitToken[0]), StandardCharsets.UTF_8));
          // String tokenType = header.getString("typ");
          // return isNotEmpty(tokenType) && tokenType.toLowerCase().contains("jwt");
          return true;
        } catch (JSONException err) {
          logAndThrowTokenException(
              String.format("NG_SCIM_JWT: Cannot construct valid json header for jwt token, on requests to account: %s",
                  accountIdentifier),
              INVALID_TOKEN);
        }
      }
    }
    return false;
  }

  private Principal getPrincipalFromServiceAccountDto(ServiceAccountDTO serviceAccountDto) {
    return new ServiceAccountPrincipal(
        serviceAccountDto.getIdentifier(), serviceAccountDto.getEmail(), serviceAccountDto.getEmail(), serviceAccountDto.getAccountIdentifier());
  }

  private void validateJwtToken(
      String jwtToken, String toMatchClaimKey, String toMatchClaimValue, String publicKeys, String accountIdentifier) {
    final int secondsOfAllowedClockSkew = 30; // 30 seconds is the allowed clockSkew in flow
    JsonWebKeySet jsonWebKeySet = null;

    try {
      jsonWebKeySet = new JsonWebKeySet(publicKeys);
    } catch (JoseException jse) {
      logAndThrowTokenException(
          String.format(
              "NG_SCIM_JWT: Cannot construct valid json key set from the public keys for requests to account: %s",
              accountIdentifier),
          INVALID_TOKEN);
    }

    JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                                  .setSkipAllValidators()
                                  .setDisableRequireSignature()
                                  .setSkipSignatureVerification()
                                  .build();

    try {
      JwtContext jwtContext = jwtConsumer.process(jwtToken);
      JwtClaims jwtTokenClaims = jwtContext.getJwtClaims();

      // validate expiry
      if (jwtTokenClaims.getExpirationTime() != null
          && (NumericDate.now().getValue() - secondsOfAllowedClockSkew)
              >= jwtTokenClaims.getExpirationTime().getValue()) {
        // token expired
        logAndThrowTokenException(
            String.format("NG_SCIM_JWT: JWT Token used for SCIM APIs in account: %s has expired", accountIdentifier),
            INVALID_TOKEN);
      }

      if (jsonWebKeySet != null) {
        VerificationKeyResolver verificationKeyResolver =
            new JwksVerificationKeyResolver(jsonWebKeySet.getJsonWebKeys());
        jwtConsumer = new JwtConsumerBuilder()
                          .setRequireExpirationTime()
                          .setSkipDefaultAudienceValidation() // skipping audience check
                          .setVerificationKeyResolver(verificationKeyResolver)
                          .build();

        // validates 'authenticity' through signature verification and get jwtTokenClaims
        jwtConsumer.processContext(jwtContext);
        jwtTokenClaims = jwtContext.getJwtClaims();
        String jwtTokenIssuer = jwtTokenClaims.getIssuer();

        if (ISSUER_HARNESS_CONST.equals(jwtTokenIssuer)) {
          logAndThrowTokenException(
              "NG_SCIM_JWT: Invalid API call: Externally issued OAuth JWT token can be only used for SCIM APIs, 'Harness Inc' issued JWT token cannot",
              UNEXPECTED);
        }

        String actualClaimValueInJwt = jwtTokenClaims.getStringClaimValue(toMatchClaimKey);

        if (!(isNotEmpty(actualClaimValueInJwt) && actualClaimValueInJwt.trim().equals(toMatchClaimValue.trim()))) {
          final String errorMessage = String.format(
              "NG_SCIM_JWT: JWT validated correctly, but the claims value did not match configured settings value in account: %s",
              accountIdentifier);
          log.warn(errorMessage);
          throw new InvalidRequestException(errorMessage, INVALID_INPUT_SET, USER);
        }
        log.info(String.format(
            "NG_SCIM_JWT: JWT validated correctly, and the claims value also matched with configured account settings value in account: %s. Proceeding with SCIM request using externally issued  JWT token.",
            accountIdentifier));
      }
    } catch (InvalidJwtException | MalformedClaimException e) {
      logAndThrowTokenException(
          String.format(
              "NG_SCIM_JWT: JWT token's signature couldn't be verified or required claims are malformed for SCIM requests on account: %s",
              accountIdentifier),
          INVALID_TOKEN);
    }
  }

  private void handleSCIMJwtTokenFlow(String accountIdentifier, String jwtToken) {
    List<SettingResponseDTO> settingsResponse = NGRestUtils.getResponse(settingsClient.listSettings(accountIdentifier,
        null, null, SettingCategory.SCIM, SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_GROUP_IDENTIFIER));

    if (isEmpty(settingsResponse)) {
      // No account settings configuration found so cannot process request coming with OAuth JWT token
      logAndThrowTokenException(
          String.format(
              "NG_SCIM_JWT: SCIM JWT token account settings not configured for account [%s]", accountIdentifier),
          INVALID_REQUEST);
      return;
    }

    String keySettingStr, valueSettingStr, publicKeysUrlSettingStr, serviceAccountSettingStr;
    keySettingStr = valueSettingStr = publicKeysUrlSettingStr = serviceAccountSettingStr = null;

    for (SettingResponseDTO settingResponseDto : settingsResponse) {
      if (settingResponseDto != null && settingResponseDto.getSetting() != null) {
        if (SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_KEY_IDENTIFIER.equals(
                settingResponseDto.getSetting().getIdentifier())) {
          keySettingStr = settingResponseDto.getSetting().getValue();
        } else if (SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_VALUE_IDENTIFIER.equals(
                       settingResponseDto.getSetting().getIdentifier())) {
          valueSettingStr = settingResponseDto.getSetting().getValue();
        } else if (SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_PUBLIC_KEY_IDENTIFIER.equals(
                       settingResponseDto.getSetting().getIdentifier())) {
          publicKeysUrlSettingStr = settingResponseDto.getSetting().getValue();
        } else if (SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_SERVICE_PRINCIPAL_IDENTIFIER.equals(
                       settingResponseDto.getSetting().getIdentifier())) {
          serviceAccountSettingStr = settingResponseDto.getSetting().getValue();
        }
      }
    }

    if (isEmpty(keySettingStr) || isEmpty(valueSettingStr) || isEmpty(publicKeysUrlSettingStr)
        || isEmpty(serviceAccountSettingStr)) {
      logAndThrowTokenException(
          String.format(
              "NG_SCIM_JWT: Some or all values for SCIM JWT token configuration at account settings are not populated in account [%s]",
              accountIdentifier),
          UNEXPECTED);
    } else {
      Request httpGetRequest = new Request.Builder().url(publicKeysUrlSettingStr).method("GET", null).build();
      OkHttpClient client = new OkHttpClient();
      try (Response response = client.newCall(httpGetRequest).execute()) {
        if (response.isSuccessful()) {
          ResponseBody responseBody = response.body();
          if (responseBody != null) {
            String publicCertsJsonString = responseBody.string();
            validateJwtToken(jwtToken, keySettingStr, valueSettingStr, publicCertsJsonString, accountIdentifier);
            ServiceAccountDTO serviceAccountDTO = NGRestUtils.getResponse(
                serviceAccountClient.getServiceAccount(serviceAccountSettingStr, accountIdentifier));
            Principal servicePrincipal = getPrincipalFromServiceAccountDto(serviceAccountDTO);
            io.harness.security.SecurityContextBuilder.setContext(servicePrincipal);
            SourcePrincipalContextBuilder.setSourcePrincipal(servicePrincipal);
          }
        } else {
          logAndThrowTokenException(
              String.format("NG_SCIM_JWT: Invalid public key URL: %s, for requests in account %s: ",
                  publicKeysUrlSettingStr, accountIdentifier),
              INVALID_INPUT_SET);
        }
      } catch (SSLHandshakeException sslExc) {
        logAndThrowTokenException(
            String.format(
                "NG_SCIM_JWT: Certificate chain not trusted for public keys URL: %s host, configured at settings in account: %s",
                publicKeysUrlSettingStr, accountIdentifier),
            INVALID_INPUT_SET);
      } catch (IOException e) {
        logAndThrowTokenException(
            String.format("NG_SCIM_JWT: Error fetching public certificate from public keys URL: %s, in account %s: ",
                publicKeysUrlSettingStr, accountIdentifier),
            INVALID_INPUT_SET);
      }
    }
  }
}
