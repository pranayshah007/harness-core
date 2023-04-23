/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.ticket.clients;

import static io.harness.ng.core.CorrelationContext.getCorrelationIdInterceptor;

import io.harness.network.Http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class TicketServiceRestClientFactory implements Provider<TicketServiceRestClient> {
  private static final String MODULE_QUERY_PARAM_KEY = "module";
  private static final String MODULE_QUERY_PARAM_VALUE = "SRM";
  private String baseUrl;
  public TicketServiceRestClientFactory(String baseUrl) {
    this.baseUrl = baseUrl;
  }
  @Override
  public TicketServiceRestClient get() {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(getUnsafeOkHttpClient())
        .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
        .build()
        .create(TicketServiceRestClient.class);
  }

  private OkHttpClient getUnsafeOkHttpClient() {
    try {
      return new OkHttpClient.Builder()
          .connectionPool(Http.connectionPool)
          .readTimeout(60, TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .addInterceptor(getCorrelationIdInterceptor())
          .addInterceptor(getModuleQueryParamInterceptor())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  private static Interceptor getModuleQueryParamInterceptor() {
    return chain
        -> chain.proceed(chain.request()
                             .newBuilder()
                             .url(chain.request()
                                      .url()
                                      .newBuilder()
                                      .addQueryParameter(MODULE_QUERY_PARAM_KEY, MODULE_QUERY_PARAM_VALUE)
                                      .build())
                             .build());
  }
}
