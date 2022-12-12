package io.harness.delegate.queueservice;

import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.hsqs.client.HsqsServiceClient;
import io.harness.network.Http;

import software.wings.app.MainConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HQueueServiceClientFactory implements Provider<HsqsServiceClient> {
  @Inject private MainConfiguration mainConfiguration;

  @Override
  public HsqsServiceClient get() {
    String url = mainConfiguration.getQueueServiceConfig().getQueueServiceConfig().getBaseUrl();
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(5, TimeUnit.SECONDS)
                                    .readTimeout(10, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();

    Gson gson = new GsonBuilder().setLenient().create();

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();

    return retrofit.create(HsqsServiceClient.class);
  }
}
