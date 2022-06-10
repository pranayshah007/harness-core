/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import static io.harness.ng.core.CorrelationContext.getCorrelationIdInterceptor;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.VerificationAuthInterceptor;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@OwnedBy(HarnessTeam.CV)
public class VerificationManagerClientFactory implements Provider<VerificationManagerClient> {
  public static final ImmutableList<TrustManager> TRUST_ALL_CERTS =
      ImmutableList.of(new VerificationManagerClientX509TrustManager());

  private String baseUrl;
  private ServiceTokenGenerator tokenGenerator;

  public VerificationManagerClientFactory(String baseUrl, ServiceTokenGenerator tokenGenerator) {
    this.baseUrl = baseUrl;
    this.tokenGenerator = tokenGenerator;
  }

  @Override
  public VerificationManagerClient get() {
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(getUnsafeOkHttpClient())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(VerificationManagerClient.class);
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      // Install the all-trusting trust manager
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
      // Create an ssl socket factory with our all-trusting manager
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(new ConnectionPool(0, 5, TimeUnit.MINUTES))
          .readTimeout(30, TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .addInterceptor(new VerificationAuthInterceptor(tokenGenerator))
          .addInterceptor(getCorrelationIdInterceptor())
          .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
          .hostnameVerifier(new NoopHostnameVerifier())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
