/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.nexus.NexusRepositories;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
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
      @QueryParam("connectorRef") String nexusConnectorIdentifier, @QueryParam("groupId") String groupId,
      @QueryParam("artifactId") String artifactId, @QueryParam("extension") String extension,
      @QueryParam("classifier") String classifier, @QueryParam("packageName") String packageName,
      @QueryParam("group") String group, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    NexusResponseDTO buildDetails = nexusResourceService.getBuildDetails(connectorRef, repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId,
        extension, classifier, packageName, group);
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
      @QueryParam("connectorRef") String nexusConnectorIdentifier, @QueryParam("groupId") String groupId,
      @QueryParam("artifactId") String artifactId, @QueryParam("extension") String extension,
      @QueryParam("classifier") String classifier, @QueryParam("packageName") String packageName,
      @QueryParam("group") String group, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    NexusResponseDTO buildDetails;
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath.trim());
      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        buildDetails = artifactResourceUtils.getBuildDetailsNexus2(nexusConnectorIdentifier, repository, repositoryPort,
            artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId,
            artifactId, extension, classifier, packageName, pipelineIdentifier, fqnPath, gitEntityBasicInfo,
            runtimeInputYaml, serviceRef, accountId, group);
        return ResponseDTO.newResponse(buildDetails);
      }
    }
    buildDetails = artifactResourceUtils.getBuildDetails(nexusConnectorIdentifier, repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId,
        extension, classifier, packageName, pipelineIdentifier, fqnPath.trim(), gitEntityBasicInfo, runtimeInputYaml,
        serviceRef, accountId, group);
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
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
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
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    boolean isValidArtifactServer =
        nexusResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @POST
  @Hidden
  @Path("getRepositories")
  @ApiOperation(value = "Get Repositories for nexus artifact server", nickname = "getRepositories")
  public ResponseDTO<List<NexusRepositories>> getRepositories(
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("fqnPath") String fqnPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        return ResponseDTO.newResponse(artifactResourceUtils.getRepositoriesNexus2(orgIdentifier, projectIdentifier,
            repositoryFormat, accountId, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath,
            gitEntityBasicInfo, serviceRef));
      }
    }
    return ResponseDTO.newResponse(
        artifactResourceUtils.getRepositoriesNexus3(orgIdentifier, projectIdentifier, repositoryFormat, accountId,
            pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef));
  }

  @POST
  @Hidden
  @Path("groupIds")
  @ApiOperation(value = "Get GroupIds for nexus", nickname = "getGroupIds")
  public ResponseDTO<List<String>> getGroupIds(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("repository") String repository,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    ArtifactSourceType artifactSourceType = null;

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS3_REGISTRY)) {
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

      } else if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
            (Nexus2RegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
        }
      }
    }

    nexusConnectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    repositoryFormat = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<String> groupIds = nexusResourceService.getGroupIds(
        accountId, orgIdentifier, projectIdentifier, connectorRef, repositoryFormat, repository, artifactSourceType);

    return ResponseDTO.newResponse(groupIds);
  }

  @POST
  @Hidden
  @Path("artifactIds")
  @ApiOperation(value = "Get ArtifactIds for nexus", nickname = "artifactIds")
  public ResponseDTO<List<String>> getArtifactIds(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("repository") String repository,
      @QueryParam("groupId") String groupId, @QueryParam("nexusSourceType") String sourceType,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    ArtifactSourceType artifactSourceType = null;

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS3_REGISTRY)) {
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexusRegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexusRegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexusRegistryArtifactConfig.getRepository().fetchFinalValue();
        }

        if (isEmpty(groupId)) {
          if (repositoryFormat.equals("maven")) {
            NexusRegistryMavenConfig mavenConfig =
                (NexusRegistryMavenConfig) nexusRegistryArtifactConfig.getNexusRegistryConfigSpec();
            groupId = (String) mavenConfig.getGroupId().fetchFinalValue();
          }
        }

      } else if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig =
            (Nexus2RegistryArtifactConfig) artifactSpecFromService;

        artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;

        if (isEmpty(nexusConnectorIdentifier)) {
          nexusConnectorIdentifier = (String) nexus2RegistryArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (isEmpty(repositoryFormat)) {
          repositoryFormat = (String) nexus2RegistryArtifactConfig.getRepositoryFormat().fetchFinalValue();
        }

        if (isEmpty(repository)) {
          repository = (String) nexus2RegistryArtifactConfig.getRepository().fetchFinalValue();
        }

        if (isEmpty(groupId)) {
          if (repositoryFormat.equals("maven")) {
            NexusRegistryMavenConfig mavenConfig =
                (NexusRegistryMavenConfig) nexus2RegistryArtifactConfig.getNexusRegistryConfigSpec();
            groupId = (String) mavenConfig.getGroupId().fetchFinalValue();
          }
        }
      } else {
        throw new InvalidRequestException("Invalid artifact source type.");
      }
    }

    nexusConnectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    repositoryFormat = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repositoryFormat, fqnPath, gitEntityBasicInfo, serviceRef);

    repository = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, repository, fqnPath, gitEntityBasicInfo, serviceRef);

    groupId = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, groupId, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    String nexus3Registry = "Nexus3Registry";
    String nexus2Registry = "Nexus2Registry";

    if (nexus3Registry.equals(sourceType)) {
      artifactSourceType = ArtifactSourceType.NEXUS3_REGISTRY;
    } else if (nexus2Registry.equals(sourceType)) {
      artifactSourceType = ArtifactSourceType.NEXUS2_REGISTRY;
    } else {
      throw new InvalidRequestException("Invalid artifact source type.");
    }

    List<String> artifactIds = nexusResourceService.getArtifactIds(accountId, orgIdentifier, projectIdentifier,
        connectorRef, repositoryFormat, repository, groupId, artifactSourceType);

    return ResponseDTO.newResponse(artifactIds);
  }
}
