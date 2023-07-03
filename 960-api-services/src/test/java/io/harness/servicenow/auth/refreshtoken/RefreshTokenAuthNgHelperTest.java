/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.REFRESH_TOKEN_GRANT_TYPE;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.REFRESH_TOKEN_ERROR_PREFIX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.ServiceNowOIDCException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class RefreshTokenAuthNgHelperTest extends CategoryTest {
  private static final String SERVICENOW_URL = "https://test.service-now.com/";
  private static final String CLIENT_ID = "clientID";
  private static final String BEARER_TOKEN = "Bearer testtoken&&%$%^%$testtoken";
  private static final String CLIENT_SECRET = "clientSecret";
  private static final String TOKEN_URL = "https://dev.okta.com/oauth/v1/token/";
  private static final String SERVICENOW_TOKEN_URL = "https://test.service-now.com/oauth_token.do";
  private static final String SERVICENOW_INVALID_TOKEN_URL = "https://test.service-now.com/oauth_token.do?qwerty";
  private static final String SCOPE = "openid email";
  private static final String REFRESH_TOKEN = "##$$refreshToken$$##";

  @Mock Call<AccessTokenResponse> mockCall;
  @Mock Response<AccessTokenResponse> mockResponse;
  @Mock RefreshTokenRestClient refreshTokenRestClient;
  @Mock ResponseBody responseBody;

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testReadAccessTokenResponse() {
    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":1800,\"scope\":\"openid\"}";
    AccessTokenResponse accessTokenResponse = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    assertThat(accessTokenResponse.getAuthToken()).isEqualTo(BEARER_TOKEN);
    assertThat(accessTokenResponse.getExpiresIn()).isEqualTo(1800);
    assertThat(accessTokenResponse.getScope()).isEqualTo("openid");

    jsonResponse =
        "{\"id_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":1800,\"scope\":\"openid\"}";
    accessTokenResponse = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    assertThat(accessTokenResponse.getAuthToken()).isEqualTo(BEARER_TOKEN);
    assertThat(accessTokenResponse.getExpiresIn()).isEqualTo(1800);
    assertThat(accessTokenResponse.getScope()).isEqualTo("openid");

    String jsonResponse1 = "{\"token_type\":\"bearer\",\"expires_in\":1800,\"scope\":\"openid\"}";
    assertThatThrownBy(
        () -> { AccessTokenResponse access = JsonUtils.asObject(jsonResponse1, new TypeReference<>() {}); })
        .getCause()
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format(
            "%s: %s", REFRESH_TOKEN_ERROR_PREFIX, "response doesn't have bearer access token or id token"));

    String jsonResponse2 =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"not_bearer\",\"expires_in\":1800,\"scope\":\"openid\"}";
    assertThatThrownBy(
        () -> { AccessTokenResponse access = JsonUtils.asObject(jsonResponse2, new TypeReference<>() {}); })
        .getCause()
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format(
            "%s: %s", REFRESH_TOKEN_ERROR_PREFIX, "response doesn't have bearer access token or id token"));

    String jsonResponse3 =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"scope\":\"openid\"}";
    assertThatThrownBy(
        () -> { AccessTokenResponse access = JsonUtils.asObject(jsonResponse3, new TypeReference<>() {}); })
        .getCause()
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX,
            "response doesn't have a positive \"expires_in\" for the access or id token"));

    String jsonResponse4 =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":-1800,\"scope\":\"openid\"}";
    assertThatThrownBy(
        () -> { AccessTokenResponse access = JsonUtils.asObject(jsonResponse4, new TypeReference<>() {}); })
        .getCause()
        .isInstanceOf(ServiceNowOIDCException.class)
        .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX,
            "response doesn't have a positive \"expires_in\" for the access or id token"));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testFormatTokenUrl() {
    assertThat(RefreshTokenAuthNgHelper.formatTokenUrl(TOKEN_URL)).isEqualTo(TOKEN_URL);
    assertThat(RefreshTokenAuthNgHelper.formatTokenUrl(TOKEN_URL.substring(0, TOKEN_URL.length() - 1)))
        .isEqualTo(TOKEN_URL);

    assertThat(RefreshTokenAuthNgHelper.formatServiceNowTokenUrl(SERVICENOW_TOKEN_URL)).isEqualTo(SERVICENOW_URL);
    assertThat(RefreshTokenAuthNgHelper.formatServiceNowTokenUrl(SERVICENOW_TOKEN_URL + "/qwerty"))
        .isEqualTo(SERVICENOW_URL);
    assertThat(RefreshTokenAuthNgHelper.formatServiceNowTokenUrl(SERVICENOW_TOKEN_URL + "?qwerty"))
        .isEqualTo(SERVICENOW_URL);
    assertThat(RefreshTokenAuthNgHelper.formatServiceNowTokenUrl(TOKEN_URL)).isEqualTo(TOKEN_URL);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithoutServiceNowTokenUrl() throws IOException, InterruptedException {
    when(refreshTokenRestClient.getAccessToken(
             REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":30}";
    AccessTokenResponse accessToken = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AccessTokenResponse> accessTokenResponse = Response.success(accessToken);
    when(mockCall.execute()).thenReturn(accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      // start by adding entry to cache
      String token =
          RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, false);
      verify(refreshTokenRestClient, times(1))
          .getAccessToken(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // using the saved entry in cache
      token = RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, true);
      verifyNoMoreInteractions(refreshTokenRestClient);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // invalidating already saved cache
      token = RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, false);
      verify(refreshTokenRestClient, times(2))
          .getAccessToken(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL);
      assertThat(token).isEqualTo(BEARER_TOKEN);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithoutServiceNowTokenUrlAfterTTLExpired() throws IOException, InterruptedException {
    when(refreshTokenRestClient.getAccessToken(
             REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":30}";
    AccessTokenResponse accessToken = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AccessTokenResponse> accessTokenResponse = Response.success(accessToken);
    when(mockCall.execute()).thenReturn(accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      // without using cache
      String token =
          RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, false);
      verify(refreshTokenRestClient, times(1))
          .getAccessToken(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // using the saved entry in cache after TTL for the access token
      Thread.sleep(30000);
      token = RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, true);
      verify(refreshTokenRestClient, times(2))
          .getAccessToken(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE, TOKEN_URL);
      assertThat(token).isEqualTo(BEARER_TOKEN);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithServiceNowTokenUrl() throws IOException {
    when(refreshTokenRestClient.getAccessTokenFromServiceNow(
             REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":30}";
    AccessTokenResponse accessToken = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AccessTokenResponse> accessTokenResponse = Response.success(accessToken);
    when(mockCall.execute()).thenReturn(accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      // adding entry in cache
      String token = RefreshTokenAuthNgHelper.getAuthToken(
          REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
      verify(refreshTokenRestClient, times(1))
          .getAccessTokenFromServiceNow(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // using the saved entry in cache
      token = RefreshTokenAuthNgHelper.getAuthToken(
          REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, true);
      verifyNoMoreInteractions(refreshTokenRestClient);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // invalidating already saved cache
      token = RefreshTokenAuthNgHelper.getAuthToken(
          REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
      verify(refreshTokenRestClient, times(2))
          .getAccessTokenFromServiceNow(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE);
      assertThat(token).isEqualTo(BEARER_TOKEN);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithServiceNowTokenUrlAfterTTLExpired() throws IOException, InterruptedException {
    when(refreshTokenRestClient.getAccessTokenFromServiceNow(
             REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":30}";
    AccessTokenResponse accessToken = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AccessTokenResponse> accessTokenResponse = Response.success(accessToken);
    when(mockCall.execute()).thenReturn(accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      // without using cache
      String token = RefreshTokenAuthNgHelper.getAuthToken(
          REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
      verify(refreshTokenRestClient, times(1))
          .getAccessTokenFromServiceNow(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE);
      assertThat(token).isEqualTo(BEARER_TOKEN);

      // using the saved entry in cache after TTL for the access token
      Thread.sleep(30000);
      token = RefreshTokenAuthNgHelper.getAuthToken(
          REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, true);
      verify(refreshTokenRestClient, times(2))
          .getAccessTokenFromServiceNow(REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE);
      assertThat(token).isEqualTo(BEARER_TOKEN);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithServiceNowTokenUrlFailure() throws IOException {
    when(refreshTokenRestClient.getAccessTokenFromServiceNow(
             anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);
    when(refreshTokenRestClient.getAccessToken(
             anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      when(mockResponse.code()).thenReturn(401);
      try {
        RefreshTokenAuthNgHelper.getAuthToken(
            REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
        fail("Hint Exception expected");
      } catch (HintException ex) {
        assertThat(ex.getMessage())
            .isEqualTo(
                "Check if the client credentials are correct, refresh token is valid and client have necessary permissions to access the ServiceNow resource");
      }

      when(mockResponse.code()).thenReturn(404);
      try {
        RefreshTokenAuthNgHelper.getAuthToken(
            REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
        fail("Hint Exception expected");
      } catch (HintException ex) {
        assertThat(ex.getMessage()).isEqualTo("Check if the token url is correct and accessible from delegate");
      }

      when(mockResponse.code()).thenReturn(400);
      when(mockResponse.errorBody()).thenReturn(responseBody);
      when(responseBody.string()).thenReturn("random error");
      try {
        RefreshTokenAuthNgHelper.getAuthToken(
            REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_TOKEN_URL, false);
        fail("Hint Exception expected");
      } catch (HintException ex) {
        assertThat(ex.getMessage())
            .isEqualTo(
                "Check if the token url is accessible from delegate, Refresh Token grant credentials are correct and refresh token is not expired or revoked");
      }
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWithInvalidServiceNowTokenUrlGivingSuccessfulResponse() throws IOException {
    when(refreshTokenRestClient.getAccessTokenFromServiceNow(
             REFRESH_TOKEN_GRANT_TYPE, CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, SCOPE))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":30}";
    AccessTokenResponse accessToken = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AccessTokenResponse> accessTokenResponse = Response.success(accessToken);
    when(mockCall.execute()).thenThrow(mock(JsonParseException.class));
    try (MockedConstruction<Retrofit> ignored = mockConstruction(Retrofit.class,
             (mock, context) -> when(mock.create(RefreshTokenRestClient.class)).thenReturn(refreshTokenRestClient))) {
      assertThatThrownBy(()
                             -> RefreshTokenAuthNgHelper.getAuthToken(
                                 REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, SERVICENOW_INVALID_TOKEN_URL, false))
          .isInstanceOf(HintException.class)
          .getCause()
          .getCause()
          .getCause()
          .hasMessage(String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX,
              "Failed to parse access token response using refresh token grant type"));
    }
  }
}