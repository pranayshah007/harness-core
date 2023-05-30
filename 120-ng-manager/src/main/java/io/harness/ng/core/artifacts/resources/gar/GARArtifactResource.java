/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.gar;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceServiceImpl.GAR_REGIONS;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.RegionGar;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/gar")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GARArtifactResource {
  private final GARResourceService gARResourceService;
  private ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(
      value = "Gets google artifact registry build details", nickname = "getBuildDetailsForGoogleArtifactRegistry")
  public ResponseDTO<GARResponseDTO>
  getBuildDetails(@QueryParam("connectorRef") String GCPConnectorIdentifier, @QueryParam("region") String region,
      @QueryParam("repositoryName") String repositoryName, @QueryParam("project") String project,
      @QueryParam("package") String pkg, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("version") String version,
      @QueryParam("versionRegex") String versionRegex, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GARResponseDTO buildDetails = gARResourceService.getBuildDetails(
        connectorRef, region, repositoryName, project, pkg, version, versionRegex, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("v2/getBuildDetails")
  @ApiOperation(
      value = "Gets google artifact registry build details v2", nickname = "getBuildDetailsForGoogleArtifactRegistryV2")
  public ResponseDTO<GARResponseDTO>
  getBuildDetailsV2(@QueryParam("connectorRef") String gcpConnectorIdentifier, @QueryParam("region") String region,
      @QueryParam("repositoryName") String repositoryName, @QueryParam("project") String project,
      @QueryParam("package") String pkg, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("version") String version, @QueryParam("versionRegex") String versionRegex,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // In case of ServiceV2 Calls

    GARResponseDTO buildDetails = artifactResourceUtils.getBuildDetailsV2GAR(gcpConnectorIdentifier, region,
        repositoryName, project, pkg, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, version,
        versionRegex, fqnPath, runtimeInputYaml, serviceRef, gitEntityBasicInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets google artifact registry last successful build",
      nickname = "getLastSuccessfulBuildForGoogleArtifactRegistry")
  public ResponseDTO<GARBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String GCPConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region,
      @QueryParam(NGArtifactConstants.REPOSITORY_NAME) String repositoryName,
      @QueryParam(NGArtifactConstants.PROJECT) String project, @QueryParam(NGArtifactConstants.PACKAGE) String pkg,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull GarRequestDTO garRequestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GARBuildDetailsDTO buildDetails = gARResourceService.getLastSuccessfulBuild(
        connectorRef, region, repositoryName, project, pkg, garRequestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("v2/getLastSuccessfulBuild")
  @ApiOperation(value = "Gets google artifact registry last successful build v2",
      nickname = "getLastSuccessfulBuildForGoogleArtifactRegistryV2")
  public ResponseDTO<GARBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String gcpConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region,
      @QueryParam(NGArtifactConstants.REPOSITORY_NAME) String repositoryName,
      @QueryParam(NGArtifactConstants.PROJECT) String project, @QueryParam(NGArtifactConstants.PACKAGE) String pkg,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull GarRequestDTO garRequestDTO) {
    GARBuildDetailsDTO buildDetails = artifactResourceUtils.getLastSuccessfulBuildV2GAR(gcpConnectorIdentifier, region,
        repositoryName, project, pkg, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, garRequestDTO,
        fqnPath, serviceRef, gitEntityBasicInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("getRegions")
  @ApiOperation(value = "Gets google artifact registry regions", nickname = "getRegionsForGoogleArtifactRegistry")
  public ResponseDTO<List<RegionGar>> getRegions() {
    return ResponseDTO.newResponse(GAR_REGIONS);
  }
}
