/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.gcr;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;

/**
 * Created by brett on 8/2/17
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface GcrRestClient {
  @GET("/v2/{imageName}/tags/list")
  Call<GcrImageTagResponse> listImageTags(
      @Header("Authorization") String bearerAuthHeader, @Path(value = "imageName", encoded = true) String imageName);

  @GET("/v2/_catalog")
  Call<GcrImageTagResponse> listCatalogs(
      @Header("Authorization") String bearerAuthHeader, @Path(value = "imageName", encoded = true) String imageName);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v1+json, application/vnd.docker.distribution.manifest.v1+prettyjws, application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json")
  @GET("/v2/{imageName}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifest(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "imageName", encoded = true) String imageName, @Path(value = "tag", encoded = true) String tag);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.v2+prettyjws, application/vnd.oci.image.index.v2+json, application/vnd.oci.image.manifest.v2+json")
  @GET("/v2/{imageName}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifestV2(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "imageName", encoded = true) String imageName, @Path(value = "tag", encoded = true) String tag);
}
