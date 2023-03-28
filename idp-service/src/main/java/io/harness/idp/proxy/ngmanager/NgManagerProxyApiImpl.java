/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.ngmanager;

import static io.harness.idp.proxy.ngmanager.NgManagerAllowList.USERS;
import static io.harness.idp.proxy.ngmanager.NgManagerAllowList.USER_GROUPS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.PmsAuthInterceptor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@RequestScoped
public class NgManagerProxyApiImpl implements NgManagerProxyApi {
  private static final String PROXY_PATH = "v1/idp-proxy/ng-manager";
  private static final String PATH_DELIMITER = "/";
  private static final String QUERY_PARAMS_DELIMITER = "\\?";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String FORWARDING_MESSAGE = "Forwarding request to [{}]";
  @Inject @Named("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @Inject @Named("ngManagerServiceSecret") private String ngManagerServiceSecret;

  private static final List<String> allowList = Arrays.asList(USERS, USER_GROUPS);

  @Override
  public Response deleteProxyNgManager(UriInfo uriInfo, HttpHeaders headers, String url, String harnessAccount) {
    OkHttpClient client = getOkHttpClient();
    HttpUrl.Builder urlBuilder =
        Objects.requireNonNull(HttpUrl.parse(ngManagerServiceHttpClientConfig.getBaseUrl())).newBuilder();
    filterAndCopyPath(uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).delete();
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(requestBuilder.build()).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward DELETE request to ng manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getProxyNgManager(UriInfo uriInfo, HttpHeaders headers, String url, String harnessAccount) {
    OkHttpClient client = getOkHttpClient();
    HttpUrl.Builder urlBuilder =
        Objects.requireNonNull(HttpUrl.parse(ngManagerServiceHttpClientConfig.getBaseUrl())).newBuilder();
    filterAndCopyPath(uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).get();
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(requestBuilder.build()).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward request GET to ng manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response postProxyNgManager(
      UriInfo uriInfo, HttpHeaders headers, String url, String harnessAccount, String body) {
    OkHttpClient client = getOkHttpClient();
    HttpUrl.Builder urlBuilder =
        Objects.requireNonNull(HttpUrl.parse(ngManagerServiceHttpClientConfig.getBaseUrl())).newBuilder();
    filterAndCopyPath(uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder =
        new Request.Builder().url(urlBuilder.build().toString()).get().post(new RequestBody() {
          @Nullable
          @Override
          public MediaType contentType() {
            return MediaType.parse(headers.getMediaType().toString());
          }

          @Override
          public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
            bufferedSink.write(body.getBytes());
          }
        });
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(requestBuilder.build()).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward POST request to ng manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response putProxyNgManager(
      UriInfo uriInfo, HttpHeaders headers, String url, String harnessAccount, String body) {
    OkHttpClient client = getOkHttpClient();
    HttpUrl.Builder urlBuilder =
        Objects.requireNonNull(HttpUrl.parse(ngManagerServiceHttpClientConfig.getBaseUrl())).newBuilder();
    filterAndCopyPath(uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder =
        new Request.Builder().url(urlBuilder.build().toString()).get().put(new RequestBody() {
          @Nullable
          @Override
          public MediaType contentType() {
            return MediaType.parse(headers.getMediaType().toString());
          }

          @Override
          public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {
            bufferedSink.write(body.getBytes());
          }
        });
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(requestBuilder.build()).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward PUT request to ng manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  private Response buildResponseObject(okhttp3.Response response) throws IOException {
    Object entity = null;
    if (response.body() != null) {
      entity = response.body().string();
    }
    return Response.status(response.code())
        .entity(entity)
        .header(CONTENT_TYPE_HEADER, javax.ws.rs.core.MediaType.APPLICATION_JSON)
        .build();
  }

  private OkHttpClient getOkHttpClient() {
    return new OkHttpClient()
        .newBuilder()
        .connectTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
        .readTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
        .writeTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(new PmsAuthInterceptor(ngManagerServiceSecret))
        .build();
  }

  private void filterAndCopyPath(UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    String suffixUrl = uriInfo.getPath().split(PROXY_PATH)[1];
    String path = suffixUrl.split(QUERY_PARAMS_DELIMITER)[0];
    filterPath(path);
    copyPath(path, urlBuilder);
  }

  private void copyPath(String path, HttpUrl.Builder urlBuilder) {
    for (String s : path.split(PATH_DELIMITER)) {
      urlBuilder.addPathSegment(s);
    }
  }

  private void filterPath(String paths) {
    boolean isAllowed = false;
    for (String allowedPath : allowList) {
      if (paths.startsWith(allowedPath)) {
        isAllowed = true;
        break;
      }
    }
    if (!isAllowed) {
      throw new InvalidRequestException(String.format("Path %s is not allowed", paths));
    }
  }

  private void copyQueryParams(UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    uriInfo.getQueryParameters().forEach(
        (key, values) -> values.forEach(value -> urlBuilder.addQueryParameter(key, value)));
  }

  private void copyHeaders(HttpHeaders headers, Request.Builder requestBuilder) {
    headers.getRequestHeaders().forEach((key, values) -> {
      if (!key.equals("Authorization")) {
        values.forEach(value -> requestBuilder.header(key, value));
      }
    });
  }
}
