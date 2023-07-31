/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class NextGenServiceImpl implements NextGenService {
  public static final String AUTHORIZATION = "Authorization";
  private static final String SPACE = " ";

  private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  private String ngManagerServiceSecret;
  private ServiceTokenGenerator tokenGenerator;

  @Inject
  public NextGenServiceImpl(
      @Named("ngManagerServiceHttpClientConfig") ServiceHttpClientConfig ngManagerServiceHttpClientConfig,
      @Named("ngManagerServiceSecret") String ngManagerServiceSecret, ServiceTokenGenerator tokenGenerator) {
    this.ngManagerServiceHttpClientConfig = ngManagerServiceHttpClientConfig;
    this.ngManagerServiceSecret = ngManagerServiceSecret;
    this.tokenGenerator = tokenGenerator;
  }

  @Override
  public Response getRequest(UriInfo uriInfo, HttpHeaders headers, String path, String harnessAccount) {
    OkHttpClient client =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
            .readTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
            .writeTimeout(ngManagerServiceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();

    SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
    String authorizationToken = tokenGenerator.getServiceTokenWithDuration(
        ngManagerServiceSecret, Duration.ofHours(4), SecurityContextBuilder.getPrincipal());

    HttpUrl.Builder urlBuilder =
        Objects.requireNonNull(HttpUrl.parse(ngManagerServiceHttpClientConfig.getBaseUrl())).newBuilder();

    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build() + path).get();
    requestBuilder.addHeader(AUTHORIZATION, NG_MANAGER.getServiceId() + SPACE + authorizationToken);

    okhttp3.Response response;
    try {
      Request request = requestBuilder.build();
      log.info("Sending Request to NextGen Manager", urlBuilder);
      response = client.newCall(request).execute();
      return response;
    } catch (Exception e) {
      log.error("Could not forward request GET to ng manager", e);
      throw new InvalidRequestException("Failed to send Request");
    }
  }
}
