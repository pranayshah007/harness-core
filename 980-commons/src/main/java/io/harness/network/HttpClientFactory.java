/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.SslContextBuilderException;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;

import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;
import lombok.experimental.UtilityClass;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public class HttpClientFactory {
  public OkHttpClient create(final OkHttpConfig config) {
    return config.isTrustAllCertificates() ? getUnsafeOkHttpClient(config) : getSafeOkHttpClient(config);
  }

  private static OkHttpClient getSafeOkHttpClient(final OkHttpConfig config) {
    try {
      final var trustManager = new X509TrustManagerBuilder().trustDefaultTrustStore().build();
      return getOkHttpClient(trustManager, config);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OkHttpClient getUnsafeOkHttpClient(final OkHttpConfig config) {
    try {
      final var trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      return getOkHttpClient(trustManager, config);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OkHttpClient getOkHttpClient(final X509TrustManager trustManager, final OkHttpConfig config)
      throws KeyManagerBuilderException, SslContextBuilderException {
    final var sslContextBuilder = new X509SslContextBuilder().trustManager(trustManager);

    if (StringUtils.isNotEmpty(config.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(config.getClientCertificateKeyFilePath())) {
      final var keyManager = new X509KeyManagerBuilder()
                                 .withClientCertificateFromFile(
                                     config.getClientCertificateFilePath(), config.getClientCertificateKeyFilePath())
                                 .build();
      sslContextBuilder.keyManager(keyManager);
    }

    final var sslContext = sslContextBuilder.build();

    final var httpBuilder = Http.getOkHttpClientWithProxyAuthSetup()
                                .connectionPool(new ConnectionPool())
                                .connectTimeout(config.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                                .readTimeout(config.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                                .retryOnConnectionFailure(true)
                                .sslSocketFactory(sslContext.getSocketFactory(), trustManager);

    config.getInterceptors().forEach(httpBuilder::addInterceptor);
    return httpBuilder.build();
  }
}
