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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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

  @Override
  public Response call(String accountIdentifier, ApiRequestDetails apiRequestDetails) {
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

  private OkHttpClient buildOkHttpClient() {
    return new OkHttpClient()
        .newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
