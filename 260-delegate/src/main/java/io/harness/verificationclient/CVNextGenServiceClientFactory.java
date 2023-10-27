/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.verificationclient;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.TokenGenerator;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.security.delegate.DelegateAuthInterceptor;

import com.google.inject.Provider;
import io.serializer.HObjectMapper;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
@OwnedBy(HarnessTeam.CV)
public class CVNextGenServiceClientFactory implements Provider<CVNextGenServiceClient> {
  private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(20, 1, TimeUnit.MINUTES);

  private final String baseUrl;
  private final TokenGenerator tokenGenerator;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;

  // As of now ignored (always trusts all certs)
  private final boolean trustAllCertificates;

  public CVNextGenServiceClientFactory(String baseUrl, TokenGenerator delegateTokenGenerator,
      String clientCertificateFilePath, String clientCertificateKeyFilePath, boolean trustAllCertificates) {
    this.baseUrl = baseUrl;
    this.tokenGenerator = delegateTokenGenerator;
    this.clientCertificateFilePath = clientCertificateFilePath;
    this.clientCertificateKeyFilePath = clientCertificateKeyFilePath;
    this.trustAllCertificates = trustAllCertificates;
  }

  @Override
  public CVNextGenServiceClient get() {
    if (isEmpty(this.baseUrl)) {
      return null;
    }
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(this.baseUrl)
                            .client(getUnsafeOkHttpClient())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(CVNextGenServiceClient.class);
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

      // https://www.baeldung.com/okhttp-timeouts
      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(CONNECTION_POOL)
          .connectTimeout(10, TimeUnit.SECONDS)
          .readTimeout(10, TimeUnit.SECONDS)
          .writeTimeout(10, TimeUnit.SECONDS) //.callTimeout(60, TimeUnit.SECONDS) // Call timeout is available in 3.12
          .retryOnConnectionFailure(false)
          .addInterceptor(new DelegateAuthInterceptor(this.tokenGenerator))
          .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
          .hostnameVerifier(new NoopHostnameVerifier())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
