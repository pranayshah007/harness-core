/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.taskagent.client.delegate;

import io.harness.delegate.taskagent.servicediscovery.ServiceEndpoint;
import io.harness.exception.SslContextBuilderException;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.TokenGenerator;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.serializer.kryo.DelegateKryoConverterFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

// FixMe: This add deps to 980-commons & 920-delegate-beans which we don't want - create task specific tokens and auth
// mechanism
@RequiredArgsConstructor
public class DelegateCoreClientFactory {
  // FIXME: remove this after proto response api merges
  private final DelegateKryoConverterFactory kryoConverterFactory;
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModules(new Jdk8Module(), new GuavaModule(), new JavaTimeModule());

  private final TokenGenerator tokenGenerator;

  public DelegateCoreClient createDelegateCoreClient(final ServiceEndpoint serviceEndpoint) {
    final Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(String.format("http://%s:%d/api/", serviceEndpoint.getHost(), serviceEndpoint.getPort()))
            .client(getUnsafeOkHttpClient())
            // FIXME: use json converter
            .addConverterFactory(kryoConverterFactory)
            .build();
    return retrofit.create(DelegateCoreClient.class);
  }

  /**
   * Trusts all certificates - should only be used for POC and local development.
   */
  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      X509TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
      return getHttpClient(trustManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private OkHttpClient getHttpClient(X509TrustManager trustManager) throws SslContextBuilderException {
    final SSLContext sslContext = new X509SslContextBuilder().trustManager(trustManager).build();

    return Http.getOkHttpClientWithProxyAuthSetup()
        .hostnameVerifier(new NoopHostnameVerifier())
        .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
        .connectionPool(Http.connectionPool)
        .retryOnConnectionFailure(true)
        // FIXME: need auth interceptor
        //.addInterceptor(new DelegateAuthInterceptor(this.tokenGenerator))
        .readTimeout(1, TimeUnit.MINUTES)
        .build();
  }
}
