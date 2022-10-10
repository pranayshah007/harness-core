/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.appspot;

import static io.harness.network.Http.getOkHttpClientBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.network.Http;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class AppSpotPricingClient {
  private static final String BASE_URL = "https://lepton.appspot.com/";
  private static final String ENDPOINT_URL = "skus?format=skuPage&currency=USD&filter=pd&offset=0&limit=1000";

  @ToString
  public static class Sku {
    public List<Sku> skus;
    public String description;
    public List<String> service_regions;
    public List<String> prices;
  }
  @ToString
  public static class ApiResponse {
    public List<Sku> skus;
  }

  private static Call appSpotPricingCall() {
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(120, TimeUnit.SECONDS)
                                    .readTimeout(120, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(BASE_URL))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Request request = new Request.Builder().url(BASE_URL + ENDPOINT_URL).build();
    return okHttpClient.newCall(request);
  }

  public static ApiResponse fetchParsedResponse() throws IOException {
    Gson gson = new Gson();
    try {
      return gson.fromJson(fetchJsonEndpoint(), ApiResponse.class);
    } catch (UnexpectedException | UnknownHostException ex) {
      log.warn("Exception while calling storage AppSpotPricing endpoint", ex);
    } catch (Exception ex) {
      log.warn("Exception while parsing storage AppSpotPricing response", ex);
    }

    // default response in case of network or parsing failure
    return gson.fromJson(fetchDefaultResponse(), ApiResponse.class);
  }

  public static String fetchJsonEndpoint() throws IOException {
    try (Response response = appSpotPricingCall().execute();) {
      if (response != null && response.isSuccessful()) {
        final String body = response.body().string();
        return cleanResponseBody(body);
      }
      throw new UnexpectedException(String.format("Response: [%s]", response));
    }
  }

  private static String fetchDefaultResponse() throws IOException {
    final URL savedPricingData = ApiResponse.class.getResource("/pricingdata/storagePricingData.txt");
    final String body = IOUtils.toString(savedPricingData, StandardCharsets.UTF_8);
    return cleanResponseBody(body);
  }

  private static String cleanResponseBody(final @NotNull String body) {
    // The response returned from appspot is intentionally bad formatted json :(, like
    // )
    // ]
    // }'
    // {
    // "message": "", ...
    return body.substring(5).trim();
  }
}
