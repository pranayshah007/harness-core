/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.gar;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;
import io.harness.artifacts.gar.beans.GarTags;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)

public interface GarRestClient {
  @GET("/v1/projects/{project}/locations/{region}/repositories/{repositories}/packages/{package}/tags")
  Call<GarPackageVersionResponse> listImageTags(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project, @Path(value = "region", encoded = true) String region,
      @Path(value = "repositories", encoded = true) String repositories, @Path(value = "package") String pkg,
      @Query(value = "pageSize", encoded = true) int pageSize,
      @Query(value = "pageToken", encoded = true) String pageToken);

  @GET("/v1/projects/{project}/locations/{region}/repositories/{repositories}/packages/{package}/tags/{tag}")
  Call<GarTags> getversioninfo(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project, @Path(value = "region", encoded = true) String region,
      @Path(value = "repositories", encoded = true) String repositories, @Path(value = "package") String pkg,
      @Path(value = "tag", encoded = true) String version);

  @GET("/v2/{project}/{repositories}/{package}/manifests/{tag}")
  Call<GarPackageVersionResponse> getImageManifest(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project,
      @Path(value = "repositories", encoded = true) String repositories, @Path(value = "package") String pkg,
      @Path(value = "tag", encoded = true) String version);
}
