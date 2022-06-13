/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
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

@OwnedBy(HarnessTeam.CDP)
@Api("artifacts")
@Path("/artifacts/nexus")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NexusArtifactResource {
  private final NexusResourceService nexusResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets nexus artifact build details", nickname = "getBuildDetailsForNexusArtifact")
  public ResponseDTO<NexusResponseDTO> getBuildDetails(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") String repositoryPort, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl, @QueryParam("artifactPath") String artifactPath,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    NexusResponseDTO buildDetails = nexusResourceService.getBuildDetails(connectorRef, repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets nexus artifact build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForNexusArtifactWithYaml")
  public ResponseDTO<NexusResponseDTO>
  getBuildDetailsV2(@QueryParam("repository") String repository, @QueryParam("repositoryPort") String repositoryPort,
      @QueryParam("artifactPath") String artifactPath, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactPath = artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo);
    NexusResponseDTO buildDetails = nexusResourceService.getBuildDetails(connectorRef, repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(
      value = "Gets nexus artifact last successful build", nickname = "getLastSuccessfulBuildForNexusArtifact")
  public ResponseDTO<NexusBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") String repositoryPort, @QueryParam("artifactPath") String artifactPath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    NexusBuildDetailsDTO buildDetails =
        nexusResourceService.getSuccessfulBuild(connectorRef, repository, repositoryPort, artifactPath,
            repositoryFormat, artifactRepositoryUrl, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate nexus artifact server", nickname = "validateArtifactServerForNexus")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        nexusResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }
}
