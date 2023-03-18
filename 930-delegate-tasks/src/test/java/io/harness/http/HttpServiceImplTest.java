/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.http;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.AGORODETKI;

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
}