/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging.client;

import io.harness.network.HttpClientFactory;
import io.harness.network.OkHttpConfig;
import io.harness.security.TokenGenerator;
import io.harness.security.delegate.DelegateAuthInterceptor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@RequiredArgsConstructor
public class LoggingTokenClientFactory implements Provider<LoggingTokenClient> {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final String accountId;
  private final String delegateToken;
  private final String managerUrl;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final boolean trustAllCertificates;
  @Override
  public LoggingTokenClient get() {
    final var authInterceptor = new DelegateAuthInterceptor(new TokenGenerator(accountId, delegateToken));

    final var httpConfig = OkHttpConfig.builder()
                               .clientCertificateFilePath(clientCertificateFilePath)
                               .clientCertificateKeyFilePath(clientCertificateKeyFilePath)
                               .trustAllCertificates(trustAllCertificates)
                               .interceptor(authInterceptor)
                               .build();
    final OkHttpClient httpClient = HttpClientFactory.create(httpConfig);

    return new Retrofit.Builder()
        .client(httpClient)
        .baseUrl(managerUrl)
        .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
        .build()
        .create(LoggingTokenClient.class);
  }
}
