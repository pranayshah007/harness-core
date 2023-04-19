/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.gar;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;

@OwnedBy(HarnessTeam.CDC)
public interface GarDockerRestClient {
  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.v2+prettyjws, application/vnd.oci.image.index.v2+json, application/vnd.oci.image.manifest.v2+json")
  @GET("/v2/{project}/{repositories}/{package}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifest(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project,
      @Path(value = "repositories", encoded = true) String repositories, @Path(value = "package") String pkg,
      @Path(value = "tag", encoded = true) String version);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v1+json, application/vnd.docker.distribution.manifest.v1+prettyjws, application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json")
  @GET("/v2/{project}/{repositories}/{package}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifestV1(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project,
      @Path(value = "repositories", encoded = true) String repositories, @Path(value = "package") String pkg,
      @Path(value = "tag", encoded = true) String version);
}
