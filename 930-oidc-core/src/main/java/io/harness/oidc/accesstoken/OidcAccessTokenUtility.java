/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class OidcAccessTokenUtility {
  public static io.harness.oidc.accesstoken.OidcAccessTokenResponse getOidcAccessToken(
      io.harness.oidc.accesstoken.OidcAccessTokenConfigStructure
          .OidcAccessTokenExchangeEndpoint oidcAccessTokenExchangeEndpoint,
      io.harness.oidc.accesstoken.OidcAccessTokenRequest oidcAccessTokenRequest) {
    // Create an OkHttpClient with any desired configurations (e.g., timeouts, interceptors)
    OkHttpClient httpClient = new OkHttpClient.Builder().build();

    // Create a Retrofit client with the base URL
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(String.valueOf(oidcAccessTokenExchangeEndpoint))
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(httpClient)
                            .build();

    // Create an instance of the API interface
    io.harness.oidc.accesstoken.OidcAccessTokenExchangeApi oidcAccessTokenExchangeApi =
        retrofit.create(io.harness.oidc.accesstoken.OidcAccessTokenExchangeApi.class);

    // Make the POST request and handle the response
    Call<io.harness.oidc.accesstoken.OidcAccessTokenResponse> call =
        oidcAccessTokenExchangeApi.exchangeToken(oidcAccessTokenRequest);

    try {
      Response<io.harness.oidc.accesstoken.OidcAccessTokenResponse> response = call.execute();
      if (response.isSuccessful()) {
        io.harness.oidc.accesstoken.OidcAccessTokenResponse oidcAccessTokenResponse = response.body();
        return oidcAccessTokenResponse;
      } else {
        log.error("Error encountered while exchanging OIDC Access Token");
      }
    } catch (IOException e) {
      log.error("Exception encountered while exchanging OIDC Access Token {} ", e);
    }

    return null;
  }
}
