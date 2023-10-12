/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.tiserviceclient;

import io.harness.ci.commonconstants.CICommonEndpointConstants;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface TIServiceClient {
  @GET(CICommonEndpointConstants.TI_SERVICE_TOKEN_ENDPOINT)
  Call<String> generateToken(@Query("accountId") String accountId, @Header("X-Harness-Token") String globalToken);

  @GET(CICommonEndpointConstants.TI_SERVICE_REPORT_SUMMARY_ENDPOINT)
  Call<JsonObject> getSummaryReport(@Query("accountId") String accountId, @Query("orgId") String orgId,
      @Query("projectId") String projectId, @Query("pipelineId") String pipelineId, @Query("buildId") int buildId,
      @Query("report") String report, @Query("stageId") String stageId, @Query("stepId") String stepId,
      @Header("X-Harness-Token") String globalToken);

  @POST(CICommonEndpointConstants.TI_SERVICE_INTERNAL_CLEANUP_ENDPOINT)
  Call<String> clean(@Query("accountId") String accountId, @Header("X-Harness-Token") String globalToken);
}
