/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.artifacts.docker.beans.DockerPublicImageTagResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

@OwnedBy(CDC)
public interface DockerRegistryRestClient {
  @GET("/token")
  Call<DockerRegistryToken> getGithubContainerRegistryToken(@Header("Authorization") String basicAuthHeader);

  //  https://auth.docker.io/token?service=registry.docker.io&scope=repository:samalba/my-app:pull,push
  @GET
  Call<DockerRegistryToken> getToken(@Header("Authorization") String basicAuthHeader, @Url String url,
      @Query("service") String service, @Query("scope") String scope);

  @GET
  Call<DockerRegistryToken> getPublicToken(
      @Url String url, @Query("service") String service, @Query("scope") String scope);

  @GET("/v2/{imageName}/tags/list")
  Call<DockerImageTagResponse> listImageTags(
      @Header("Authorization") String bearerAuthHeader, @Path(value = "imageName", encoded = true) String imageName);

  @GET("/v2") Call<Object> getApiVersion(@Header("Authorization") String bearerAuthHeader);

  // Added to handle special case for some custom docker repos
  @GET("/v2/") Call<Void> getApiVersionEndingWithForwardSlash(@Header("Authorization") String bearerAuthHeader);

  @GET
  Call<DockerImageTagResponse> listImageTagsByUrl(@Header("Authorization") String bearerAuthHeader, @Url String url);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v1+json, application/vnd.docker.distribution.manifest.v1+prettyjws, application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.v2+prettyjws, application/vnd.oci.image.index.v2+json, application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v2+json, application/vnd.oci.image.manifest.v1+json")
  @GET("/v2/{imageName}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  verifyImage(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "imageName", encoded = true) String imageName, @Path(value = "tag", encoded = true) String tag);

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

  @GET("/v2/repositories/{imageName}/tags")
  Call<DockerPublicImageTagResponse> listPublicImageTags(@Path(value = "imageName", encoded = true) String imageName,
      @Query("page") Integer pageNum, @Query("page_size") int pageSize);

  @GET("/v2/repositories/{imageName}/tags/{tagNumber}")
  Call<DockerPublicImageTagResponse.Result> getPublicImageTag(
      @Path(value = "imageName", encoded = true) String imageName,
      @Path(value = "tagNumber", encoded = true) String tagNumber);
}
