/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import io.harness.beans.entities.Execution;
import io.harness.common.IACMCommonEndpointConstants;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface IACMServiceClient {
  @GET(IACMCommonEndpointConstants.IACM_SERVICE_TOKEN_ENDPOINT)
  Call<JsonObject> generateToken(@Query("accountId") String accountId, @Header("Harness-Token") String globalToken);
  @GET(IACMCommonEndpointConstants.IACM_SERVICE_GET_WORKSPACE_ENDPOINT)
  Call<JsonObject> getWorkspaceInfo(@Path("org") String org, @Path("project") String project,
      @Path("workspaceId") String workspaceId, @Header("Harness-Token") String globalToken,
      @Header("Harness-Account") String accountId);
  @GET(IACMCommonEndpointConstants.IACM_SERVICE_GET_WORKSPACE_VARIABLES_ENDPOINT)
  Call<JsonArray> getWorkspaceVariables(@Path("org") String org, @Path("project") String project,
      @Path("workspaceId") String workspaceId, @Header("Harness-Token") String globalToken,
      @Header("Harness-Account") String accountId);

  @GET(IACMCommonEndpointConstants.IACM_SERVICE_GET_WORKSPACE_RESOUCES_ENDPOINT)
  Call<JsonObject> getWorkspaceResoures(@Path("org") String org, @Path("project") String project,
      @Path("workspaceId") String workspaceId, @Header("Harness-Token") String globalToken,
      @Header("Harness-Account") String accountId);

  @POST(IACMCommonEndpointConstants.IACM_SERVICE_POST_EXECUTION)
  Call<JsonObject> postIACMExecution(@Path("org") String org, @Path("project") String project,
      @Body Execution execution, @Header("Harness-Token") String globalToken,
      @Header("Harness-Account") String accountId);
}
