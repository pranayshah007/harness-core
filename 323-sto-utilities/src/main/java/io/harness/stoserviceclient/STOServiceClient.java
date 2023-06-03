/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stoserviceclient;

import io.harness.common.STOCommonEndpointConstants;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface STOServiceClient {
  @GET(STOCommonEndpointConstants.STO_SERVICE_TOKEN_ENDPOINT)
  Call<JsonObject> generateTokenAllAccounts(@Header("X-Harness-Token") String globalToken);

  @GET(STOCommonEndpointConstants.STO_SERVICE_TOKEN_ENDPOINT)
  Call<JsonObject> generateToken(@Query("accountId") String accountId, @Header("X-Harness-Token") String globalToken);

  @GET(STOCommonEndpointConstants.STO_SERVICE_SCAN_RESULTS_ENDPOINT)
  Call<JsonObject> getOutputVariables(@Header("Authorization") String token, @Path("scanId") String scanId,
      @Query("accountId") String accountId, @Query("orgId") String orgId, @Query("projectId") String projectId);

  @GET(STOCommonEndpointConstants.STO_SERVICE_SCANS_ENDPOINT)
  Call<JsonObject> getScans(@Header("Authorization") String token, @Query("accountId") String accountId,
      @Query("executionId") String executionId, @Query("page") String page, @Query("pageSize") String pageSize);

  @GET(STOCommonEndpointConstants.STO_SERVICE_USAGE_ALL_ACCOUNTS_ENDPOINT)
  Call<JsonObject> getUsageAllAccounts(@Header("Authorization") String token, @Query("timestamp") String timestamp);
}
