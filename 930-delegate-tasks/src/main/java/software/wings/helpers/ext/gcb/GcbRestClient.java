/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcb;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.helpers.ext.gcb.models.BuildOperationDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildTriggers;
import software.wings.helpers.ext.gcb.models.RepoSource;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
public interface GcbRestClient {
  @POST("v1/projects/{projectId}/triggers/{triggerId}:run")
  Call<BuildOperationDetails> runTrigger(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "projectId") String projectID, @Path(value = "triggerId") String triggerID,
      @Body RepoSource repoSource);

  @GET("v1/projects/{projectId}/builds/{buildId}")
  Call<GcbBuildDetails> getBuild(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "projectId") String projectId, @Path(value = "buildId") String buildId);

  @POST("v1/projects/{projectId}/builds")
  Call<BuildOperationDetails> createBuild(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "projectId") String projectId, @Body GcbBuildDetails buildParams);

  @GET("v1/projects/{projectId}/triggers")
  Call<GcbBuildTriggers> getAllTriggers(
      @Header("Authorization") String bearerAuthHeader, @Path(value = "projectId") String projectId);

  @POST("v1/projects/{projectId}/builds/{buildId}:cancel")
  Call<GcbBuildDetails> cancelBuild(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "projectId") String projectId, @Path(value = "buildId") String buildId);
}
