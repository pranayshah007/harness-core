/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.docker.DockerApiTagsListResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDP)
public interface OciHelmDockerApiRestClient {
  @GET("/v2/{chartName}/tags/list")
  Call<DockerApiTagsListResponse> getTagsList(@Header("Authorization") String authorization,
      @Path(value = "chartName", encoded = true) String chartName, @Query("n") int pageSize,
      @Query("last") String lastTagFromPreviousPage);

  @GET("/v2/{chartName}/tags/list")
  Call<DockerApiTagsListResponse> getTagsListAsAnonymous(@Path(value = "chartName", encoded = true) String chartName,
      @Query("n") int pageSize, @Query("last") String lastTagFromPreviousPage);
}
