/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.azureartifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.azureartifacts.AzureArtifactsResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
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
@Path("/artifacts/azureartifacts")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AzureArtifactsArtifactResource {
  private final AzureArtifactsResourceService azureArtifactsResourceService;

  // GET Api to fetch Azure Artifacts Packages
  @GET
  @Path("packages")
  @ApiOperation(value = "List Packages for Azure Artifacts", nickname = "listPackagesForAzureArtifacts")
  public ResponseDTO<List<AzureArtifactsPackage>> listPackages(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("packageType") String packageType,
      @NotNull @QueryParam("feed") String feed, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsPackage> response = azureArtifactsResourceService.listAzureArtifactsPackages(
        connectorRef, accountId, orgIdentifier, projectIdentifier, org, project, feed, packageType);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Versions for Azure Artifacts Package
  @GET
  @Path("versions")
  @ApiOperation(value = "List Versions from Package", nickname = "listVersionsFromPackage")
  public ResponseDTO<List<BuildDetails>> listVersionsOfPackage(
      @NotNull @QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("package") String packageName,
      @NotNull @QueryParam("packageType") String packageType, @QueryParam("versionRegex") String versionRegex,
      @NotNull @QueryParam("feed") String feed, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> response = azureArtifactsResourceService.listVersionsOfAzureArtifactsPackage(connectorRef,
        accountId, orgIdentifier, projectIdentifier, org, project, feed, packageType, packageName, versionRegex);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch a Version for Azure Artifacts Package
  @GET
  @Path("version")
  @ApiOperation(value = "Gets Version from Packages", nickname = "getVersionFromPackage")
  public ResponseDTO<BuildDetails> getVersionOfPackage(@NotNull @QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("package") String packageName,
      @NotNull @QueryParam("packageType") String packageType, @QueryParam("version") String version,
      @QueryParam("versionRegex") String versionRegex, @NotNull @QueryParam("feed") String feed,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    BuildDetails response = azureArtifactsResourceService.getLastSuccessfulVersion(connectorRef, accountId,
        orgIdentifier, projectIdentifier, org, project, feed, packageType, packageName, version, versionRegex);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Azure Artifacts Projects
  @GET
  @Path("projects")
  @ApiOperation(value = "List Projects for Azure Artifacts", nickname = "listProjectsForAzureArtifacts")
  public ResponseDTO<List<AzureDevopsProject>> listProjects(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull @QueryParam("org") String org,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureDevopsProject> response = azureArtifactsResourceService.listAzureArtifactsProjects(
        connectorRef, accountId, orgIdentifier, projectIdentifier, org);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Azure Artifacts Packages
  @GET
  @Path("feeds")
  @ApiOperation(value = "Lists Feeds for Azure Artifacts Org or Project", nickname = "listFeedsForAzureArtifacts")
  public ResponseDTO<List<AzureArtifactsFeed>> listFeeds(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull @QueryParam("org") String org,
      @QueryParam("project") String project, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsFeed> response = azureArtifactsResourceService.listAzureArtifactsFeeds(
        connectorRef, accountId, orgIdentifier, projectIdentifier, org, project);

    return ResponseDTO.newResponse(response);
  }
}
