/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class NGLogStreamingClientFactory implements Provider<LogStreamingServiceRestClient> {
  String logStreamingServiceBaseUrl;

  @Override
  public LogStreamingServiceRestClient get() {
      OkHttpClient okHttpClient = Http.getOkHttpClient().newBuilder()
                                    .connectTimeout(5, TimeUnit.SECONDS)
                                    .readTimeout(10, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(logStreamingServiceBaseUrl))
                                    .retryOnConnectionFailure(true)
                                    .build();

    Gson gson = new GsonBuilder().setLenient().create();

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(logStreamingServiceBaseUrl)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();

    return retrofit.create(LogStreamingServiceRestClient.class);
  }
}
