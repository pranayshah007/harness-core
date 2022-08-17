/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.githubpackages;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/githubpackages")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GithubPackagesArtifactResource {
  private final GithubPackagesResourceService githubPackagesResourceService;

  // GET Api to fetch Github Packages from an account or an org
  @GET
  @Path("packages")
  @ApiOperation(value = "Gets Package details for GithubPackages", nickname = "getPackagesFromGithub")
  public ResponseDTO<GithubPackagesResponseDTO> getPackages(@QueryParam("connectorRef") String gitConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gitConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    GithubPackagesResponseDTO response =
        githubPackagesResourceService.getPackageDetails(connectorRef, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Versions for a Github Package
  @GET
  @Path("versions")
  @ApiOperation(value = "Gets Versions from Packages", nickname = "getVersionsFromPackages")
  public ResponseDTO<List<BuildDetails>> getVersionsOfPackage(
      @NotNull @QueryParam("connectorRef") String gitConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("packageName") String packageName, @NotNull @QueryParam("packageType") String packageType,
      @QueryParam("versionRegex") String versionRegex, @QueryParam("org") String org,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gitConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> response = githubPackagesResourceService.getVersionsOfPackage(
        connectorRef, packageName, packageType, versionRegex, org, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(response);
  }
}
