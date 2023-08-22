/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.SslContextBuilderException;
import io.harness.network.FibonacciBackOff;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.serializer.kryo.DelegateKryoConverterFactory;
import io.harness.version.VersionInfoManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.protobuf.ExtensionRegistryLite;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request.Builder;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateAgentManagerClientFactory implements Provider<DelegateAgentManagerClient> {
  private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(16, 5, TimeUnit.MINUTES);
  private final VersionInfoManager versionInfoManager;
  private final DelegateKryoConverterFactory kryoConverterFactory;
  private final String baseUrl;
  private final String clientCertificateFilePath;
  private final String clientCertificateKeyFilePath;
  private final OkHttpClient httpClient;
  private final DelegateAuthInterceptor authInterceptor;

  @Inject
  public DelegateAgentManagerClientFactory(final DelegateConfiguration configuration,
      final VersionInfoManager versionInfoManager, final DelegateKryoConverterFactory kryoConverterFactory,
      final DelegateAuthInterceptor authInterceptor) {
    this.baseUrl = configuration.getManagerUrl();
    this.clientCertificateFilePath = configuration.getClientCertificateFilePath();
    this.clientCertificateKeyFilePath = configuration.getClientCertificateKeyFilePath();
    this.versionInfoManager = versionInfoManager;
    this.kryoConverterFactory = kryoConverterFactory;
    this.authInterceptor = authInterceptor;
    this.httpClient =
        configuration.isTrustAllCertificates() ? this.getUnsafeOkHttpClient() : this.getSafeOkHttpClient();
  }

  @Override
  public DelegateAgentManagerClient get() {
    final ObjectMapper objectMapper = new ObjectMapper()
                                          .registerModule(new Jdk8Module())
                                          .registerModule(new GuavaModule())
                                          .registerModule(new JavaTimeModule());

    ExtensionRegistryLite registryLite = ExtensionRegistryLite.newInstance();
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(this.baseUrl)
                            .client(httpClient)
                            .addConverterFactory(this.kryoConverterFactory)
                            .addConverterFactory(ProtoConverterFactory.createWithRegistry(registryLite))
                            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                            .build();
    return retrofit.create(DelegateAgentManagerClient.class);
  }

  private OkHttpClient getSafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustDefaultTrustStore().build();
      return this.getHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Trusts all certificates - should only be used for local development.
   */
  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      return this.getHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OkHttpClient getHttpClient(X509TrustManager trustManager)
      throws KeyManagerBuilderException, SslContextBuilderException {
    X509SslContextBuilder sslContextBuilder = new X509SslContextBuilder().trustManager(trustManager);

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
        .hostnameVerifier(new NoopHostnameVerifier())
        .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
        .connectionPool(CONNECTION_POOL)
        .retryOnConnectionFailure(true)
        .addInterceptor(authInterceptor)
        .addInterceptor(chain -> {
          Builder request = chain.request().newBuilder().addHeader(
              "User-Agent", "delegate/" + this.versionInfoManager.getVersionInfo().getVersion());
          return chain.proceed(request.build());
        })
        .addInterceptor(chain -> FibonacciBackOff.executeForEver(() -> chain.proceed(chain.request())))
        // During this call we not just query the task but we also obtain the secret on the manager side
        // we need to give enough time for the call to finish.
        .readTimeout(5, TimeUnit.MINUTES)
        .build();
  }
}
