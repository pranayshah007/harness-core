/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.http;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Ascii.toUpperCase;
import static org.apache.commons.lang3.exception.ExceptionUtils.getMessage;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.KeyValuePair;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.AuthenticationRuntimeException;
import io.harness.exception.runtime.AuthorizationRuntimeException;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class HttpServiceImpl implements HttpService {
  private static final Splitter HEADERS_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
  private static final Splitter HEADER_SPLITTER = Splitter.on(":").trimResults();

  @Override
  public HttpInternalResponse executeUrl(HttpInternalConfig httpInternalConfig) throws IOException {
    HttpInternalResponse httpInternalResponse = new HttpInternalResponse();

    SSLContextBuilder builder = new SSLContextBuilder();

    if (!httpInternalConfig.isCertValidationRequired()) {
      try {
        builder.loadTrustMaterial((x509Certificates, s) -> true);
      } catch (NoSuchAlgorithmException | KeyStoreException e) {
        log.error("", e);
      }
    }

    SSLConnectionSocketFactory sslsf = null;
    try {
      sslsf = new SSLConnectionSocketFactory(builder.build(), (s, sslSession) -> true);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      log.error("", e);
    }

    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder = requestBuilder.setConnectTimeout(2000);
    requestBuilder = requestBuilder.setSocketTimeout(httpInternalConfig.getSocketTimeoutMillis());
    HttpClientBuilder httpClientBuilder =
        HttpClients.custom().setSSLSocketFactory(sslsf).setDefaultRequestConfig(requestBuilder.build());

    if (httpInternalConfig.isUseProxy()) {
      if (Http.shouldUseNonProxy(httpInternalConfig.getUrl())) {
        if (httpInternalConfig.isThrowErrorIfNoProxySetWithDelegateProxy()) {
          throw new InvalidRequestException(
              "Delegate is configured not to use proxy for the given url: " + httpInternalConfig.getUrl(),
              WingsException.USER);
        }
      } else {
        HttpHost proxyHost = Http.getHttpProxyHost();
        if (proxyHost != null) {
          if (isNotEmpty(Http.getProxyUserName())) {
            httpClientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(proxyHost),
                new UsernamePasswordCredentials(Http.getProxyUserName(), Http.getProxyPassword()));
            httpClientBuilder.setDefaultCredentialsProvider(credsProvider);
          }
          httpClientBuilder.setProxy(proxyHost);
        } else {
          log.warn("Task setup to use DelegateProxy but delegate setup without any proxy");
        }
      }
    }

    CloseableHttpClient httpclient = httpClientBuilder.build();

    HttpUriRequest httpUriRequest = getMethodSpecificHttpRequest(
        toUpperCase(httpInternalConfig.getMethod()), httpInternalConfig.getUrl(), httpInternalConfig.getBody());

    if (isNotEmpty(httpInternalConfig.getHeaders())) {
      for (KeyValuePair header : httpInternalConfig.getHeaders()) {
        httpUriRequest.addHeader(header.getKey(), header.getValue());
      }
    }

    // For NG Headers
    if (httpInternalConfig.getRequestHeaders() != null) {
      for (Map.Entry<String, String> entry : httpInternalConfig.getRequestHeaders().entrySet()) {
        httpUriRequest.addHeader(entry.getKey(), entry.getValue());
      }
    }

    httpInternalResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);

    ErrorHandlingGlobalContextData globalContextData =
        GlobalContextManager.get(ErrorHandlingGlobalContextData.IS_SUPPORTED_ERROR_FRAMEWORK);
    if (globalContextData != null && globalContextData.isSupportedErrorFramework()) {
      return executeHttpStep(httpclient, httpInternalResponse, httpUriRequest, httpInternalConfig, true);
    } else {
      try {
        executeHttpStep(httpclient, httpInternalResponse, httpUriRequest, httpInternalConfig, false);
      } catch (SocketTimeoutException | ConnectTimeoutException | HttpHostConnectException e) {
        handleException(httpInternalResponse, e, true);
      } catch (IOException e) {
        handleException(httpInternalResponse, e, false);
      }

      return httpInternalResponse;
    }
  }

  @VisibleForTesting
  protected HttpInternalResponse executeHttpStep(CloseableHttpClient httpclient,
      HttpInternalResponse httpInternalResponse, HttpUriRequest httpUriRequest, HttpInternalConfig httpInternalConfig,
      boolean isSupportingErrorFramework) throws IOException {
    HttpResponse httpResponse = httpclient.execute(httpUriRequest);
    if (isSupportingErrorFramework) {
      if (httpResponse.getStatusLine().getStatusCode() == 401) {
        throw new AuthenticationRuntimeException(httpUriRequest.getURI().toString());
      }

      if (httpResponse.getStatusLine().getStatusCode() == 403) {
        throw new AuthorizationRuntimeException(httpUriRequest.getURI().toString());
      }
    }

    httpInternalResponse.setHeader(httpInternalConfig.getHeader());
    httpInternalResponse.setHttpResponseCode(httpResponse.getStatusLine().getStatusCode());
    HttpEntity entity = httpResponse.getEntity();
    httpInternalResponse.setHttpResponseBody(
        entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "");

    return httpInternalResponse;
  }

  private void handleException(HttpInternalResponse httpInternalResponse, IOException e, boolean timedOut) {
    log.error("Exception occurred during HTTP task execution", e);
    httpInternalResponse.setHttpResponseCode(500);
    httpInternalResponse.setHttpResponseBody(getMessage(e));
    httpInternalResponse.setErrorMessage(getMessage(e));
    httpInternalResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    httpInternalResponse.setTimedOut(timedOut);
  }

  @VisibleForTesting
  protected HttpUriRequest getMethodSpecificHttpRequest(String method, String url, String body) {
    switch (method) {
      case "GET":
        return new HttpGet(url);
      case "POST":
        HttpPost post = new HttpPost(url);
        setEntity(body, post);
        return post;
      case "PATCH":
        HttpPatch patch = new HttpPatch(url);
        setEntity(body, patch);
        return patch;
      case "PUT":
        HttpPut put = new HttpPut(url);
        setEntity(body, put);
        return put;
      case "DELETE":
        return new HttpDelete(url);
      case "HEAD":
      default:
        return new HttpHead(url);
    }
  }

  private void setEntity(String body, HttpEntityEnclosingRequestBase entityEnclosingRequestBase) {
    if (body != null) {
      entityEnclosingRequestBase.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
    }
  }
}
