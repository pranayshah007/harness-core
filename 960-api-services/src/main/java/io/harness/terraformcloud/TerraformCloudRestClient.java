/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.terraformcloud.model.ApplyData;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.PlanData;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.RunActionRequest;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.StateVersionOutputData;
import io.harness.terraformcloud.model.TerraformCloudResponse;
import io.harness.terraformcloud.model.WorkspaceData;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDP)
public interface TerraformCloudRestClient {
  @GET("api/v2/organizations")
  Call<TerraformCloudResponse<List<OrganizationData>>> listOrganizations(
      @Header("Authorization") String authorization, @Query("page[number]") int page);

  @GET("api/v2/organizations/{organization}/workspaces")
  Call<TerraformCloudResponse<List<WorkspaceData>>> listWorkspaces(@Header("Authorization") String authorization,
      @Path("organization") String organization, @Query("page[number]") int page);

  @Headers({"Content-Type: application/vnd.api+json"})
  @POST("api/v2/runs")
  Call<TerraformCloudResponse<RunData>> createRun(@Header("Authorization") String authorization, @Body RunRequest body);

  @Headers({"Content-Type: application/vnd.api+json"})
  @POST("api/v2/runs/{runId}/actions/apply")
  Call<Void> applyRun(
      @Header("Authorization") String authorization, @Path("runId") String runId, @Body RunActionRequest body);

  @Headers({"Content-Type: application/vnd.api+json"})
  @POST("api/v2/runs/{runId}/actions/discard")
  Call<Void> discardRun(
      @Header("Authorization") String authorization, @Path("runId") String runId, @Body RunActionRequest body);

  @Headers({"Content-Type: application/vnd.api+json"})
  @POST("api/v2/runs/{runId}/actions/force-execute")
  Call<Void> forceExecuteRun(@Header("Authorization") String authorization, @Path("runId") String runId);

  @GET("api/v2/runs/{runId}")
  Call<TerraformCloudResponse<RunData>> getRun(
      @Header("Authorization") String authorization, @Path("runId") String runId);

  @GET("api/v2/plans/{planId}")
  Call<TerraformCloudResponse<PlanData>> getPlan(
      @Header("Authorization") String authorization, @Path("planId") String planId);

  @GET("api/v2/applies/{applyId}")
  Call<TerraformCloudResponse<ApplyData>> getApply(
      @Header("Authorization") String authorization, @Path("applyId") String applyId);

  @GET("api/v2/runs/{runId}/policy-checks")
  Call<TerraformCloudResponse<List<PolicyCheckData>>> listPolicyChecks(
      @Header("Authorization") String authorization, @Path("runId") String runId, @Query("page[number]") int page);

  @GET("/api/v2/state-versions/{stateVersionId}/outputs")
  Call<TerraformCloudResponse<List<StateVersionOutputData>>> getStateVersionOutputs(
      @Header("Authorization") String authorization, @Path("stateVersionId") String stateVersionId,
      @Query("page[number]") int page);

  @Headers({"Content-Type: application/vnd.api+json"})
  @POST("api/v2/policy-checks/{policyChecksId}/actions/override")
  Call<Void> overridePolicyChecks(
      @Header("Authorization") String authorization, @Path("policyChecksId") String policyChecksId);
}
