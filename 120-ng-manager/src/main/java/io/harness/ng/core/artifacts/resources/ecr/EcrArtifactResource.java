/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.ecr;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.cdng.artifact.NGArtifactConstants.REGISTRY_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrListImagesDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.YamlExpressionEvaluatorWithContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.yaml.ParameterField;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@Api("artifacts")
@Path("/artifacts/ecr")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class EcrArtifactResource {
  private final EcrResourceService ecrResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets ecr build details", nickname = "getBuildDetailsForEcr")
  public ResponseDTO<EcrResponseDTO> getBuildDetails(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    EcrResponseDTO buildDetails = ecrResourceService.getBuildDetails(
        connectorRef, registryId, imagePath, region, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets ecr build details with yaml expression", nickname = "getBuildDetailsForEcrWithYaml")
  public ResponseDTO<EcrResponseDTO> getBuildDetailsV2(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam("imagePath") String imagePath, @QueryParam("region") String region,
      @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null
              ? null
              : baseEvaluatorWithContext.getContextMap().get(artifactResourceUtils.SERVICE_GIT_BRANCH));

      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }
      if (isEmpty(imagePath)) {
        imagePath = (String) ecrArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(region)) {
        region = (String) ecrArtifactConfig.getRegion().fetchFinalValue();
      }

      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    ecrConnectorIdentifier = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, ecrConnectorIdentifier,
                                     fqnPath, gitEntityBasicInfo, serviceRef, null)
                                 .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    registryId =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, registryId, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();

    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef, null)
                    .getValue();

    // resolving region
    region = artifactResourceUtils
                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                     pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                 .getValue();

    EcrResponseDTO buildDetails = ecrResourceService.getBuildDetails(
        connectorRef, registryId, imagePath, region, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets ecr last successful build", nickname = "getLastSuccessfulBuildForEcr")
  public ResponseDTO<EcrBuildDetailsDTO> getLastSuccessfulBuild(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @NotNull @QueryParam(NGArtifactConstants.CONNECTOR_REF) String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, EcrRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    EcrBuildDetailsDTO buildDetails = ecrResourceService.getSuccessfulBuild(
        connectorRef, registryId, imagePath, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @ApiOperation(
      value = "Gets ecr last successful build with yaml expression", nickname = "getLastSuccessfulBuildForEcrWithYaml")
  public ResponseDTO<EcrBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull EcrRequestDTO ecrRequestDTO) {
    EcrBuildDetailsDTO ecrBuildDetailsDTO = artifactResourceUtils.getLastSuccessfulBuildV2ECR(registryId, imagePath,
        ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, fqnPath, serviceRef, pipelineIdentifier,
        gitEntityBasicInfo, ecrRequestDTO);
    return ResponseDTO.newResponse(ecrBuildDetailsDTO);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate ecr artifact server", nickname = "validateArtifactServerForEcr")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer = ecrResourceService.validateArtifactServer(
        connectorRef, registryId, imagePath, orgIdentifier, projectIdentifier, region);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @ApiOperation(value = "Validate Ecr image", nickname = "validateArtifactImageForEcr")
  public ResponseDTO<Boolean> validateArtifactImage(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactImage = ecrResourceService.validateArtifactSource(
        registryId, imagePath, connectorRef, region, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @ApiOperation(value = "Validate Ecr Artifact", nickname = "validateArtifactForEcr")
  public ResponseDTO<Boolean> validateArtifact(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, EcrRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(ecrConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(registryId)) {
      throw new InvalidRequestException("RegistryId is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(region)) {
      throw new InvalidRequestException("region is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      isValidArtifact = ecrResourceService.validateArtifactSource(
          registryId, imagePath, connectorRef, region, orgIdentifier, projectIdentifier);
    } else {
      try {
        ResponseDTO<EcrBuildDetailsDTO> lastSuccessfulBuild = getLastSuccessfulBuild(
            registryId, imagePath, ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO);
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

  @POST
  @Path("getImages")
  @ApiOperation(value = "Gets ecr images", nickname = "getImagesListForEcr")
  public ResponseDTO<EcrListImagesDTO> getImages(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam("region") String region, @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("fqnPath") String fqnPath,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }
      if (isEmpty(region)) {
        region = (String) ecrArtifactConfig.getRegion().fetchFinalValue();
        if (isEmpty(region)) {
          throw new InvalidRequestException("Please input a valid region.");
        }
      }
      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }
    ecrConnectorIdentifier = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, ecrConnectorIdentifier,
                                     fqnPath, gitEntityBasicInfo, serviceRef, null)
                                 .getValue();
    registryId =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, registryId, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();
    region = artifactResourceUtils
                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                     pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef, null)
                 .getValue();
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    EcrListImagesDTO ecrListImagesDTO =
        ecrResourceService.getImages(connectorRef, registryId, region, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(ecrListImagesDTO);
  }
}
