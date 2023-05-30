/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.network.Http.getUnsafeOkHttpClientBuilder;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class RancherRestClientFactory {
  private static final long CONNECTION_TIMEOUT_SECONDS = 60;
  private static final long READ_TIMEOUT_SECONDS = 60;
  private static final String BEARER_TOKEN_HEADER_PREFIX = "Bearer";
  private static final String BEARER_TOKEN = BEARER_TOKEN_HEADER_PREFIX + " %s";

  public RancherRestClient getRestClient(String rancherUrl, String bearerTokenValue) {
    OkHttpClient okHttpClient =
        getUnsafeOkHttpClientBuilder(rancherUrl, CONNECTION_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS)
            .addInterceptor(chain -> {
              Request newRequest = chain.request()
                                       .newBuilder()
                                       .addHeader("Authorization", format(BEARER_TOKEN, bearerTokenValue))
                                       .build();
              return chain.proceed(newRequest);
            })
            .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(rancherUrl)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(RancherRestClient.class);
  }
}
