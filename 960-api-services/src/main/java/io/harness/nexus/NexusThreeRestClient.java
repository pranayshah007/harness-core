/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.nexus.model.DockerImageResponse;
import io.harness.nexus.model.DockerImageTagResponse;
import io.harness.nexus.model.Nexus3AssetResponse;
import io.harness.nexus.model.Nexus3ComponentResponse;
import io.harness.nexus.model.Nexus3Repository;
import io.harness.nexus.model.RepositoryRequest;
import io.harness.nexus.model.RepositoryResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Created by sgurubelli on 11/16/17.
 */
@OwnedBy(CDC)
public interface NexusThreeRestClient {
  @Headers("Accept: application/json")
  @POST("service/extdirect")
  Call<RepositoryResponse> getRepositories(
      @Header("Authorization") String authorization, @Body RepositoryRequest repositoryRequest);

  @Headers("Accept: application/json")
  @POST("service/extdirect")
  Call<RepositoryResponse> getRepositories(@Body RepositoryRequest repositoryRequest);

  @GET("repository/{repoKey}/v2/_catalog")
  Call<DockerImageResponse> getDockerImages(
      @Header("Authorization") String authorization, @Path(value = "repoKey", encoded = true) String repository);

  @GET("repository/{repoKey}/v2/_catalog")
  Call<DockerImageResponse> getDockerImages(@Path(value = "repoKey", encoded = true) String repository);

  @GET("repository/{repoKey}/v2/{imageName}/tags/list")
  Call<DockerImageTagResponse> getDockerTags(@Header("Authorization") String authorization,
      @Path(value = "repoKey", encoded = true) String repository,
      @Path(value = "imageName", encoded = true) String imageName);

  @GET("repository/{repoKey}/v2/{imageName}/tags/list")
  Call<DockerImageTagResponse> getDockerTags(@Path(value = "repoKey", encoded = true) String repository,
      @Path(value = "imageName", encoded = true) String imageName);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/repositories")
  Call<List<Nexus3Repository>> listRepositories(@Header("Authorization") String authorization);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/repositories")
  Call<List<Nexus3Repository>> listRepositories();

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> search(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> search(
      @Query("repository") String repository, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> search(@Query("repository") String repository, @Query("name") String imageName,
      @Query("format") String repoFormat, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> search(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("name") String imageName, @Query("format") String repoFormat,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> getArtifact(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("name") String imageName, @Query("format") String repoFormat,
      @Query("version") String version, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> getArtifact(@Query("repository") String repository, @Query("name") String imageName,
      @Query("format") String repoFormat, @Query("version") String version,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getPackageVersions(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("name") String packageName,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getGroupVersions(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("group") String group,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getGroupVersions(@Query("repository") String repository, @Query("group") String group,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getPackageVersions(@Query("repository") String repository,
      @Query("name") String packageName, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getAsset(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("name") String name, @Query("version") String version);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getAsset(
      @Query("repository") String repository, @Query("name") String name, @Query("version") String version);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> getArtifactNames(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("maven.groupId") String groupId,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search")
  Call<Nexus3ComponentResponse> getArtifactNames(@Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getArtifactVersions(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("maven.groupId") String groupId,
      @Query("maven.artifactId") String artifactId, @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getArtifactVersions(@Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("maven.artifactId") String artifactId,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getArtifactVersionsWithExtensionAndClassifier(
      @Header("Authorization") String authorization, @Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("maven.artifactId") String artifactId,
      @Query("maven.extension") String extension, @Query("maven.classifier") String classifier,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search?sort=version&direction=desc")
  Call<Nexus3ComponentResponse> getArtifactVersionsWithExtensionAndClassifier(@Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("maven.artifactId") String artifactId,
      @Query("maven.extension") String extension, @Query("maven.classifier") String classifier,
      @Query("continuationToken") String continuationToken);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getMavenAsset(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("maven.groupId") String groupId,
      @Query("maven.artifactId") String artifactId, @Query("version") String version);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getMavenAsset(@Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("maven.artifactId") String artifactId,
      @Query("version") String version);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getMavenAssetWithExtensionAndClassifier(@Header("Authorization") String authorization,
      @Query("repository") String repository, @Query("maven.groupId") String groupId,
      @Query("maven.artifactId") String artifactId, @Query("version") String version,
      @Query("maven.extension") String extension, @Query("maven.classifier") String classifier);

  @Headers("Accept: application/json")
  @GET("service/rest/v1/search/assets")
  Call<Nexus3AssetResponse> getMavenAssetWithExtensionAndClassifier(@Query("repository") String repository,
      @Query("maven.groupId") String groupId, @Query("maven.artifactId") String artifactId,
      @Query("version") String version, @Query("maven.extension") String extension,
      @Query("maven.classifier") String classifier);
}
