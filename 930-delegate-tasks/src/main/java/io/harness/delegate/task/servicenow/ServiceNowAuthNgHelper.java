/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.adfs.AdfsConstants.ADFS_ACCESS_TOKEN_ENDPOINT;
import static io.harness.adfs.AdfsConstants.ADFS_CLIENT_ASSERTION_TYPE;
import static io.harness.adfs.AdfsConstants.ADFS_GRANT_TYPE;
import static io.harness.adfs.AdfsConstants.INVALID_ADFS_CREDENTIALS;
import static io.harness.adfs.AdfsConstants.NOT_FOUND;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.ADFS_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.util.Objects.isNull;

import io.harness.adfs.AdfsAccessTokenResponse;
import io.harness.adfs.AdfsExceptionHandler;
import io.harness.adfs.AdfsRestClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.servicenow.ServiceNowADFSDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowRefreshTokenDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AdfsAuthException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.network.Http;
import io.harness.security.ADFSAuthHelper;
import io.harness.servicenow.auth.refreshtoken.RefreshTokenAuthNgHelper;

import software.wings.delegatetasks.utils.UrlUtility;

import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Responsible for generating ADFS access token via openId protocol using x509 certificates and Pkcs8 format private key
 * <p>
 * Exact scenario : <a
 * href="https://learn.microsoft.com/en-us/windows-server/identity/ad-fs/overview/ad-fs-openid-connect-oauth-flows-scenarios#second-case-access-token-request-with-a-certificate-1">...</a>
 * <p>
 * Only modification at the time of writing this is one additional parameter resource_id is being used to indicate the
 * resource for which token is generated.
 */
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ServiceNowAuthNgHelper {
  public static final long TIME_OUT = 60;

  public static String getAuthToken(ServiceNowConnectorDTO decryptedServiceNowConnectorDTO) {
    if (isNull(decryptedServiceNowConnectorDTO)) {
      return null;
    }
    if (isNull(decryptedServiceNowConnectorDTO.getAuth())
        || isNull(decryptedServiceNowConnectorDTO.getAuth().getCredentials())) {
      // means somehow auth type is not present (not easily possible as migration done)
      return getAuthTokenUsingUserNamePassword(decryptedServiceNowConnectorDTO.getUsername(),
          decryptedServiceNowConnectorDTO.getUsernameRef(), decryptedServiceNowConnectorDTO.getPasswordRef());
    }

    ServiceNowAuthType serviceNowAuthType = decryptedServiceNowConnectorDTO.getAuth().getAuthType();
    ServiceNowAuthCredentialsDTO serviceNowAuthCredentialsDTO =
        decryptedServiceNowConnectorDTO.getAuth().getCredentials();

    if (ServiceNowAuthType.USER_PASSWORD.equals(serviceNowAuthType)) {
      ServiceNowUserNamePasswordDTO serviceNowUserNamePasswordDTO =
          (ServiceNowUserNamePasswordDTO) serviceNowAuthCredentialsDTO;
      return getAuthTokenUsingUserNamePassword(serviceNowUserNamePasswordDTO.getUsername(),
          serviceNowUserNamePasswordDTO.getUsernameRef(), serviceNowUserNamePasswordDTO.getPasswordRef());
    } else if (ServiceNowAuthType.ADFS.equals(serviceNowAuthType)) {
      ServiceNowADFSDTO serviceNowADFSDTO = (ServiceNowADFSDTO) serviceNowAuthCredentialsDTO;
      return getAuthTokenUsingAdfs(serviceNowADFSDTO);
    } else if (ServiceNowAuthType.REFRESH_TOKEN.equals(serviceNowAuthType)) {
      ServiceNowRefreshTokenDTO serviceNowRefreshTokenDTO = (ServiceNowRefreshTokenDTO) serviceNowAuthCredentialsDTO;
      return getAuthTokenUsingRefreshToken(serviceNowRefreshTokenDTO);
    } else {
      throw new InvalidRequestException(
          String.format("Unsupported auth type in servicenow connector: %s", serviceNowAuthType));
    }
  }

  private static String getAuthTokenUsingUserNamePassword(
      String userName, SecretRefData userNameRef, SecretRefData passwordRef) {
    String finalUserName =
        FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(userName, userNameRef);
    String password = new String(passwordRef.getDecryptedValue());
    return Credentials.basic(finalUserName, password);
  }

  /**
   * Responsible for generating ADFS access token via openId protocol using x509 certificates and Pkcs8 format private
   * key <p> Exact scenario : <a
   * href="https://learn.microsoft.com/en-us/windows-server/identity/ad-fs/overview/ad-fs-openid-connect-oauth-flows-scenarios#second-case-access-token-request-with-a-certificate-1">...</a>
   * <p>
   * Only modification at the time of writing this is one additional parameter resource_id is being used to indicate the
   * resource for which token is generated.
   */
  private static String getAuthTokenUsingAdfs(ServiceNowADFSDTO serviceNowADFSDTO) {
    try {
      String clientId = new String(serviceNowADFSDTO.getClientIdRef().getDecryptedValue());
      String jwtSignedRequest = ADFSAuthHelper.generateJwtSignedRequestWithCertificate(
          new String(serviceNowADFSDTO.getCertificateRef().getDecryptedValue()),
          new String(serviceNowADFSDTO.getPrivateKeyRef().getDecryptedValue()), clientId,
          UrlUtility.appendPathToBaseUrl(serviceNowADFSDTO.getAdfsUrl(), ADFS_ACCESS_TOKEN_ENDPOINT));
      AdfsRestClient adfsRestClient = getAdfsRestClient(serviceNowADFSDTO.getAdfsUrl());

      final Call<AdfsAccessTokenResponse> request = adfsRestClient.getAccessToken(clientId, ADFS_CLIENT_ASSERTION_TYPE,
          jwtSignedRequest, ADFS_GRANT_TYPE, new String(serviceNowADFSDTO.getResourceIdRef().getDecryptedValue()));

      Response<AdfsAccessTokenResponse> response = request.execute();
      log.info("Response received from adfs: {}", response);
      AdfsExceptionHandler.handleResponse(response, "Failed to get access token from ADFS");
      // body() is validated to be not null in above method
      return response.body().getAuthToken();
    } catch (AdfsAuthException adfsEx) {
      AdfsAuthException sanitizedException = ExceptionMessageSanitizer.sanitizeException(adfsEx);
      log.warn("Failed to get access token from ADFS: {}", sanitizedException.getMessage());
      if (sanitizedException.getMessage().equals(INVALID_ADFS_CREDENTIALS)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the ADFS credentials are correct and client have necessary permissions to access the ServiceNow resource",
            "The credentials provided are invalid or client doesn't have necessary permissions to access the ServiceNow resource",
            sanitizedException);
      } else if (sanitizedException.getMessage().equals(NOT_FOUND)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the Adfs url is correct and accessible from delegate", "Not able to access the given Adfs url",
            sanitizedException);
      } else {
        throw wrapInNestedException(sanitizedException);
      }
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.warn("Failed to get access token from ADFS. {}", sanitizedException.getMessage());
      throw wrapInNestedException(
          new AdfsAuthException(ExceptionUtils.getMessage(sanitizedException), ADFS_ERROR, USER, sanitizedException));
    }
  }

  /**
   * Responsible for generating access token via oauth refresh token grant type protocol
   *
   * Protocol description : https://www.oauth.com/oauth2-servers/access-tokens/refreshing-access-tokens/
   *
   * Tested using ServiceNow and Okta as authentication severs.
   */
  private static String getAuthTokenUsingRefreshToken(ServiceNowRefreshTokenDTO serviceNowRefreshTokenDTO) {
    String refreshToken = new String(serviceNowRefreshTokenDTO.getRefreshTokenRef().getDecryptedValue());
    String clientId = new String(serviceNowRefreshTokenDTO.getClientIdRef().getDecryptedValue());
    String clientSecret = isNull(serviceNowRefreshTokenDTO.getClientSecretRef())
            || serviceNowRefreshTokenDTO.getClientSecretRef().isNull()
        ? null
        : new String(serviceNowRefreshTokenDTO.getClientSecretRef().getDecryptedValue());
    String scope = serviceNowRefreshTokenDTO.getScope();
    String tokenUrl = serviceNowRefreshTokenDTO.getTokenUrl();

    return RefreshTokenAuthNgHelper.getAuthToken(refreshToken, clientId, clientSecret, scope, tokenUrl, true);
  }

  private static AdfsRestClient getAdfsRestClient(String url) {
    Retrofit retrofit = new Retrofit.Builder()
                            .client(getHttpClient(url))
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AdfsRestClient.class);
  }

  @NotNull
  private OkHttpClient getHttpClient(String url) {
    return getOkHttpClientBuilder()
        .connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        .readTimeout(TIME_OUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(url))
        .build();
  }

  @NotNull
  private static WingsException wrapInNestedException(AdfsAuthException ex) {
    return NestedExceptionUtils.hintWithExplanationException(
        "Check if the ADFS url and credentials are correct and accessible from delegate",
        "Not able to access the given ADFS url with the credentials or not able to get access token from ADFS",
        new GeneralException(ExceptionUtils.getMessage(ex), ex, USER));
  }
}
