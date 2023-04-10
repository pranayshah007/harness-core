/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.http;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.HttpCertificate;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;
import io.harness.rule.Owner;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import wiremock.org.apache.http.conn.ConnectTimeoutException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Http.class, GlobalContextManager.class, HttpServiceImpl.class})
public class HttpServiceImplTest extends CategoryTest {
  @InjectMocks HttpServiceImpl httpService;

  HttpInternalConfig httpInternalConfig_Proxy_T_CertValid_F_Error_T;
  HttpInternalConfig httpInternalConfig_Proxy_T_CertValid_T_Error_T;
  HttpInternalConfig httpInternalConfig_Proxy_F_CertValid_F_Error_T;
  HttpInternalConfig httpInternalConfig_Create_SSLContextBuilder;

  String cert = "-----BEGIN CERTIFICATE-----\n"
      + "MIICwDCCAagCCQCzMN+X/Ym6hTANBgkqhkiG9w0BAQsFADAiMQswCQYDVQQGEwJV\n"
      + "UzETMBEGA1UECAwKQ2FsaWZvcm5pYTAeFw0yMzAxMDYyMjQ2NDZaFw0yNTEwMDEy\n"
      + "MjQ2NDZaMCIxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMIIBIjAN\n"
      + "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxrPzWy6wTmtLHgQ0mVY/iLBBdh6I\n"
      + "8YpqYqAFgrm3vjvhe+ymxUlHNCkM8syfFeUrHm06yfBwOoBsjkxCK9wjZgPnxRHe\n"
      + "4OwNEM85668Qz5rWe0HViHedhVDVx/JSU5Ci/Z4dn1DzEumAtb9dZzUrDK5rRJ27\n"
      + "7sUlMqvcc23w39h0HLwh8o/WxHNtHSWfx/Pqs3OkazKrxr9f54BoX31zTmSmq4LV\n"
      + "t8sILLDRbfFeHQvlxY+ZWVFNtCVo40L5Pn0YmBOcjm1tcjuuaJpNbTu+AVwoR2Jb\n"
      + "Nrfwq6Tjhg0kajIKVJhdUlAp6lJ8w+L3LZV4a8dwAl5dWfLSmb9gf4tY6QIDAQAB\n"
      + "MA0GCSqGSIb3DQEBCwUAA4IBAQAonmJCr3JhGYRrazhRA7DtohD+UsidL81PY1ij\n"
      + "r67FvyfV6pXw4NumN9+HPEa5a6/ZQ4u2FNbG1Jip7862XV/TTB5rH4ysrl24znVq\n"
      + "/mMBaj8j7/QYOmp/9RLotCD3QmQ0SpKxF5BT1X38iSJV7puoVp7osKjt7rDvBT1d\n"
      + "iQUHFO+wQJrrqWS7lZm8bdSF7ZdHT+ezTkBIW/+b0yzWsaJ9V9aNa//MG8SBKEAl\n"
      + "I1c0N4LlabXsFRatLB5WUmkjG5PCUL5Nt3ArgAU3Jyy6O8Bmma+abXUAm81eEzpY\n"
      + "sPOlhBFKZWC651W+vVvXlaBp6fGL0LpYbvaaxVbqsvevYPJY\n"
      + "-----END CERTIFICATE-----\n";

