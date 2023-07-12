/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.docker;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
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
@Path("/artifacts/docker")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class DockerArtifactResource {
  private final DockerResourceService dockerResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets docker build details", nickname = "getBuildDetailsForDocker")
  public ResponseDTO<DockerResponseDTO> getBuildDetails(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    DockerResponseDTO buildDetails =
        dockerResourceService.getBuildDetails(connectorRef, imagePath, orgIdentifier, projectIdentifier, null);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets docker build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForDockerWithYaml")
  public ResponseDTO<DockerResponseDTO>
  getBuildDetailsV2(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGArtifactConstants.TAG_INPUT) String tagInput, @NotNull @QueryParam("fqnPath") String fqnPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    String tagRegex = null;
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) dockerHubArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(dockerConnectorIdentifier)) {
        dockerConnectorIdentifier = (String) dockerHubArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (EmptyPredicate.isNotEmpty(tagInput)) {
        final ParameterField<String> tagRegexParameterField =
            RuntimeInputValuesValidator.getInputSetParameterField(tagInput);
        if (tagRegexParameterField != null && artifactResourceUtils.checkValidRegexType(tagRegexParameterField)) {
          tagRegex = tagRegexParameterField.getInputSetValidator().getParameters();
        }
      }

      if (EmptyPredicate.isEmpty(tagRegex)
          && artifactResourceUtils.checkValidRegexType(dockerHubArtifactConfig.getTag())) {
        tagRegex = dockerHubArtifactConfig.getTag().getInputSetValidator().getParameters();
      }
    }

    dockerConnectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, dockerConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    imagePath = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    DockerResponseDTO buildDetails =
        dockerResourceService.getBuildDetails(connectorRef, imagePath, orgIdentifier, projectIdentifier, tagRegex);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLabels")
  @ApiOperation(value = "Gets docker labels", nickname = "getLabelsForDocker")
  public ResponseDTO<DockerResponseDTO> getLabels(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    DockerResponseDTO buildDetails =
        dockerResourceService.getLabels(connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets docker last successful build", nickname = "getLastSuccessfulBuildForDocker")
  public ResponseDTO<DockerBuildDetailsDTO> getLastSuccessfulBuild(
      @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    DockerBuildDetailsDTO buildDetails =
        dockerResourceService.getSuccessfulBuild(connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @ApiOperation(value = "Gets docker last successful build with yaml input for expression resolution",
      nickname = "getLastSuccessfulBuildForDockerWithYaml")
  public ResponseDTO<DockerBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String dockerConnectorIdentifier,
      @QueryParam(NGArtifactConstants.TAG) String tag,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull DockerRequestDTO requestDTO, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    DockerBuildDetailsDTO dockerBuildDetailsDTO =
        artifactResourceUtils.getLastSuccessfulBuildV2Docker(imagePath, dockerConnectorIdentifier, tag, accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier, fqnPath, gitEntityBasicInfo, requestDTO, serviceRef);
    return ResponseDTO.newResponse(dockerBuildDetailsDTO);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate docker artifact server", nickname = "validateArtifactServerForDocker")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        dockerResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @ApiOperation(value = "Validate docker image", nickname = "validateArtifactImageForDocker")
  public ResponseDTO<Boolean> validateArtifactImage(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactImage =
        dockerResourceService.validateArtifactSource(imagePath, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @ApiOperation(value = "Validate docker artifact with tag/tagregx if given", nickname = "validateArtifactForDocker")
  public ResponseDTO<Boolean> validateArtifact(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(dockerConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      isValidArtifact =
          dockerResourceService.validateArtifactSource(imagePath, connectorRef, orgIdentifier, projectIdentifier);
    } else {
      try {
        ResponseDTO<DockerBuildDetailsDTO> lastSuccessfulBuild = getLastSuccessfulBuild(
            imagePath, dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO);
        if (lastSuccessfulBuild.getData() != null && isNotEmpty(lastSuccessfulBuild.getData().getTag())) {
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
