/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pricing.client;

import io.harness.network.Http;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class CloudInfoPricingHttpClientFactory implements Provider<CloudInfoPricingClient> {
  private final ServiceHttpClientConfig httpClientConfig;

  public CloudInfoPricingHttpClientFactory(ServiceHttpClientConfig httpClientConfig) {
    this.httpClientConfig = httpClientConfig;
  }

  @Override
  public CloudInfoPricingClient get() {
    log.info("BASE_CLOUD_INFO_PRICING_SERVICE_URL: {}", httpClientConfig.getBaseUrl());

      OkHttpClient okHttpClient = Http.getDefaultOkHttpClient().newBuilder()
                                    .connectTimeout(httpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
                                    .readTimeout(httpClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(httpClientConfig.getBaseUrl()))
                                    .retryOnConnectionFailure(true)
                                    .build();

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(httpClientConfig.getBaseUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();

    return retrofit.create(CloudInfoPricingClient.class);
  }
}
