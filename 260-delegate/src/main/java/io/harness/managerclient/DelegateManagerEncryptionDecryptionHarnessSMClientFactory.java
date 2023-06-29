/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.TokenGenerator;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class DelegateManagerEncryptionDecryptionHarnessSMClientFactory
    implements Provider<DelegateManagerEncryptionDecryptionHarnessSMClient> {
  private final String baseUrl;
  private final TokenGenerator tokenGenerator;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final OkHttpClient httpClient;

  @Inject
  public DelegateManagerEncryptionDecryptionHarnessSMClientFactory(
      final DelegateConfiguration configuration, final TokenGenerator tokenGenerator) {
    this.baseUrl = configuration.getManagerUrl();
    this.tokenGenerator = tokenGenerator;
    this.clientCertificateFilePath = configuration.getClientCertificateFilePath();
    this.clientCertificateKeyFilePath = configuration.getClientCertificateKeyFilePath();
    this.httpClient = this.getUnsafeOkHttpClient();
  }

  @Override
  public DelegateManagerEncryptionDecryptionHarnessSMClient get() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModule(new GuavaModule());
    objectMapper.registerModule(new JavaTimeModule());
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(this.baseUrl)
                            .client(httpClient)
                            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                            .build();
    return retrofit.create(DelegateManagerEncryptionDecryptionHarnessSMClient.class);
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      X509SslContextBuilder sslContextBuilder =
          new X509SslContextBuilder().trustManager(trustManager).secureRandom(new java.security.SecureRandom());

      if (StringUtils.isNotEmpty(this.clientCertificateFilePath)
          && StringUtils.isNotEmpty(this.clientCertificateKeyFilePath)) {
        X509KeyManager keyManager =
            new X509KeyManagerBuilder()
                .withClientCertificateFromFile(this.clientCertificateFilePath, this.clientCertificateKeyFilePath)
                .build();
        sslContextBuilder.keyManager(keyManager);
      }

      SSLContext sslContext = sslContextBuilder.build();

      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(Http.connectionPool)
          .retryOnConnectionFailure(true)
          .addInterceptor(new DelegateAuthInterceptor(this.tokenGenerator))
          .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
          .hostnameVerifier(new NoopHostnameVerifier())
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
