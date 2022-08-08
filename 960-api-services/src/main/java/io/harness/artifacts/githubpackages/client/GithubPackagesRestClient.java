/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.client;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersionsResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDC)
public class GithubPackagesRestClient {
  @GET("/user/packages/container/{packageName}/versions")
  Call<GithubPackagesVersionsResponse> listVersionsForPackages(
      @Path(value = "packageName", encoded = true) String packageName, @Query("page") Integer pageNum,
      @Query("page_size") int pageSize);
}
