/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.artifactory;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidIdentifierRefException;
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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Api("artifacts")
@Path("/artifacts/artifactory")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ArtifactoryArtifactResource {
  private final ArtifactoryResourceService artifactoryResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  /* Note:
    This API is used for both Artifactory Docker and Artifactory Generic.
    For Artifactory Generic this artfactPath Parameter will be artifactDirectory
  */

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets artifactory artifact build details", nickname = "getBuildDetailsForArtifactoryArtifact")
  public ResponseDTO<ArtifactoryResponseDTO> getBuildDetails(@QueryParam("repository") String repository,
      @QueryParam("artifactPath") String artifactPath, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryResponseDTO buildDetails = artifactoryResourceService.getBuildDetails(connectorRef, repository,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  /* Note:
  This API is used for both Artifactory Docker and Artifactory Generic.
  For Artifactory Generic this artfactPath Parameter will be artifactDirectory
*/

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets artifactory artifact build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForArtifactoryArtifactWithYaml")
  public ResponseDTO<ArtifactoryResponseDTO>
  getBuildDetailsV2(@QueryParam("repository") String repository, @QueryParam("artifactPath") String artifactPath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;
      if (isEmpty(repository)) {
        repository = (String) artifactoryRegistryArtifactConfig.getRepository().fetchFinalValue();
      }
      // There is an overload in this endpoint so to make things clearer:
      // artifactPath is the artifactDirectory for Artifactory Generic
      // artifactPath is the artifactPath for Artifactory Docker
      if (isEmpty(artifactPath)) {
        if (artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().equals("docker")) {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactPath().fetchFinalValue();
        } else {
          artifactPath = (String) artifactoryRegistryArtifactConfig.getArtifactDirectory().fetchFinalValue();
        }
      }
      if (isEmpty(repositoryFormat)) {
        repositoryFormat = (String) artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
      }

      if (isEmpty(artifactRepositoryUrl)) {
        artifactRepositoryUrl = (String) artifactoryRegistryArtifactConfig.getRepositoryUrl().fetchFinalValue();
      }

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier = (String) artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    artifactoryConnectorIdentifier =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, artifactoryConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    // todo(hinger): resolve other expressions here
    artifactPath = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactPath, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef);

    artifactRepositoryUrl = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, artifactRepositoryUrl, fqnPath, gitEntityBasicInfo, serviceRef);

    ArtifactoryResponseDTO buildDetails = artifactoryResourceService.getBuildDetails(connectorRef, repository,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }
  // unuse
  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets artifactory artifact last successful build",
      nickname = "getLastSuccessfulBuildForArtifactoryArtifact")
  public ResponseDTO<ArtifactoryBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam("repository") String repository, @QueryParam("artifactPath") String artifactPath,
      @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      ArtifactoryRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryBuildDetailsDTO buildDetails = artifactoryResourceService.getSuccessfulBuild(connectorRef, repository,
        artifactPath, repositoryFormat, artifactRepositoryUrl, requestDTO, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate artifactory artifact server", nickname = "validateArtifactServerForArtifactory")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        artifactoryResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("repositoriesDetails")
  @ApiOperation(value = "Gets repository details", nickname = "getRepositoriesDetailsForArtifactory")
  public ResponseDTO<ArtifactoryRepoDetailsDTO> getRepositoriesDetails(
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @QueryParam("repositoryType") @DefaultValue("any") String repositoryType,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    // If UI is not passing repository type as param,then we are assuming repositoryType as any

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier =
            artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }

      if (isEmpty(repositoryType) || "any".equals(repositoryType)) {
        if (!StringUtils.isBlank(
                artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString())) {
          repositoryType = artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
        }
      }
    }
    if (artifactoryConnectorIdentifier != null && NGExpressionUtils.isRuntimeField(artifactoryConnectorIdentifier)) {
      throw new InvalidIdentifierRefException(
          "Artifactory Connector is required to fetch repositories. You can make this field Runtime input otherwise.");
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryRepoDetailsDTO repoDetailsDTO =
        artifactoryResourceService.getRepositories(repositoryType, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(repoDetailsDTO);
  }

  @POST
  @Path("repositoriesDetailsV2")
  @ApiOperation(value = "Gets repository details", nickname = "getRepositoriesDetailsV2ForArtifactory")
  public ResponseDTO<ArtifactoryRepoDetailsDTO> getRepositoriesDetailsV2(
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @QueryParam("repositoryType") @DefaultValue("any") String repositoryType,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @NotNull String runtimeInputYaml, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // If UI is not passing repository type as param,then we are assuming repositoryType as any

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier =
            artifactoryRegistryArtifactConfig.getConnectorRef().fetchFinalValue().toString();
      }

      if (isEmpty(repositoryType) || "any".equals(repositoryType)) {
        if (!StringUtils.isBlank(
                artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString())) {
          repositoryType = artifactoryRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue().toString();
        }
      }
    }

    artifactoryConnectorIdentifier =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, artifactoryConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    if (artifactoryConnectorIdentifier != null && NGExpressionUtils.isRuntimeField(artifactoryConnectorIdentifier)) {
      throw new InvalidIdentifierRefException(
          "Artifactory Connector is required to fetch repositories. You can make this field Runtime input otherwise.");
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryRepoDetailsDTO repoDetailsDTO =
        artifactoryResourceService.getRepositories(repositoryType, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(repoDetailsDTO);
  }

  @GET
  @Path("imagePaths")
  @ApiOperation(value = "Gets Image Paths details", nickname = "getImagePathsForArtifactory")
  public ResponseDTO<ArtifactoryImagePathsDTO> getImagePaths(
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @QueryParam("repositoryType") @DefaultValue("any") String repositoryType,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("repository") String repository, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
          (ArtifactoryRegistryArtifactConfig) artifactSpecFromService;

      if (isEmpty(artifactoryConnectorIdentifier)) {
        artifactoryConnectorIdentifier = artifactoryRegistryArtifactConfig.getConnectorRef().getValue();
      }
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ArtifactoryImagePathsDTO artifactoryImagePathsDTO = artifactoryResourceService.getImagePaths(
        repositoryType, connectorRef, orgIdentifier, projectIdentifier, repository);
    return ResponseDTO.newResponse(artifactoryImagePathsDTO);
  }

  @POST
  @Path("imagePathsV2")
  @ApiOperation(value = "Gets Image Paths details", nickname = "getImagePathsForArtifactoryV2")
  public ResponseDTO<ArtifactoryImagePathsDTO> getImagePathsV2(
      @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @QueryParam("repositoryType") @DefaultValue("any") String repositoryType,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repository") String repository, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @NotNull String runtimeInputYaml,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // Resolving parameters for service v2 compatibility
    ArtifactoryImagePathsDTO artifactoryImagePathsDTO = artifactResourceUtils.getArtifactoryImagePath(repositoryType,
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, repository, fqnPath,
        runtimeInputYaml, pipelineIdentifier, serviceRef, gitEntityBasicInfo);

    return ResponseDTO.newResponse(artifactoryImagePathsDTO);
  }

  @GET
  @Path("artifactBuildsDetails")
  @ApiOperation(value = "Gets artifacts builds details", nickname = "getArtifactsBuildsDetailsForArtifactory")
  public ResponseDTO<List<ArtifactoryArtifactBuildDetailsDTO>> getBuildsDetails(
      @NotNull @QueryParam("connectorRef") String artifactoryConnectorIdentifier,
      @NotNull @QueryParam("repositoryName") String repositoryName, @QueryParam("filePath") String filePath,
      @NotNull @QueryParam("maxVersions") int maxVersions,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        artifactoryConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    List<ArtifactoryArtifactBuildDetailsDTO> buildDetails = artifactoryResourceService.getBuildDetails(
        repositoryName, filePath, maxVersions, connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }
}