/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure.devops;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(CDC)
@TargetModule(_960_API_SERVICES)
public interface AzureArtifactsRestClient {
  @GET("_apis/packaging/feeds?api-version=5.1-preview.1")
  Call<AzureArtifactsFeeds> listFeeds(@Header("Authorization") String authHeader);

  @GET("_apis/packaging/feeds/{feed}/packages?api-version=5.1-preview.1")
  Call<AzureArtifactsPackages> listPackages(@Header("Authorization") String authHeader,
      @Path(value = "feed") String feed, @Query("protocolType") String protocolType);

  @GET("_apis/packaging/feeds/{feed}/packages/{packageId}?api-version=5.1-preview.1")
  Call<AzureArtifactsPackage> getPackage(@Header("Authorization") String authHeader, @Path(value = "feed") String feed,
      @Path(value = "packageId") String packageId);

  @GET("_apis/packaging/feeds/{feed}/packages/{packageId}/versions?api-version=5.1-preview.1&isDeleted=false")
  Call<AzureArtifactsPackageVersions> listPackageVersions(@Header("Authorization") String authHeader,
      @Path(value = "feed") String feed, @Path(value = "packageId") String packageId);

  @GET("_apis/packaging/feeds/{feed}/packages/{packageId}/versions/{version}?api-version=5.1-preview.1")
  Call<AzureArtifactsPackageVersion> getPackageVersion(@Header("Authorization") String authHeader,
      @Path(value = "feed") String feed, @Path(value = "packageId") String packageId,
      @Path(value = "version") String version);
}
