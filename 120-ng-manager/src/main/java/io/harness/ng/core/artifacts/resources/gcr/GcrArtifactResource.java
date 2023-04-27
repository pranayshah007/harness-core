/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.gcr;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
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

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/gcr")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GcrArtifactResource {
  private final GcrResourceService gcrResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets gcr build details", nickname = "getBuildDetailsForGcr")
  public ResponseDTO<GcrResponseDTO> getBuildDetails(@NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("registryHostname") String registryHostname,
      @NotNull @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GcrResponseDTO buildDetails =
        gcrResourceService.getBuildDetails(connectorRef, imagePath, registryHostname, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets gcr build details with Yaml expression", nickname = "getBuildDetailsForGcrWithYaml")
  public ResponseDTO<GcrResponseDTO> getBuildDetails(@QueryParam("imagePath") String imagePath,
      @QueryParam("registryHostname") String registryHostname,
      @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) gcrArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(registryHostname)) {
        registryHostname = (String) gcrArtifactConfig.getRegistryHostname().fetchFinalValue();
      }

      if (isEmpty(gcrConnectorIdentifier)) {
        gcrConnectorIdentifier = (String) gcrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }
    // todo(hinger): resolve other expressions here?

    gcrConnectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, gcrConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    imagePath = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef);
    registryHostname = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, registryHostname, fqnPath, gitEntityBasicInfo, serviceRef);
    GcrResponseDTO buildDetails =
        gcrResourceService.getBuildDetails(connectorRef, imagePath, registryHostname, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets gcr last successful build", nickname = "getLastSuccessfulBuildForGcr")
  public ResponseDTO<GcrBuildDetailsDTO> getLastSuccessfulBuild(@NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, GcrRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GcrBuildDetailsDTO buildDetails =
        gcrResourceService.getSuccessfulBuild(connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @ApiOperation(
      value = "Gets gcr last successful build with Yaml expression", nickname = "getLastSuccessfulBuildForGcrV2")
  public ResponseDTO<GcrBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull GcrRequestDTO gcrRequestDTO) {
    GcrBuildDetailsDTO buildDetails =
        artifactResourceUtils.getSuccessfulBuildV2GCR(imagePath, gcrConnectorIdentifier, accountId, orgIdentifier,
            projectIdentifier, fqnPath, serviceRef, pipelineIdentifier, gitEntityBasicInfo, gcrRequestDTO);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate gcr artifact server", nickname = "validateArtifactServerForGcr")
  public ResponseDTO<Boolean> validateArtifactServer(@NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam("registryHostname") String registryHostname,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer = gcrResourceService.validateArtifactServer(
        connectorRef, imagePath, orgIdentifier, projectIdentifier, registryHostname);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @ApiOperation(value = "Validate Gcr image", nickname = "validateArtifactImageForGcr")
  public ResponseDTO<Boolean> validateArtifactImage(@NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("registryHostname") String registryHostname,
      @NotNull @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactImage = gcrResourceService.validateArtifactSource(
        imagePath, connectorRef, registryHostname, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @ApiOperation(value = "Validate Gcr Artifact", nickname = "validateArtifactForGcr")
  public ResponseDTO<Boolean> validateArtifact(@NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("registryHostname") String registryHostname,
      @NotNull @QueryParam("connectorRef") String gcrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, GcrRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(gcrConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(registryHostname)) {
      throw new InvalidRequestException("RegistryHostName is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      isValidArtifact = gcrResourceService.validateArtifactSource(
          imagePath, connectorRef, registryHostname, orgIdentifier, projectIdentifier);
    } else {
      try {
        ResponseDTO<GcrBuildDetailsDTO> lastSuccessfulBuild = getLastSuccessfulBuild(
            imagePath, gcrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO);
        if (lastSuccessfulBuild.getData() != null
            && EmptyPredicate.isNotEmpty(lastSuccessfulBuild.getData().getTag())) {
          isValidArtifact = true;
        }
      } catch (Exception e) {
        log.info("Not able to find any artifact with given parameters - " + requestDTO.toString() + " and imagePath - "
            + imagePath);
      }
    }
    return ResponseDTO.newResponse(isValidArtifact);
  }
}
