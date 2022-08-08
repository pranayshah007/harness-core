/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;
import rx.Observable;

public interface AzureManagementRestClient {
  String APP_VERSION = "2020-10-01";

  @GET("providers/Microsoft.Management/managementGroups?api-version=2020-10-01")
  Observable<Response<ResponseBody>> listManagementGroups(@Header("Authorization") String bearerAuthHeader);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET
  Observable<Response<ResponseBody>> listNext(
      @Header("Authorization") String bearerAuthHeader, @Url String nextUrl, @Query("api-version") String appVersion);

  @GET("subscriptions/{subscriptionId}/tagNames?api-version=2020-10-01")
  Observable<Response<ResponseBody>> listTags(
      @Header("Authorization") String bearerAuthHeader, @Path("subscriptionId") String subscriptionId);
}
