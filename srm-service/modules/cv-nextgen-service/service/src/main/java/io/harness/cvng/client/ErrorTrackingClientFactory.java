/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.client;

import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.AllTrustingX509TrustManager;
import io.harness.security.ErrorTrackingAuthInterceptor;
import io.harness.security.ServiceTokenGenerator;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
public class ErrorTrackingClientFactory implements Provider<ErrorTrackingClient> {
  public static final ImmutableList<TrustManager> TRUST_ALL_CERTS = ImmutableList.of(new AllTrustingX509TrustManager());
  private static final int TIMEOUT = 60;

  private final String baseUrl;
  private final ServiceTokenGenerator tokenGenerator;
  private final String serviceSecret;

  public ErrorTrackingClientFactory(String baseUrl, String serviceSecret, ServiceTokenGenerator tokenGenerator) {
    this.baseUrl = baseUrl;
    this.tokenGenerator = tokenGenerator;
    this.serviceSecret = serviceSecret;
  }

  @Override
  public ErrorTrackingClient get() {
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(getUnsafeOkHttpClient())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(ErrorTrackingClient.class);
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      // Install the all-trusting trust manager
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
      // Create a ssl socket factory with our all-trusting manager
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(Http.connectionPool)
          .readTimeout(TIMEOUT, TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .addInterceptor(new ErrorTrackingAuthInterceptor(serviceSecret, tokenGenerator))
          .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
          .hostnameVerifier(new NoopHostnameVerifier())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}