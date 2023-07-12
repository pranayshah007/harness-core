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
import io.harness.artifacts.githubpackages.beans.GithubMavenMetaData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface GithubPackagesMavenRestClient {
  @GET("{userOrg}/{repository}/{packageName}/{artifactId}/{version}/maven-metadata.xml")
  Call<GithubMavenMetaData> getMavenMetaData(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "userOrg", encoded = true) String userOrg,
      @Path(value = "repository", encoded = true) String repository,
      @Path(value = "packageName", encoded = true) String packageName,
      @Path(value = "artifactId", encoded = true) String artifactId,
      @Path(value = "version", encoded = true) String version);
}
