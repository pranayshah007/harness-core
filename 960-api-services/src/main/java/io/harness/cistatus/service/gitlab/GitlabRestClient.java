/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.gitlab;

import io.harness.cistatus.StatusCreationResponse;

import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface GitlabRestClient {
  @POST("v4/projects/{id}/statuses/{sha}/")
  Call<StatusCreationResponse> createStatus(@Header("Authorization") String authorization, @Path("id") String id,
      @Path("sha") String sha, @Query("state") String state, @Query("context") String context,
      @Query("description") String description, @Query("target_url") String target_url);

  @PUT("api/v4/projects/{project}/merge_requests/{prNumber}/merge")
  Call<Object> mergePR(@Header("PRIVATE-TOKEN") String token, @Path("project") String project,
      @Path("prNumber") String prNumber, @Query("should_remove_source_branch") boolean should_remove_source_branch);
}
