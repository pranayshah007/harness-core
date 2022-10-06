/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.commandlibrary.client;

import static io.harness.commandlibrary.common.CommandLibraryConstants.MANAGER_CLIENT_ID;
import static io.harness.ng.core.CorrelationContext.getCorrelationIdInterceptor;
import static io.harness.request.RequestContextFilter.getRequestContextInterceptor;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.JsonSubtypeResolver;

import software.wings.app.MainConfiguration;
import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class CommandLibraryServiceHttpClientFactory implements Provider<CommandLibraryServiceHttpClient> {
  private final String baseUrl;
  private final ServiceTokenGenerator tokenGenerator;
  private boolean publishingAllowed;
  private final String publishingSecret;
  @Inject private MainConfiguration mainConfiguration;

  public CommandLibraryServiceHttpClientFactory(
      String baseUrl, ServiceTokenGenerator tokenGenerator, boolean publishingAllowed, String publishingSecret) {
    this.baseUrl = baseUrl;
    this.tokenGenerator = tokenGenerator;
    this.publishingAllowed = publishingAllowed;
    this.publishingSecret = publishingSecret;
  }

  @Override
  public CommandLibraryServiceHttpClient get() {
    log.info(" Create Command Library service retrofit client");
    ObjectMapper objectMapper = getObjectMapper();
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .client(getUnsafeOkHttpClient(baseUrl))
                                  .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                                  .build();
    return retrofit.create(CommandLibraryServiceHttpClient.class);
  }

  @NotNull
  @VisibleForTesting
  ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModule(new GuavaModule());
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.setSubtypeResolver(new JsonSubtypeResolver(objectMapper.getSubtypeResolver()));
    objectMapper.setConfig(objectMapper.getSerializationConfig().withView(JsonViews.Public.class));
    objectMapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);

    return objectMapper;
  }

  private OkHttpClient getUnsafeOkHttpClient(String baseUrl) {
    try {
      return Http.getOkHttpClient(baseUrl, false, 15, 15).newBuilder()
          .connectionPool(new ConnectionPool())
          .retryOnConnectionFailure(true)
          .addInterceptor(getAuthorizationInterceptor())
          .addInterceptor(getCorrelationIdInterceptor())
          .addInterceptor(getRequestContextInterceptor())
          .build();

    } catch (Exception e) {
      throw new GeneralException("error while creating okhttp client for Command library service", e);
    }
  }

  @NotNull
  private Interceptor getAuthorizationInterceptor() {
    final Supplier<String> secretKeyForManageSupplier = this::getServiceSecretForManager;
    return chain -> {
      String token = tokenGenerator.getServiceToken(secretKeyForManageSupplier.get());
      Request request = chain.request();
      return chain.proceed(
          request.newBuilder().header("Authorization", MANAGER_CLIENT_ID + StringUtils.SPACE + token).build());
    };
  }

  @VisibleForTesting
  String getServiceSecretForManager() {
    final String managerToCommandLibraryServiceSecret =
        mainConfiguration.getCommandLibraryServiceConfig().getManagerToCommandLibraryServiceSecret();

    if (StringUtils.isNotBlank(managerToCommandLibraryServiceSecret)) {
      return managerToCommandLibraryServiceSecret.trim();
    }
    throw new InvalidRequestException("no secret key for client : " + MANAGER_CLIENT_ID);
  }
}