  String certKey = "-----BEGIN PRIVATE KEY-----\n"
      + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDGs/NbLrBOa0se\n"
      + "BDSZVj+IsEF2HojximpioAWCube+O+F77KbFSUc0KQzyzJ8V5SsebTrJ8HA6gGyO\n"
      + "TEIr3CNmA+fFEd7g7A0QzznrrxDPmtZ7QdWId52FUNXH8lJTkKL9nh2fUPMS6YC1\n"
      + "v11nNSsMrmtEnbvuxSUyq9xzbfDf2HQcvCHyj9bEc20dJZ/H8+qzc6RrMqvGv1/n\n"
      + "gGhffXNOZKargtW3ywgssNFt8V4dC+XFj5lZUU20JWjjQvk+fRiYE5yObW1yO65o\n"
      + "mk1tO74BXChHYls2t/CrpOOGDSRqMgpUmF1SUCnqUnzD4vctlXhrx3ACXl1Z8tKZ\n"
      + "v2B/i1jpAgMBAAECggEAEJA5jfVDXxYUiekB1XJaE3PV0RnUgoXuPlBmhTIj/eiR\n"
      + "8DmW4UUteUyetrKV5EZZJM0oJGM1h7ri0a3LqkpMbRmQPV4y/P7QTAFqK5pJRXT7\n"
      + "wgSH3ztRVyaY23T4pdydqZR/laMyz/XE8+GC0LKe5wy3Bl47pzip1CJ9WuXkOVR0\n"
      + "7Yc1Y6cNlhU2RHseEhFpVT4/MXp+bc/naEF/QMxVLfvIXrbnULlaMRAuqDEdZy7q\n"
      + "t2qvW+TsPnQrYH5LDKx0sDzS14yI+OIuTZ7DDpJ3TszZ3lLdLHbUYjuxLDiKxwam\n"
      + "KUAObQ7kqxa3tvt++FxWkkvisCjgmkEH0GzyFUNVMQKBgQD7quXqMQ27tks/u1SG\n"
      + "C3Ep/a4Gx6wqxHrp4GsdIdORu6odNOtnYjVPZiRZGzutOe8dcKVhbat88HIJNRpF\n"
      + "6Z01WbOvwsRgTe+ozI4b4a1AaROzvTz4rq9f3oZTmYcOTUxf8Zam2f8V3B+H357o\n"
      + "NS2kSfSeurUCZYvvYKBvfNLN1QKBgQDKH6MAv3j054GeMLBRreob0sxj31WV8PXR\n"
      + "igQ2d75QPkmWC6SA9JlJgkFRjT/a9D+a0He/jhm+DKS222UgGMaE4W0K0NgfNdzQ\n"
      + "Dp51/jNCKbsvjXBWtQUC53OYaOfKf/zOVWgqWeOf432pacMZnUs/LdhlB5OTgmgy\n"
      + "TXg77l0kxQKBgQCpZsXQOCi4W+KXCa/Bct4/l6SWp7z6JLtfxlITj/trs1i0xDRY\n"
      + "qMCdq3F4EV7AIakUtgh8Zmfyd58rF3WR7ciGatUK0B2DfbJ+ewKFPglyu8gpSo5K\n"
      + "Dru52n2stEE2nU11n5b6xO5xdnQ674l1YKZSWf2xApho/pWNEgusP+dd6QKBgBM/\n"
      + "egVbNoiT90r6NgBBQJcPtvkXzo2t2arvqsEJHC2GEPnh9/Nz15khd1jty5PtSJVU\n"
      + "nuK2BIuNpq3nLLUmxtjmoryx8LLgLTv++GYiI/17/eBkZrtLF8QUCHUOIGyvTYLU\n"
      + "rUvDLaMPRes5MCQjT4QfuIi+dPZKJ+QKbpW+eE5FAoGARw1A+oTxWs1yB94TXAX6\n"
      + "pTc4MCbgG+VVnQtlkHlBuBPdQltXumn5qSfLbSiXM0l/aVB3MV1OIqHLutC7QEbT\n"
      + "4IPx0xJAR5cmNKHeRAjsL5yN+N8Bz02zg/pz/upapxl6jitEj4+2CLVb2eK140pU\n"
      + "OqwwfdMKaytzpDqUNsbiy/w=\n"
      + "-----END PRIVATE KEY-----\n";

