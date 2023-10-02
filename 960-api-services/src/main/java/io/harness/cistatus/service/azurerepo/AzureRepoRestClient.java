/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.azurerepo;

import io.harness.cistatus.StatusCreationResponse;

import java.util.Map;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface AzureRepoRestClient {
  @POST("{organization}/{project}/_apis/git/repositories/{repositoryId}/commits/{commitId}/statuses?api-version=6.0")
  Call<StatusCreationResponse> createStatus(@Header("Authorization") String authorization,
      @Path("organization") String organization, @Path("project") String project,
      @Path("repositoryId") String repositoryId, @Path("commitId") String commitId,
      @Body Map<String, Object> parameters);

  @POST(
      "{organization}/{project}/_apis/git/repositories/{repositoryId}/pullRequests/{pullRequestId}/statuses?api-version=4.1-preview.1")
  Call<StatusCreationResponse>
  createPRStatus(@Header("Authorization") String authorization, @Path("organization") String organization,
      @Path("project") String project, @Path("repositoryId") String repositoryId,
      @Path("pullRequestId") String pullRequestId, @Body Map<String, Object> parameters);

  @PATCH("{organization}/{project}/_apis/git/repositories/{repositoryId}/pullrequests/{prNumber}?api-version=6.0")
  @Headers("Accept: application/json")
  Call<Object> mergePR(@Header("Authorization") String authorization, @Path("organization") String organization,
      @Path("project") String project, @Path("repositoryId") String repositoryId, @Path("prNumber") String prNumber,
      @Body RequestBody parameters);
}
