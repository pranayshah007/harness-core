/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.client;

import io.harness.network.HttpClientFactory;
import io.harness.network.OkHttpConfig;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@RequiredArgsConstructor
public class LogServiceStackdriverClientFactory implements Provider<LogServiceStackdriverClient> {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .registerModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  private final String logServiceUrl;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final boolean trustAllCertificates;

  @Override
  public LogServiceStackdriverClient get() {
    final var httpConfig = OkHttpConfig.builder()
                               .clientCertificateFilePath(clientCertificateFilePath)
                               .clientCertificateKeyFilePath(clientCertificateKeyFilePath)
                               .trustAllCertificates(trustAllCertificates)
                               .build();
    final OkHttpClient httpClient = HttpClientFactory.create(httpConfig);

    return new Retrofit.Builder()
        .client(httpClient)
        .baseUrl(logServiceUrl)
        .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
        .build()
        .create(LogServiceStackdriverClient.class);
  }
}
