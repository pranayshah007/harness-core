/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service;

import io.harness.cistatus.GithubAppTokenCreationResponse;
import io.harness.cistatus.StatusCreationResponse;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface GithubRestClient {
  @POST("app/installations/{installation_id}/access_tokens")
  @Headers("Accept: application/vnd.github.v3+json")
  Call<GithubAppTokenCreationResponse> createAccessToken(
      @Header("Authorization") String authorization, @Path("installation_id") String installationId);

  @POST("app/installations/{installation_id}/access_tokens")
  @Headers("Accept: application/vnd.github.machine-man-preview+json")
  Call<GithubAppTokenCreationResponse> createAccessTokenForGithubEnterprise(
      @Header("Authorization") String authorization, @Path("installation_id") String installationId);

  @POST("repos/{owner}/{repo}/statuses/{sha}")
  @Headers("Accept: application/vnd.github.v3+json")
  Call<StatusCreationResponse> createStatus(@Header("Authorization") String authorization, @Path("owner") String owner,
      @Path("repo") String repo, @Path("sha") String sha, @Body Map<String, Object> parameters);

  @GET("/repos/{owner}/{repo}/pulls/{pull_number}")
  @Headers("Accept: application/vnd.github.v3+json")
  Call<Object> findPR(@Header("Authorization") String authorization, @Path("owner") String owner,
      @Path("repo") String repo, @Path("pull_number") String pullNumber);

  @PUT("repos/{owner}/{repo}/pulls/{pull_number}/merge")
  @Headers("Accept: application/vnd.github.v3+json")
  Call<Object> mergePR(@Header("Authorization") String authorization, @Path("owner") String owner,
      @Path("repo") String repo, @Path("pull_number") String pullNumber);
}
