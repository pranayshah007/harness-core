/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.adfs.AdfsConstants.ADFS_ACCESS_TOKEN_ENDPOINT;
import static io.harness.adfs.AdfsConstants.ADFS_CLIENT_ASSERTION_TYPE;
import static io.harness.adfs.AdfsConstants.ADFS_GRANT_TYPE;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.ADFS_ERROR;
import static io.harness.eraro.ErrorCode.SERVICENOW_REFRESH_TOKEN_ERROR;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.adfs.AdfsAccessTokenResponse;
import io.harness.adfs.AdfsRestClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.servicenow.ServiceNowADFSDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowRefreshTokenDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.AdfsAuthException;
import io.harness.exception.HintException;
import io.harness.exception.ServiceNowOIDCException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.security.ADFSAuthHelper;
import io.harness.serializer.JsonUtils;
import io.harness.servicenow.auth.refreshtoken.RefreshTokenAuthNgHelper;

import software.wings.delegatetasks.utils.UrlUtility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ServiceNowAuthNgHelperTest extends CategoryTest {
  private static final String SERVICENOW_URL = "https://test.service-now.com/";
  private static final String USERNAME = "username";
  private static final String PEM_KEY_VALID = loadResource("/servicenow/rsa-key-valid.pem");
  private static final String PEM_CERT_VALID = loadResource("/servicenow/x509-cert-valid.pem");
  private static final String CLIENT_ID = "clientID";
  private static final String ADFS_URL = "https://adfs.test.com";
  private static final String RESOURCE_ID = "https://resource.of.servicenow.in.adfs.com";
  private static final String BEARER_TOKEN = "Bearer testtoken&&%$%^%$testtoken";
  private static final String CLIENT_SECRET = "clientSecret";
  private static final String TOKEN_URL = "https://dev.okta.com/oauth/v1/token/";
  private static final String SCOPE = "openid email";
  private static final String REFRESH_TOKEN = "##$$refreshToken$$##";

  private static final String JWT_TOKEN = "test jwt token";
  private static String loadResource(String resourcePath) {
    try {
      return Resources.toString(ServiceNowAuthNgHelperTest.class.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "NOT FOUND";
    }
  }
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenDTONull() {
    // deprecated case, very unlikely to occur
    assertThat(ServiceNowAuthNgHelper.getAuthToken(null)).isNull();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenCredentialsAtBaseLevel() {
    // deprecated case, very unlikely to occur
    // username
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .serviceNowUrl(SERVICENOW_URL)
            .username(USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    String token = ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
    assertThat(token).isEqualTo(Credentials.basic(USERNAME, "34f51"));
    // usernameRef
    serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .serviceNowUrl(SERVICENOW_URL)
            .usernameRef(SecretRefData.builder().decryptedValue(new char[] {'U', 'S', 'E', 'R', '1'}).build())
            .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
            .build();
    token = ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
    assertThat(token).isEqualTo(Credentials.basic("USER1", "34f51"));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenUserNamePwd() {
    // username
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(ServiceNowAuthenticationDTO.builder()
                      .authType(ServiceNowAuthType.USER_PASSWORD)
                      .credentials(
                          ServiceNowUserNamePasswordDTO.builder()
                              .username(USERNAME)
                              .passwordRef(
                                  SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
                              .build())
                      .build())
            .build();

    String token = ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
    assertThat(token).isEqualTo(Credentials.basic(USERNAME, "34f51"));
    // usernameRef
    serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(ServiceNowAuthenticationDTO.builder()
                      .authType(ServiceNowAuthType.USER_PASSWORD)
                      .credentials(
                          ServiceNowUserNamePasswordDTO.builder()
                              .usernameRef(
                                  SecretRefData.builder().decryptedValue(new char[] {'U', 'S', 'E', 'R', '1'}).build())
                              .passwordRef(
                                  SecretRefData.builder().decryptedValue(new char[] {'3', '4', 'f', '5', '1'}).build())
                              .build())
                      .build())
            .build();

    token = ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
    assertThat(token).isEqualTo(Credentials.basic("USER1", "34f51"));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenAdfs() throws Exception {
    // adfs
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(
                ServiceNowAuthenticationDTO.builder()
                    .authType(ServiceNowAuthType.ADFS)
                    .credentials(
                        ServiceNowADFSDTO.builder()
                            .resourceIdRef(SecretRefData.builder().decryptedValue(RESOURCE_ID.toCharArray()).build())
                            .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                            .privateKeyRef(SecretRefData.builder().decryptedValue(PEM_KEY_VALID.toCharArray()).build())
                            .certificateRef(
                                SecretRefData.builder().decryptedValue(PEM_CERT_VALID.toCharArray()).build())
                            .adfsUrl(ADFS_URL)
                            .build())
                    .build())
            .build();
    AdfsRestClient adfsRestClient = Mockito.mock(AdfsRestClient.class);
    Call<AdfsAccessTokenResponse> mockCall = Mockito.mock(Call.class);
    when(adfsRestClient.getAccessToken(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);

    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"bearer\",\"expires_in\":43200}";
    AdfsAccessTokenResponse adfsAccessTokenResponse = JsonUtils.asObject(jsonResponse, new TypeReference<>() {});
    Response<AdfsAccessTokenResponse> accessTokenResponse = Response.success(adfsAccessTokenResponse);
    when(mockCall.execute()).thenReturn(accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(
             Retrofit.class, (mock, context) -> when(mock.create(AdfsRestClient.class)).thenReturn(adfsRestClient));
         MockedStatic<ADFSAuthHelper> ignored1 = mockStatic(ADFSAuthHelper.class)) {
      when(ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_CERT_VALID, PEM_KEY_VALID, CLIENT_ID,
               UrlUtility.appendPathToBaseUrl(ADFS_URL, ADFS_ACCESS_TOKEN_ENDPOINT)))
          .thenReturn(JWT_TOKEN);

      String token = ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);

      final ArgumentCaptor<String> clientIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
      final ArgumentCaptor<String> clientAssertionTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
      final ArgumentCaptor<String> jwtTokenArgumentCaptor = ArgumentCaptor.forClass(String.class);
      final ArgumentCaptor<String> grantTypeArgumentCaptor = ArgumentCaptor.forClass(String.class);
      final ArgumentCaptor<String> resourceIdArgumentCaptor = ArgumentCaptor.forClass(String.class);

      verify(adfsRestClient, times(1))
          .getAccessToken(clientIdArgumentCaptor.capture(), clientAssertionTypeArgumentCaptor.capture(),
              jwtTokenArgumentCaptor.capture(), grantTypeArgumentCaptor.capture(), resourceIdArgumentCaptor.capture());
      assertThat(clientIdArgumentCaptor.getValue()).isEqualTo(CLIENT_ID);
      assertThat(clientAssertionTypeArgumentCaptor.getValue()).isEqualTo(ADFS_CLIENT_ASSERTION_TYPE);
      assertThat(jwtTokenArgumentCaptor.getValue()).isEqualTo(JWT_TOKEN);
      assertThat(grantTypeArgumentCaptor.getValue()).isEqualTo(ADFS_GRANT_TYPE);
      assertThat(resourceIdArgumentCaptor.getValue()).isEqualTo(RESOURCE_ID);
      assertThat(token).isEqualTo(BEARER_TOKEN);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenAdfsWhenTokenGenerationThrowingException() throws Exception {
    // adfs
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(
                ServiceNowAuthenticationDTO.builder()
                    .authType(ServiceNowAuthType.ADFS)
                    .credentials(
                        ServiceNowADFSDTO.builder()
                            .resourceIdRef(SecretRefData.builder().decryptedValue(RESOURCE_ID.toCharArray()).build())
                            .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                            .privateKeyRef(SecretRefData.builder().decryptedValue(PEM_KEY_VALID.toCharArray()).build())
                            .certificateRef(
                                SecretRefData.builder().decryptedValue(PEM_CERT_VALID.toCharArray()).build())
                            .adfsUrl(ADFS_URL)
                            .build())
                    .build())
            .build();

    try (MockedStatic<ADFSAuthHelper> ignored = Mockito.mockStatic(ADFSAuthHelper.class)) {
      when(ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_CERT_VALID, PEM_KEY_VALID, CLIENT_ID,
               UrlUtility.appendPathToBaseUrl(ADFS_URL, ADFS_ACCESS_TOKEN_ENDPOINT)))
          .thenThrow(new AdfsAuthException("Error generating jwt token", ADFS_ERROR, WingsException.USER));

      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(WingsException.class);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenAdfsWhenRestCallErrorResponse() throws Exception {
    // adfs
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(
                ServiceNowAuthenticationDTO.builder()
                    .authType(ServiceNowAuthType.ADFS)
                    .credentials(
                        ServiceNowADFSDTO.builder()
                            .resourceIdRef(SecretRefData.builder().decryptedValue(RESOURCE_ID.toCharArray()).build())
                            .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                            .privateKeyRef(SecretRefData.builder().decryptedValue(PEM_KEY_VALID.toCharArray()).build())
                            .certificateRef(
                                SecretRefData.builder().decryptedValue(PEM_CERT_VALID.toCharArray()).build())
                            .adfsUrl(ADFS_URL)
                            .build())
                    .build())
            .build();
    AdfsRestClient adfsRestClient = Mockito.mock(AdfsRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call<AdfsAccessTokenResponse> mockCall = Mockito.mock(Call.class);

    // generic error response, not parsed into error response
    Response<?> errorResp =
        Response.error(ResponseBody.create(MediaType.parse("application/json"), "400 related error"),
            new okhttp3.Response.Builder()
                .message("message")
                .code(400)
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url("http://localhost/").build())
                .build());
    when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) errorResp);

    when(adfsRestClient.getAccessToken(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);

    try (MockedConstruction<Retrofit> ignored = mockConstruction(
             Retrofit.class, (mock, context) -> when(mock.create(AdfsRestClient.class)).thenReturn(adfsRestClient));
         MockedStatic<ADFSAuthHelper> ignored1 = mockStatic(ADFSAuthHelper.class)) {
      when(ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_CERT_VALID, PEM_KEY_VALID, CLIENT_ID,
               UrlUtility.appendPathToBaseUrl(ADFS_URL, ADFS_ACCESS_TOKEN_ENDPOINT)))
          .thenReturn(JWT_TOKEN);

      try {
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
        fail("exception expected");
      } catch (HintException ex) {
        assertThat(ex.getMessage())
            .isEqualTo("Check if the ADFS url and credentials are correct and accessible from delegate");
        assertThat(ex.getCause().getMessage())
            .isEqualTo(
                "Not able to access the given ADFS url with the credentials or not able to get access token from ADFS");
        assertThat(ex.getCause().getCause().getCause()).isInstanceOf(AdfsAuthException.class);
        assertThat(ex.getCause().getCause().getCause().getMessage()).isEqualTo("400 related error");
      }

      // 404 error response
      Response errorResp1 =
          Response.error(ResponseBody.create(MediaType.parse("application/json"), "404 related error"),
              new okhttp3.Response.Builder()
                  .message("message")
                  .code(404)
                  .protocol(Protocol.HTTP_1_1)
                  .request(new Request.Builder().url("http://localhost/").build())
                  .build());
      when(mockCall.execute()).thenReturn(errorResp1);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class)
          .hasMessage("Check if the Adfs url is correct and accessible from delegate");

      // 401 error response
      Response errorResp2 =
          Response.error(ResponseBody.create(MediaType.parse("application/json"), "401 related error"),
              new okhttp3.Response.Builder()
                  .message("message")
                  .code(401)
                  .protocol(Protocol.HTTP_1_1)
                  .request(new Request.Builder().url("http://localhost/").build())
                  .build());
      when(mockCall.execute()).thenReturn(errorResp2);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class)
          .hasMessage(
              "Check if the ADFS credentials are correct and client have necessary permissions to access the ServiceNow resource");

      //  error response with custom code and correct error response
      String errorJson =
          "{\"error\":\"invalid_grant\",\"error_description\":\"MSIS9622: Client authentication failed. Please verify the credential provided for client authentication is valid.\"}";
      Response errorResp3 = Response.error(ResponseBody.create(MediaType.parse("application/json"), errorJson),
          new okhttp3.Response.Builder()
              .message("message")
              .code(400)
              .protocol(Protocol.HTTP_1_1)
              .request(new Request.Builder().url("http://localhost/").build())
              .build());
      when(mockCall.execute()).thenReturn(errorResp3);
      try {
        ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO);
        fail("exception expected");
      } catch (HintException ex) {
        assertThat(ex.getMessage())
            .isEqualTo("Check if the ADFS url and credentials are correct and accessible from delegate");
        assertThat(ex.getCause().getMessage())
            .isEqualTo(
                "Not able to access the given ADFS url with the credentials or not able to get access token from ADFS");
        assertThat(ex.getCause().getCause().getCause()).isInstanceOf(AdfsAuthException.class);
        assertThat(ex.getCause().getCause().getCause().getMessage())
            .isEqualTo(
                "[invalid_grant] : MSIS9622: Client authentication failed. Please verify the credential provided for client authentication is valid.");
      }
    }
  }
  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenAdfsSuccessButInvalidFormat() throws Exception {
    // adfs
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(
                ServiceNowAuthenticationDTO.builder()
                    .authType(ServiceNowAuthType.ADFS)
                    .credentials(
                        ServiceNowADFSDTO.builder()
                            .resourceIdRef(SecretRefData.builder().decryptedValue(RESOURCE_ID.toCharArray()).build())
                            .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                            .privateKeyRef(SecretRefData.builder().decryptedValue(PEM_KEY_VALID.toCharArray()).build())
                            .certificateRef(
                                SecretRefData.builder().decryptedValue(PEM_CERT_VALID.toCharArray()).build())
                            .adfsUrl(ADFS_URL)
                            .build())
                    .build())
            .build();
    AdfsRestClient adfsRestClient = Mockito.mock(AdfsRestClient.class);
    Retrofit retrofit = Mockito.mock(Retrofit.class);
    Call<AdfsAccessTokenResponse> mockCall = Mockito.mock(Call.class);
    when(adfsRestClient.getAccessToken(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(mockCall);

    // token type invalid
    String jsonResponse =
        "{\"access_token\":\"testtoken&&%$%^%$testtoken\",\"token_type\":\"invalid token type\",\"expires_in\":43200}";
    Response<?> accessTokenResponse = Response.success(jsonResponse);
    when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) accessTokenResponse);
    try (MockedConstruction<Retrofit> ignored = mockConstruction(
             Retrofit.class, (mock, context) -> when(mock.create(AdfsRestClient.class)).thenReturn(adfsRestClient));
         MockedStatic<ADFSAuthHelper> ignored1 = mockStatic(ADFSAuthHelper.class)) {
      when(ADFSAuthHelper.generateJwtSignedRequestWithCertificate(PEM_CERT_VALID, PEM_KEY_VALID, CLIENT_ID,
               UrlUtility.appendPathToBaseUrl(ADFS_URL, ADFS_ACCESS_TOKEN_ENDPOINT)))
          .thenReturn(JWT_TOKEN);

      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class);

      // access token blank
      jsonResponse = "{\"access_token\":\"  \",\"token_type\":\"bearer\",\"expires_in\":43200}";
      accessTokenResponse = Response.success(jsonResponse);
      when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) accessTokenResponse);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class);

      // missing expires in
      jsonResponse = "{\"access_token\":\" dddd \",\"token_type\":\"bearer\"}";
      accessTokenResponse = Response.success(jsonResponse);
      when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) accessTokenResponse);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class);

      // missing token type in
      jsonResponse = "{\"access_token\":\" dd  \",\"expires_in\":43200}";
      accessTokenResponse = Response.success(jsonResponse);
      when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) accessTokenResponse);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class);

      // missing access token  in
      jsonResponse = "{\"token_type\":\"bearer\",\"expires_in\":43200}";
      accessTokenResponse = Response.success(jsonResponse);
      when(mockCall.execute()).thenReturn((Response<AdfsAccessTokenResponse>) accessTokenResponse);
      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(HintException.class);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenRefreshTokenWhenTokenGenerationThrowingException() {
    // refresh token
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(ServiceNowAuthenticationDTO.builder()
                      .authType(ServiceNowAuthType.REFRESH_TOKEN)
                      .credentials(
                          ServiceNowRefreshTokenDTO.builder()
                              .refreshTokenRef(
                                  SecretRefData.builder().decryptedValue(REFRESH_TOKEN.toCharArray()).build())
                              .scope(SCOPE)
                              .tokenUrl(TOKEN_URL)
                              .clientSecretRef(
                                  SecretRefData.builder().decryptedValue(CLIENT_SECRET.toCharArray()).build())
                              .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                              .build())
                      .build())
            .build();

    try (MockedStatic<RefreshTokenAuthNgHelper> ignored = Mockito.mockStatic(RefreshTokenAuthNgHelper.class)) {
      when(RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, true))
          .thenThrow(new ServiceNowOIDCException(
              "Error fetching access token", SERVICENOW_REFRESH_TOKEN_ERROR, WingsException.USER));

      assertThatThrownBy(() -> ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO))
          .isInstanceOf(ServiceNowOIDCException.class);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAuthTokenWhenRefreshToken() {
    // refresh token
    ServiceNowConnectorDTO serviceNowConnectorDTO =
        ServiceNowConnectorDTO.builder()
            .auth(ServiceNowAuthenticationDTO.builder()
                      .authType(ServiceNowAuthType.REFRESH_TOKEN)
                      .credentials(
                          ServiceNowRefreshTokenDTO.builder()
                              .refreshTokenRef(
                                  SecretRefData.builder().decryptedValue(REFRESH_TOKEN.toCharArray()).build())
                              .scope(SCOPE)
                              .tokenUrl(TOKEN_URL)
                              .clientSecretRef(
                                  SecretRefData.builder().decryptedValue(CLIENT_SECRET.toCharArray()).build())
                              .clientIdRef(SecretRefData.builder().decryptedValue(CLIENT_ID.toCharArray()).build())
                              .build())
                      .build())
            .build();

    try (MockedStatic<RefreshTokenAuthNgHelper> ignored = Mockito.mockStatic(RefreshTokenAuthNgHelper.class)) {
      when(RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, CLIENT_SECRET, SCOPE, TOKEN_URL, true))
          .thenReturn(BEARER_TOKEN);

      assertThat(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO)).isEqualTo(BEARER_TOKEN);
    }

    // client secret null
    ServiceNowRefreshTokenDTO serviceNowRefreshTokenDTO =
        (ServiceNowRefreshTokenDTO) serviceNowConnectorDTO.getAuth().getCredentials();
    serviceNowRefreshTokenDTO.setClientSecretRef(SecretRefData.builder().build());

    try (MockedStatic<RefreshTokenAuthNgHelper> ignored = Mockito.mockStatic(RefreshTokenAuthNgHelper.class)) {
      when(RefreshTokenAuthNgHelper.getAuthToken(REFRESH_TOKEN, CLIENT_ID, null, SCOPE, TOKEN_URL, true))
          .thenReturn(BEARER_TOKEN);

      assertThat(ServiceNowAuthNgHelper.getAuthToken(serviceNowConnectorDTO)).isEqualTo(BEARER_TOKEN);
    }
  }
}