  @Before
  public void setup() {
    httpInternalConfig_Proxy_T_CertValid_F_Error_T = HttpInternalConfig.builder()
                                                         .method("GET")
                                                         .body(null)
                                                         .headers(null)
                                                         .socketTimeoutMillis(5000)
                                                         .url("https://tempUrl")
                                                         .useProxy(true)
                                                         .isCertValidationRequired(false)
                                                         .throwErrorIfNoProxySetWithDelegateProxy(true)
                                                         .build();

    httpInternalConfig_Proxy_T_CertValid_T_Error_T = HttpInternalConfig.builder()
                                                         .method("GET")
                                                         .body(null)
                                                         .headers(null)
                                                         .socketTimeoutMillis(5000)
                                                         .url("https://tempUrl")
                                                         .useProxy(true)
                                                         .isCertValidationRequired(true)
                                                         .throwErrorIfNoProxySetWithDelegateProxy(true)
                                                         .build();

    httpInternalConfig_Proxy_F_CertValid_F_Error_T = HttpInternalConfig.builder()
                                                         .method("GET")
                                                         .body(null)
                                                         .headers(null)
                                                         .socketTimeoutMillis(5000)
                                                         .url("https://tempUrl")
                                                         .useProxy(false)
                                                         .isCertValidationRequired(false)
                                                         .throwErrorIfNoProxySetWithDelegateProxy(true)
                                                         .build();

    httpInternalConfig_Create_SSLContextBuilder =
        HttpInternalConfig.builder()
            .method("GET")
            .body(null)
            .headers(null)
            .socketTimeoutMillis(5000)
            .url("https://tempUrl")
            .useProxy(false)
            .certificate(HttpCertificate.builder().cert(cert.toCharArray()).certKey(certKey.toCharArray()).build())
            .isCertValidationRequired(true)
            .build();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnMethodSpecificUriRequest() {
    List<String> methodsList = Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");
    for (String method : methodsList) {
      HttpUriRequest request = httpService.getMethodSpecificHttpRequest(method, "url", "body");
      assertThat(request.getMethod()).isEqualTo(method);
    }
  }

  // Should throw InvalidRequestException when UseProxy is true, Http return true to shouldUseNonProxy and throwing
  // error is allowed when No Proxy Set With Delegate Proxy

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_IsUseProxy_Error() throws IOException {
    PowerMockito.mockStatic(Http.class);
    when(Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl()))
        .thenAnswer((Answer<Boolean>) invocation -> true);

    assertThatThrownBy(() -> httpService.executeUrl(httpInternalConfig_Proxy_T_CertValid_F_Error_T))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Delegate is configured not to use proxy for the given url: "
            + httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl());
  }

  // Check for Successful completion when proxy Host is set and executeHttpStep return Positive response

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenProxy() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(Http.class);
    // this is a dirty fix (because we are using different MockMaker then PowerMockito is not working as expected when
    // calling verifyStatic(...)) with this call we are registering Http class to list of mocked static classes
    Mockito.mockStatic(Http.class);
    when(Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl()))
        .thenAnswer((Answer<Boolean>) invocation -> false);

    HttpHost myHttpHost = new HttpHost("MyHost");
    when(Http.getHttpProxyHost()).thenAnswer((Answer<HttpHost>) invocation -> myHttpHost);

    when(Http.getProxyUserName()).thenAnswer((Answer<String>) invocation -> "username");
    when(Http.getProxyPassword()).thenAnswer((Answer<String>) invocation -> "password");

    doReturn(new HttpGet(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getMethod(),
            httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl(),
            httpInternalConfig_Proxy_T_CertValid_T_Error_T.getBody());

    doReturn(new HttpInternalResponse())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_T_CertValid_T_Error_T);

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl());

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.getHttpProxyHost();

    PowerMockito.verifyStatic(Http.class, times(2));
    Http.getProxyUserName();

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.getProxyPassword();

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getMethod(),
        httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl(),
        httpInternalConfig_Proxy_T_CertValid_T_Error_T.getBody());
  }

  // Check for Successful completion when proxy Host is set but username is empty and executeHttpStep return Positive
  // response

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenProxyButUsername_Null() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(Http.class);
    // this is a dirty fix (because we are using different MockMaker then PowerMockito is not working as expected when
    // calling verifyStatic(...)) with this call we are registering Http class to list of mocked static classes
    Mockito.mockStatic(Http.class);
    when(Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl()))
        .thenAnswer((Answer<Boolean>) invocation -> false);

    HttpHost myHttpHost = new HttpHost("MyHost");
    when(Http.getHttpProxyHost()).thenAnswer((Answer<HttpHost>) invocation -> myHttpHost);

    when(Http.getProxyUserName()).thenAnswer((Answer<String>) invocation -> "");
    when(Http.getProxyPassword()).thenAnswer((Answer<String>) invocation -> "password");

    doReturn(new HttpGet(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getMethod(),
            httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl(),
            httpInternalConfig_Proxy_T_CertValid_T_Error_T.getBody());

    doReturn(new HttpInternalResponse())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_T_CertValid_T_Error_T);

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl());

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.getHttpProxyHost();

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.getProxyUserName();

    PowerMockito.verifyStatic(Http.class, times(0));
    Http.getProxyPassword();

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_T_Error_T.getMethod(),
        httpInternalConfig_Proxy_T_CertValid_T_Error_T.getUrl(),
        httpInternalConfig_Proxy_T_CertValid_T_Error_T.getBody());
  }

  // Check for Successful completion when proxy Host is set null and executeHttpStep return Positive response

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenProxyHostNull() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(Http.class);
    // this is a dirty fix (because we are using different MockMaker then PowerMockito is not working as expected when
    // calling verifyStatic(...)) with this call we are registering Http class to list of mocked static classes
    Mockito.mockStatic(Http.class);
    when(Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl()))
        .thenAnswer((Answer<Boolean>) invocation -> false);

    when(Http.getHttpProxyHost()).thenAnswer((Answer<Void>) invocation -> null);

    doReturn(new HttpGet(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getMethod(),
            httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl(),
            httpInternalConfig_Proxy_T_CertValid_F_Error_T.getBody());

    doReturn(new HttpInternalResponse())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_T_CertValid_F_Error_T);

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.shouldUseNonProxy(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl());

    PowerMockito.verifyStatic(Http.class, times(1));
    Http.getHttpProxyHost();

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_T_CertValid_F_Error_T.getMethod(),
        httpInternalConfig_Proxy_T_CertValid_F_Error_T.getUrl(),
        httpInternalConfig_Proxy_T_CertValid_F_Error_T.getBody());
  }

  // Check for Successful completion when globalContextData is null and executeHttpStep return Positive response

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenGlobalContextDataNull_Positive() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(GlobalContextManager.class);

    when(GlobalContextManager.get(any(String.class))).thenAnswer((Answer<Void>) invocation -> null);

    doReturn(new HttpGet(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());

    doReturn(new HttpInternalResponse())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    HttpInternalResponse output = spyHttpService.executeUrl(httpInternalConfig_Proxy_F_CertValid_F_Error_T);

    assertThat(output.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());
  }

  // Check for Successful completion and SocketTimeoutException is handled when globalContextData is null and
  // executeHttpStep throws SocketTimeoutException

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenGlobalContextDataNull_SocketTimeoutExceptionCase() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(GlobalContextManager.class);

    when(GlobalContextManager.get(any(String.class))).thenAnswer((Answer<Void>) invocation -> null);

    doReturn(new HttpGet(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());

    doThrow(new SocketTimeoutException())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_F_CertValid_F_Error_T);

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());
  }

  // Check for Successful completion and ConnectTimeoutException is handled when globalContextData is null and
  // executeHttpStep throws ConnectTimeoutException

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenGlobalContextDataNull_ConnectTimeoutExceptionCase() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(GlobalContextManager.class);

    when(GlobalContextManager.get(any(String.class))).thenAnswer((Answer<Void>) invocation -> null);

    doReturn(new HttpGet(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());

    doThrow(new ConnectTimeoutException())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_F_CertValid_F_Error_T);

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());
  }

  // Check for Successful completion and IOException is handled when globalContextData is null and executeHttpStep
  // throws IOException

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeUrl_checkWhenGlobalContextDataNull_IOExceptionCase() throws Exception {
    HttpServiceImpl spyHttpService = spy(httpService);

    PowerMockito.mockStatic(GlobalContextManager.class);

    when(GlobalContextManager.get(any(String.class))).thenAnswer((Answer<Void>) invocation -> null);

    doReturn(new HttpGet(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl()))
        .when(spyHttpService)
        .getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
            httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());

    doThrow(new IOException())
        .when(spyHttpService)
        .executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class), any(HttpUriRequest.class),
            any(HttpInternalConfig.class), anyBoolean(), any());
    spyHttpService.executeUrl(httpInternalConfig_Proxy_F_CertValid_F_Error_T);

    verify(spyHttpService, times(1));
    spyHttpService.executeHttpStep(any(CloseableHttpClient.class), any(HttpInternalResponse.class),
        any(HttpUriRequest.class), any(HttpInternalConfig.class), anyBoolean(), any());

    verify(spyHttpService, times(1));
    spyHttpService.getMethodSpecificHttpRequest(httpInternalConfig_Proxy_F_CertValid_F_Error_T.getMethod(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getUrl(),
        httpInternalConfig_Proxy_F_CertValid_F_Error_T.getBody());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCreateSslContextBuilder() {
    SSLContextBuilder sslContextBuilder =
        httpService.createSslContextBuilder(httpInternalConfig_Create_SSLContextBuilder);
    assertThat(sslContextBuilder).isNotNull();
  }
}