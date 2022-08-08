/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestoreclient.remote;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.ServiceTokenGenerator;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
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

public class FileStoreClientFactoryForCg implements Provider<FileStoreClient> {
  public static final ImmutableList<TrustManager> TRUST_ALL_CERTS =
      ImmutableList.of(new FileStoreNgClientX509TrustManager());
  private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(0, 10, TimeUnit.MINUTES);

  private String baseUrl;
  private String serviceSecret;
  private ServiceTokenGenerator serviceTokenGenerator;

  public FileStoreClientFactoryForCg(
      String baseUrl, String serviceSecret, ServiceTokenGenerator serviceTokenGenerator) {
    this.baseUrl = baseUrl;
    this.serviceSecret = serviceSecret;
    this.serviceTokenGenerator = serviceTokenGenerator;
  }

  @Override
  public FileStoreClient get() {
    if (isEmpty(baseUrl)) {
      return null;
    }
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(getUnsafeOkHttpClient())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(FileStoreClient.class);
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      // Install the all-trusting trust manager
      final SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, TRUST_ALL_CERTS.toArray(new TrustManager[1]), new java.security.SecureRandom());
      // Create an ssl socket factory with our all-trusting manager
      final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
      return Http.getOkHttpClientWithProxyAuthSetup()
          .connectionPool(CONNECTION_POOL)
          .connectTimeout(10, TimeUnit.SECONDS)
          .readTimeout(10, TimeUnit.SECONDS)
          .writeTimeout(10, TimeUnit.SECONDS) //.callTimeout(60, TimeUnit.SECONDS) // Call timeout is available in 3.12
          .retryOnConnectionFailure(false)
          .addInterceptor(new FileStoreAuthInterceptorForCg(serviceSecret, serviceTokenGenerator))
          .sslSocketFactory(sslSocketFactory, (X509TrustManager) TRUST_ALL_CERTS.get(0))
          .hostnameVerifier(new NoopHostnameVerifier())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
