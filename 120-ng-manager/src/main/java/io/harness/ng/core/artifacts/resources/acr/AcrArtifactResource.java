/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.acr;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.delegate.beans.azure.AcrResponseDTO;
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
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Api("artifacts")
@Path("/artifacts/acr")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AcrArtifactResource {
  private final AcrResourceService acrResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("container-registries")
  @ApiOperation(value = "Gets ACR registries by subscription ", nickname = "getACRRegistriesBySubscription")
  public ResponseDTO<AcrRegistriesDTO> getRegistriesBySubscription(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        acrResourceService.getRegistries(connectorRef, orgIdentifier, projectIdentifier, subscriptionId));
  }

  @GET
  @Path("container-registries/{registry}/repositories")
  @ApiOperation(
      value = "Gets ACR repositories by subscription and container registry name ", nickname = "getACRRepositories")
  public ResponseDTO<AcrRepositoriesDTO>
  getAzureRepositories(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("subscriptionId") String subscriptionId, @PathParam("registry") String registry) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        acrResourceService.getRepositories(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, registry));
  }

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets ACR repository build details", nickname = "getBuildDetailsForACRRepository")
  public ResponseDTO<AcrResponseDTO> getBuildDetails(@QueryParam("subscriptionId") String subscriptionId,
      @QueryParam("registry") String registry, @QueryParam("repository") String repository,
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    AcrResponseDTO buildDetails = acrResourceService.getBuildDetails(
        connectorRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets ACR build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForAcrArtifactWithYaml")
  public ResponseDTO<AcrResponseDTO>
  getBuildDetailsV2(@QueryParam("subscriptionId") String subscriptionId, @QueryParam("registry") String registry,
      @QueryParam("repository") String repository, @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    String subId = artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, subscriptionId, fqnPath, gitEntityBasicInfo);
    String reg = artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registry, fqnPath, gitEntityBasicInfo);
    String repo = artifactResourceUtils.getResolvedImagePath(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo);

    AcrResponseDTO buildDetails =
        acrResourceService.getBuildDetails(connectorRef, subId, reg, repo, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }
}
