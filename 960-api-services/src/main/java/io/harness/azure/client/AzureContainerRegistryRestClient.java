/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.azure.model.AzureConstants;

import software.wings.helpers.ext.azure.AcrGetRepositoriesResponse;
import software.wings.helpers.ext.azure.AcrGetRepositoryTagsResponse;
import software.wings.helpers.ext.azure.AcrGetTokenResponse;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AzureContainerRegistryRestClient {
  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("/v2/{repositoryName}/tags/list?n=500&orderby=timedesc")
  Call<AcrGetRepositoryTagsResponse> listRepositoryTags(@Header("Authorization") String authHeader,
      @Path(value = "repositoryName", encoded = true) String repositoryName);

  @Headers({"Content-Type: application/json; charset=utf-8", "accept-language: en-US"})
  @GET("/v2/_catalog")
  Call<AcrGetRepositoriesResponse> listRepositories(
      @Header("Authorization") String authHeader, @Query("last") String last);

  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "accept-language: en-US"})
  @POST("/oauth2/exchange")
  @FormUrlEncoded
  Call<AcrGetTokenResponse> getRefreshToken(@Field(AzureConstants.GRANT_TYPE) String grantType,
      @Field(AzureConstants.ACCESS_TOKEN) String accessToken, @Field(AzureConstants.SERVICE) String service);

  @Headers({"Content-Type: application/x-www-form-urlencoded; charset=utf-8", "accept-language: en-US"})
  @POST("/oauth2/token")
  @FormUrlEncoded
  Call<AcrGetTokenResponse> getAccessToken(@Field(AzureConstants.GRANT_TYPE) String grantType,
      @Field(AzureConstants.REFRESH_TOKEN) String refreshToken, @Field(AzureConstants.SERVICE) String service,
      @Field(AzureConstants.SCOPE) String scope);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v1+json, application/vnd.docker.distribution.manifest.v1+prettyjws, application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json")
  @GET("/v2/{repository}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifestV1(@Header("Authorization") String authHeader,
      @Path(value = "repository", encoded = true) String repository, @Path(value = "tag", encoded = true) String tag);

  @Headers(
      "Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.docker.distribution.manifest.v2+prettyjws, application/vnd.oci.image.index.v2+json, application/vnd.oci.image.manifest.v2+json")
  @GET("/v2/{repository}/manifests/{tag}")
  Call<DockerImageManifestResponse>
  getImageManifestV2(@Header("Authorization") String authHeader,
      @Path(value = "repository", encoded = true) String repository, @Path(value = "tag", encoded = true) String tag);
}
