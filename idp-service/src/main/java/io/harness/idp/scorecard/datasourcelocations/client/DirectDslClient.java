/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.security.AllTrustingX509TrustManager;

import com.google.common.collect.ImmutableList;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class DirectDslClient implements DslClient {
  private static final String POST_METHOD = "POST";
  private static final String GET_METHOD = "GET";
  private static final ImmutableList<TrustManager> TRUST_ALL_CERTS =
      ImmutableList.of(new AllTrustingX509TrustManager());

  @Override
  public Response call(String accountIdentifier, ApiRequestDetails apiRequestDetails)
      throws NoSuchAlgorithmException, KeyManagementException {
    OkHttpClient client = buildOkHttpClient();
    String url = apiRequestDetails.getUrl();
    String method = apiRequestDetails.getMethod();
    String body = apiRequestDetails.getRequestBody();
    log.info("Executing request through direct DSL for url = {}, method = {}, body - {}", url, method, body);
    Request request = buildRequest(url, method, apiRequestDetails.getHeaders(), body);
    log.info("Request - {}", request.body());
    log.info("Request - {} ", request.toString());
    return executeRequest(client, request);
  }

  private OkHttpClient buildOkHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    return new OkHttpClient()
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
        .build();
  }

  private Request buildRequest(String url, String method, Map<String, String> headers, String body) {
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
    headers.forEach(requestBuilder::addHeader);

    switch (method) {
      case POST_METHOD:
        log.info("Request body for build Request - {}", body);
        RequestBody requestBody = RequestBody.create(body, MediaType.parse(APPLICATION_JSON));
        requestBuilder.post(requestBody);
        return requestBuilder.build();
      case GET_METHOD:
        requestBuilder.get();
        return requestBuilder.build();
      default:
        throw new UnsupportedOperationException("Method " + method + " is not supported for DSL call");
    }
  }

  private Response executeRequest(OkHttpClient client, Request request) {
    try (okhttp3.Response response = client.newCall(request).execute()) {
      return Response.status(response.code()).entity(Objects.requireNonNull(response.body()).string()).build();
    } catch (Exception e) {
      log.error("Error in request execution through direct dsl client. Error = {}", e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Error occurred while fetching data").build())
          .build();
    }
  }
}
