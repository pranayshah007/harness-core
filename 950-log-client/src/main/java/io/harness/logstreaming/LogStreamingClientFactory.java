/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.SslContextBuilderException;
import io.harness.network.Http;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class LogStreamingClientFactory implements Provider<LogStreamingClient> {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .registerModule(new JavaTimeModule())
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  private final String logStreamingServiceBaseUrl;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final boolean trustAllCertificates;

  public LogStreamingClientFactory(final String logStreamingServiceBaseUrl, final String clientCertificateFilePath,
      final String clientCertificateKeyFilePath, final boolean trustAllCertificates) {
    this.logStreamingServiceBaseUrl = logStreamingServiceBaseUrl;
    this.clientCertificateFilePath = clientCertificateFilePath;
    this.clientCertificateKeyFilePath = clientCertificateKeyFilePath;
    this.trustAllCertificates = trustAllCertificates;
    if (StringUtils.isEmpty(logStreamingServiceBaseUrl)) {
      log.warn("The property logStreamingServiceBaseUrl is missing or empty");
    }
  }

  @Override
  public LogStreamingClient get() {
    if (StringUtils.isBlank(logStreamingServiceBaseUrl)) {
      return null;
    }

    final OkHttpClient httpClient =
        this.trustAllCertificates ? this.getUnsafeOkHttpClient() : this.getSafeOkHttpClient();

    return new Retrofit.Builder()
        .client(httpClient)
        .baseUrl(logStreamingServiceBaseUrl)
        .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
        .build()
        .create(LogStreamingClient.class);
  }

  private OkHttpClient getSafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustDefaultTrustStore().build();
      return this.getOkHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      final X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      return this.getOkHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OkHttpClient getOkHttpClient(X509TrustManager trustManager)
      throws KeyManagerBuilderException, SslContextBuilderException {
    final var sslContextBuilder = new X509SslContextBuilder().trustManager(trustManager);

    if (StringUtils.isNotEmpty(this.clientCertificateFilePath)
        && StringUtils.isNotEmpty(this.clientCertificateKeyFilePath)) {
      final var keyManager =
          new X509KeyManagerBuilder()
              .withClientCertificateFromFile(this.clientCertificateFilePath, this.clientCertificateKeyFilePath)
              .build();
      sslContextBuilder.keyManager(keyManager);
    }

    final var sslContext = sslContextBuilder.build();

    return Http.getOkHttpClientWithProxyAuthSetup()
        .connectionPool(new ConnectionPool())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
        .build();
  }
}
