/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.client;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersion;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface GithubPackagesRestClient {
  @GET("/user/packages")
  Call<List<JsonNode>> listPackages(
      @Header("Authorization") String bearerAuthHeader, @Query("package_type") String packageType);

  @GET("/orgs/{org}/packages")
  Call<List<JsonNode>> listPackagesForOrg(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "org", encoded = true) String org, @Query("package_type") String packageType);

  @GET("/user/packages/{packageType}/{packageName}/versions")
  Call<List<JsonNode>> listVersionsForPackages(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "packageName", encoded = true) String packageName,
      @Path(value = "packageType", encoded = true) String packageType, @Query("per_page") Integer perPageEntries,
      @Query("page") Integer pageNumber);

  @GET("/orgs/{org}/packages/{packageType}/{packageName}/versions")
  Call<List<JsonNode>> listVersionsForPackagesInOrg(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "org", encoded = true) String org, @Path(value = "packageName", encoded = true) String packageName,
      @Path(value = "packageType", encoded = true) String packageType, @Query("per_page") Integer perPageEntries,
      @Query("page") Integer pageNumber);

  @GET("/user/packages/{packageType}/{packageName}/versions/{versionId}")
  Call<GithubPackagesVersion> getVersion(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "packageName", encoded = true) String packageName,
      @Path(value = "packageType", encoded = true) String packageType,
      @Path(value = "packageType", encoded = true) Integer versionId);
}
