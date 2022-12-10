/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.util;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_INPUT_SET;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.UNEXPECTED;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.remote.TokenClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLHandshakeException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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

@UtilityClass
@Slf4j
@OwnedBy(PL)
public class JWTTokenFlowAuthFilterUtils {
  private final String ISSUER_HARNESS_STRING_CONSTANT = "Harness Inc";
  private final OkHttpClient client;

  static {
    client = new OkHttpClient();
  }

  public ServiceAccountDTO getServiceAccountByIdAndAccountId(
      final String identifier, final String accountIdentifier, TokenClient serviceAccountClient) {
    ServiceAccountDTO serviceAccountDTO = null;
    try {
      serviceAccountDTO =
          NGRestUtils.getResponse(serviceAccountClient.getServiceAccount(identifier, accountIdentifier));
    } catch (Exception exc) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format("NG_SCIM_JWT: Error getting ServiceAccount details for account: %s, with ServiceAccount Id: %s",
              accountIdentifier, identifier),
          INVALID_INPUT_SET, exc);
    }
    return serviceAccountDTO;
  }

  public void logAndThrowJwtTokenExceptionWithCause(String errorMessage, ErrorCode errorCode, Throwable th) {
    log.error(errorMessage);
    throw new InvalidRequestException(errorMessage, th, errorCode, USER);
  }

  public void validateJwtTokenAndMatchClaimKeyValue(
      String jwtToken, String toMatchClaimKey, String toMatchClaimValue, String publicKeys, String accountIdentifier) {
    final int clockSkewAllowedInSeconds = 30; // 30 seconds is the allowed clockSkew in flow
    JsonWebKeySet jsonWebKeySet = null;

    try {
      jsonWebKeySet = new JsonWebKeySet(publicKeys);
    } catch (JoseException jse) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: Cannot construct valid json key set from the public keys for requests to account: %s",
              accountIdentifier),
          INVALID_TOKEN, jse);
    }

    if (jsonWebKeySet != null) {
      try {
        VerificationKeyResolver verificationKeyResolver =
            new JwksVerificationKeyResolver(jsonWebKeySet.getJsonWebKeys());

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                                      .setRequireExpirationTime()
                                      .setAllowedClockSkewInSeconds(clockSkewAllowedInSeconds)
                                      .setSkipDefaultAudienceValidation() // skip audience check
                                      .setVerificationKeyResolver(verificationKeyResolver)
                                      .build();

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

        log.info(String.format(
            "NG_SCIM_JWT: JWT token validated correctly, and the claims value also matched with configured account setting values in account: %s. Allowing SCIM request using externally issued JWT token",
            accountIdentifier));
      } catch (InvalidJwtException exc) {
        if (exc.getMessage() != null && exc.getMessage().contains("The JWT is no longer valid - the evaluation time")) {
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
  }

  public boolean isJWTTokenType(String[] splitToken, String accountIdentifier) {
    if (splitToken.length == 3) {
      if (!(ApiKeyType.USER.getValue().equalsIgnoreCase(splitToken[0])
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
    }
    return false;
  }

  public String getPublicKeysJsonStringFromUrl(String accountIdentifier, String publicKeysUrlSettingStr) {
    Request httpGetRequest = new Request.Builder().url(publicKeysUrlSettingStr).method("GET", null).build();
    String publicKeyDetailsJsonString = null;

    try (Response response = client.newCall(httpGetRequest).execute()) {
      if (response.isSuccessful()) {
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
          publicKeyDetailsJsonString = responseBody.string();
        }
      } else {
        logAndThrowJwtTokenExceptionWithCause(
            String.format(
                "NG_SCIM_JWT: Error fetching public key json details from publicKeys URL: %s, in account %s: ",
                publicKeysUrlSettingStr, accountIdentifier),
            UNEXPECTED, null);
      }
    } catch (SSLHandshakeException sslExc) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: Certificate chain not trusted for public keys URL: %s host, configured at settings in account: %s",
              publicKeysUrlSettingStr, accountIdentifier),
          INVALID_INPUT_SET, null);
    } catch (IOException ioExc) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format("NG_SCIM_JWT: Error connecting to public keys URL: %s, or URL not reachable for account %s: ",
              publicKeysUrlSettingStr, accountIdentifier),
          INVALID_INPUT_SET, ioExc);
    }
    return publicKeyDetailsJsonString;
  }

  public List<SettingResponseDTO> getSettingListResponseByAccountForSCIMAndJWT(
      final String accountIdentifier, TokenClient settingsClient) {
    List<SettingResponseDTO> settingsResponse = null;
    try {
      settingsResponse = NGRestUtils.getResponse(settingsClient.listSettings(accountIdentifier, null, null,
          SettingCategory.SCIM, SettingIdentifiers.SCIM_JWT_TOKEN_CONFIGURATION_GROUP_IDENTIFIER));
    } catch (Exception exc) {
      logAndThrowJwtTokenExceptionWithCause(
          String.format(
              "NG_SCIM_JWT: Error getting setting list response for SCIM category, and JWT configuration group on account: %s",
              accountIdentifier),
          INVALID_INPUT_SET, exc);
    }

    if (isEmpty(settingsResponse)) {
      // No account settings configuration found, so cannot process request coming with JWT token
      logAndThrowJwtTokenExceptionWithCause(
          String.format("NG_SCIM_JWT: SCIM JWT token NG account settings value not configured at account [%s]",
              accountIdentifier),
          UNEXPECTED, null);
    }
    return settingsResponse;
  }

  public Map<String, String> getScimJwtTokenSettingConfigurationValuesFromDTOList(
      List<SettingResponseDTO> settingsResponse, final String accountIdentifier) {
    List<SettingDTO> dtos = settingsResponse.stream().map(SettingResponseDTO::getSetting).collect(Collectors.toList());
    Map<String, String> settingsMap =
        dtos.stream().collect(Collectors.toMap(SettingDTO::getIdentifier, SettingDTO::getValue));

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
}
