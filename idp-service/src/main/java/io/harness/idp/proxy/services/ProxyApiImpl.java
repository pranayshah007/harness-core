/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@OwnedBy(HarnessTeam.IDP)
@RequestScoped
@NextGenManagerAuth
@NoArgsConstructor
@Slf4j
public class ProxyApiImpl implements ProxyApi {
  private ServiceTokenGenerator tokenGenerator;
  private ProxyAllowListConfig proxyAllowListConfig;
  private static final String FORWARDING_MESSAGE = "Forwarding request to [{}]";
  private static final String QUERY_PARAMS_DELIMITER = "\\?";
  private static final String PATH_DELIMITER = "/";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  @Inject
  public ProxyApiImpl(
      ServiceTokenGenerator tokenGenerator, @Named("proxyAllowList") ProxyAllowListConfig proxyAllowListConfig) {
    this.tokenGenerator = tokenGenerator;
    this.proxyAllowListConfig = proxyAllowListConfig;
  }

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response getProxyService(
      UriInfo uriInfo, HttpHeaders headers, String service, String url, String harnessAccount) {
    OkHttpClient client = getOkHttpClient(service);
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(getUrl(service))).newBuilder();

    filterAndCopyPath(service, uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).get();
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      Request request = requestBuilder.build();
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(request).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward request GET to manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response postProxyService(
      UriInfo uriInfo, HttpHeaders headers, String service, String url, String harnessAccount, String body) {
    OkHttpClient client = getOkHttpClient(service);
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(getUrl(service))).newBuilder();
    filterAndCopyPath(service, uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).post(new RequestBody() {
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
      log.error("Could not forward POST request to manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response putProxyService(
      UriInfo uriInfo, HttpHeaders headers, String service, String url, String harnessAccount, String body) {
    OkHttpClient client = getOkHttpClient(service);
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(getUrl(service))).newBuilder();
    filterAndCopyPath(service, uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).put(new RequestBody() {
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
      log.error("Could not forward PUT request to manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @IdpServiceAuthIfHasApiKey
  @Override
  public Response deleteProxyService(
      UriInfo uriInfo, HttpHeaders headers, String service, String url, String harnessAccount) {
    OkHttpClient client = getOkHttpClient(service);
    HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(getUrl(service))).newBuilder();
    filterAndCopyPath(service, uriInfo, urlBuilder);
    copyQueryParams(uriInfo, urlBuilder);

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build().toString()).delete();
    copyHeaders(headers, requestBuilder);

    okhttp3.Response response;
    try {
      log.info(FORWARDING_MESSAGE, urlBuilder);
      response = client.newCall(requestBuilder.build()).execute();
      return buildResponseObject(response);
    } catch (Exception e) {
      log.error("Could not forward DELETE request to manager", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @VisibleForTesting
  OkHttpClient getOkHttpClient(String service) {
    return new OkHttpClient()
        .newBuilder()
        .connectTimeout(proxyAllowListConfig.getServices().get(service).getClientConfig().getConnectTimeOutSeconds(),
            TimeUnit.SECONDS)
        .readTimeout(
            proxyAllowListConfig.getServices().get(service).getClientConfig().getReadTimeOutSeconds(), TimeUnit.SECONDS)
        .writeTimeout(proxyAllowListConfig.getServices().get(service).getClientConfig().getConnectTimeOutSeconds(),
            TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(
            new IdpAuthInterceptor(tokenGenerator, proxyAllowListConfig.getServices().get(service).getSecret()))
        .addInterceptor(getEncodingInterceptor())
        .build();
  }

  private String getUrl(String service) {
    return proxyAllowListConfig.getServices().get(service).getClientConfig().getBaseUrl();
  }

  private static Interceptor getEncodingInterceptor() {
    return chain -> chain.proceed(chain.request().newBuilder().header("Accept-Encoding", "identity").build());
  }

  private void filterAndCopyPath(String service, UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    String proxyPath = proxyAllowListConfig.getServices().get(service).getProxyPath();
    List<String> allowList = proxyAllowListConfig.getServices().get(service).getAllowList();
    String suffixUrl = uriInfo.getPath().split(proxyPath)[1];
    String path = suffixUrl.split(QUERY_PARAMS_DELIMITER)[0];
    filterPath(path, allowList);
    copyPath(path, urlBuilder);
  }

  private void filterPath(String paths, List<String> allowList) {
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

  private void copyPath(String path, HttpUrl.Builder urlBuilder) {
    for (String s : path.split(PATH_DELIMITER)) {
      urlBuilder.addPathSegment(s);
    }
  }

  private void copyQueryParams(UriInfo uriInfo, HttpUrl.Builder urlBuilder) {
    uriInfo.getQueryParameters().forEach(
        (key, values) -> values.forEach(value -> urlBuilder.addQueryParameter(key, value)));
  }

  private void copyHeaders(HttpHeaders headers, Request.Builder requestBuilder) {
    headers.getRequestHeaders().forEach((key, values) -> {
      if (!key.equals(IdpAuthInterceptor.AUTHORIZATION)) {
        values.forEach(value -> requestBuilder.header(key, value));
      }
    });
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
}
