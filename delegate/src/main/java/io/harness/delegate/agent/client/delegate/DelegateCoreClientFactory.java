/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.agent.client.delegate;

import io.harness.delegate.agent.servicediscovery.ServiceEndpoint;
import io.harness.exception.SslContextBuilderException;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

// FixMe: This add deps to 980-commons which we don't want after POC
public class DelegateCoreClientFactory {
  final static ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModules(new Jdk8Module(), new GuavaModule(), new JavaTimeModule());
  final static OkHttpClient HTTP_CLIENT = getUnsafeOkHttpClient();

  public static DelegateCoreClient createDelegateCoreClient(final ServiceEndpoint serviceEndpoint) {
    final HttpUrl baseUrl =
        new HttpUrl.Builder().host(serviceEndpoint.getHost()).port(serviceEndpoint.getPort()).build();

    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(baseUrl)
                                  .client(HTTP_CLIENT)
                                  .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                                  .build();
    return retrofit.create(DelegateCoreClient.class);
  }

  /**
   * Trusts all certificates - should only be used for POC and local development.
   */
  private static OkHttpClient getUnsafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      return getHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static OkHttpClient getHttpClient(X509TrustManager trustManager) throws SslContextBuilderException {
    final SSLContext sslContext = new X509SslContextBuilder().trustManager(trustManager).build();

    return Http.getOkHttpClientWithProxyAuthSetup()
        .hostnameVerifier(new NoopHostnameVerifier())
        .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
        .connectionPool(Http.connectionPool)
        .retryOnConnectionFailure(true)
        // API is public for POC
        //            .addInterceptor(new io.harness.managerclient.DelegateAuthInterceptor(this.tokenGenerator))
        .readTimeout(1, TimeUnit.MINUTES)
        .build();
  }
}
