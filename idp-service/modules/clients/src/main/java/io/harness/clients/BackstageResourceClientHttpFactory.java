/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.clients;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.network.Http.getSslContext;
import static io.harness.network.Http.getTrustManagers;

import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.protobuf.ExtensionRegistryLite;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(IDP)
public class BackstageResourceClientHttpFactory implements Provider<BackstageResourceClient> {
  private final ServiceHttpClientConfig backstageClientConfig;
  private final OkHttpClient httpClient;
  private static final ObjectMapper mapper = new ObjectMapper()
                                                 .registerModule(new Jdk8Module())
                                                 .registerModule(new GuavaModule())
                                                 .registerModule(new JavaTimeModule());
  public BackstageResourceClientHttpFactory(ServiceHttpClientConfig backstageClientConfig) {
    this.backstageClientConfig = backstageClientConfig;
    this.httpClient = this.getSafeOkHttpClient();
  }
  @Override
  public BackstageResourceClient get() {
    var retrofit =
        new Retrofit.Builder()
            .baseUrl(this.backstageClientConfig.getBaseUrl())
            .client(httpClient)
            .addConverterFactory(ProtoConverterFactory.createWithRegistry(ExtensionRegistryLite.newInstance()))
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .build();
    return retrofit.create(BackstageResourceClient.class);
  }

  private OkHttpClient getSafeOkHttpClient() {
    try {
      return this.getHttpClient();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OkHttpClient getHttpClient() {
    return Http.getOkHttpClientWithProxyAuthSetup()
        .hostnameVerifier(new NoopHostnameVerifier())
        .sslSocketFactory(getSslContext().getSocketFactory(), (X509TrustManager) getTrustManagers()[0])
        .connectTimeout(backstageClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
        .readTimeout(backstageClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
        .addInterceptor(new BackstageAuthInterceptor())
        .build();
  }
}
