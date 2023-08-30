/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.azureartifacts;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.resources.azureartifacts.AzureArtifactsResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
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

  private final ArtifactResourceUtils artifactResourceUtils;

  // GET Api to fetch Azure Artifacts Packages
  @GET
  @Path("packages")
  @ApiOperation(value = "List Packages for Azure Artifacts", nickname = "listPackagesForAzureArtifacts")
  public ResponseDTO<List<AzureArtifactsPackage>> listPackages(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("packageType") String packageType,
      @NotNull @QueryParam("feed") String feed, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsPackage> response = azureArtifactsResourceService.listAzureArtifactsPackages(
        connectorRef, accountId, orgIdentifier, projectIdentifier, project, feed, packageType);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Azure Artifacts Packages with serviceV2
  @POST
  @Path("v2/packages")
  @ApiOperation(value = "List Packages for Azure Artifacts", nickname = "listPackagesForAzureArtifactsWithServiceV2")
  public ResponseDTO<List<AzureArtifactsPackage>> listPackagesWithServiceV2(
      @QueryParam("connectorRef") String azureConnectorRef, @QueryParam("project") String project,
      @QueryParam("packageType") String packageType, @QueryParam("feed") String feed,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactSpecFromService;

      if (StringUtils.isBlank(azureConnectorRef)) {
        azureConnectorRef = (String) azureArtifactsConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(project)) {
        project = (String) azureArtifactsConfig.getProject().fetchFinalValue();
      }

      if (StringUtils.isBlank(feed)) {
        feed = (String) azureArtifactsConfig.getFeed().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) azureArtifactsConfig.getPackageType().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAzureArtifactsConnector =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, azureConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project  in case of expressions
    String resolvedProject = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved feed  in case of expressions
    String resolvedFeed = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, feed, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved packageType  in case of expressions
    String resolvedPackageType = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAzureArtifactsConnector, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsPackage> response = azureArtifactsResourceService.listAzureArtifactsPackages(
        connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedProject, resolvedFeed, resolvedPackageType);

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
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("package") String packageName,
      @NotNull @QueryParam("packageType") String packageType, @QueryParam("versionRegex") String versionRegex,
      @NotNull @QueryParam("feed") String feed, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> response = azureArtifactsResourceService.listVersionsOfAzureArtifactsPackage(connectorRef,
        accountId, orgIdentifier, projectIdentifier, project, feed, packageType, packageName, versionRegex);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Versions for Azure Artifacts Package with serviceV2
  @POST
  @Path("v2/versions")
  @ApiOperation(value = "List Versions from Package", nickname = "listVersionsFromPackageWithServiceV2")
  public ResponseDTO<List<BuildDetails>> listVersionsOfPackageWithServiceV2(
      @QueryParam("connectorRef") String azureConnectorRef, @QueryParam("project") String project,
      @QueryParam("package") String packageName, @QueryParam("packageType") String packageType,
      @QueryParam("versionRegex") String versionRegex, @QueryParam("feed") String feed,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactSpecFromService;

      if (StringUtils.isBlank(azureConnectorRef)) {
        azureConnectorRef = (String) azureArtifactsConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(project)) {
        project = (String) azureArtifactsConfig.getProject().fetchFinalValue();
      }

      if (StringUtils.isBlank(feed)) {
        feed = (String) azureArtifactsConfig.getFeed().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) azureArtifactsConfig.getPackageType().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageName)) {
        packageName = (String) azureArtifactsConfig.getPackageName().fetchFinalValue();
      }

      if (StringUtils.isBlank(versionRegex)) {
        versionRegex = (String) azureArtifactsConfig.getVersionRegex().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAzureArtifactsConnector =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, azureConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project  in case of expressions
    String resolvedProject = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved feed  in case of expressions
    String resolvedFeed = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, feed, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved packageType  in case of expressions
    String resolvedPackageType = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved packageName  in case of expressions
    String resolvedPackageName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved versionRegex  in case of expressions
    String resolvedVersionRegex = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, versionRegex, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAzureArtifactsConnector, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> response = azureArtifactsResourceService.listVersionsOfAzureArtifactsPackage(connectorRef,
        accountId, orgIdentifier, projectIdentifier, resolvedProject, resolvedFeed, resolvedPackageType,
        resolvedPackageName, resolvedVersionRegex);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch a Version for Azure Artifacts Package
  @GET
  @Path("version")
  @ApiOperation(value = "Gets Version from Packages", nickname = "getVersionFromPackage")
  public ResponseDTO<BuildDetails> getVersionOfPackage(@NotNull @QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("org") String org,
      @QueryParam("project") String project, @NotNull @QueryParam("package") String packageName,
      @NotNull @QueryParam("packageType") String packageType, @QueryParam("version") String version,
      @QueryParam("versionRegex") String versionRegex, @NotNull @QueryParam("feed") String feed,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    BuildDetails response = azureArtifactsResourceService.getLastSuccessfulVersion(connectorRef, accountId,
        orgIdentifier, projectIdentifier, project, feed, packageType, packageName, version, versionRegex);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch a Version for Azure Artifacts Package with serviceV2
  @POST
  @Path("v2/version")
  @ApiOperation(value = "Gets Version from Packages", nickname = "getVersionFromPackageWithServiceV2")
  public ResponseDTO<BuildDetails> getVersionOfPackageWithServiceV2(
      @QueryParam("connectorRef") String azureConnectorRef, @QueryParam("project") String project,
      @QueryParam("package") String packageName, @QueryParam("packageType") String packageType,
      @QueryParam("version") String version, @QueryParam("versionRegex") String versionRegex,
      @QueryParam("feed") String feed, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactSpecFromService;

      if (StringUtils.isBlank(azureConnectorRef)) {
        azureConnectorRef = (String) azureArtifactsConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(project)) {
        project = (String) azureArtifactsConfig.getProject().fetchFinalValue();
      }

      if (StringUtils.isBlank(feed)) {
        feed = (String) azureArtifactsConfig.getFeed().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) azureArtifactsConfig.getPackageType().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageName)) {
        packageName = (String) azureArtifactsConfig.getPackageName().fetchFinalValue();
      }

      if (StringUtils.isBlank(versionRegex)) {
        versionRegex = (String) azureArtifactsConfig.getVersionRegex().fetchFinalValue();
      }

      if (StringUtils.isBlank(version)) {
        version = (String) azureArtifactsConfig.getVersion().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAzureArtifactsConnector =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, azureConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project  in case of expressions
    String resolvedProject = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved feed  in case of expressions
    String resolvedFeed = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, feed, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved packageType  in case of expressions
    String resolvedPackageType = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved packageName  in case of expressions
    String resolvedPackageName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageName, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved versionRegex  in case of expressions
    String resolvedVersionRegex = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, versionRegex, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved version  in case of expressions
    String resolvedVersion = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, version, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAzureArtifactsConnector, accountId, orgIdentifier, projectIdentifier);

    BuildDetails response = azureArtifactsResourceService.getLastSuccessfulVersion(connectorRef, accountId,
        orgIdentifier, projectIdentifier, resolvedProject, resolvedFeed, resolvedPackageType, resolvedPackageName,
        resolvedVersion, resolvedVersionRegex);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Azure Artifacts Projects
  @GET
  @Path("projects")
  @ApiOperation(value = "List Projects for Azure Artifacts", nickname = "listProjectsForAzureArtifacts")
  public ResponseDTO<List<AzureDevopsProject>> listProjects(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("org") String org,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureDevopsProject> response = azureArtifactsResourceService.listAzureArtifactsProjects(
        connectorRef, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Azure Artifacts Projects with serviceV2
  @POST
  @Path("v2/projects")
  @ApiOperation(value = "List Projects for Azure Artifacts", nickname = "listProjectsForAzureArtifactsWithServiceV2")
  public ResponseDTO<List<AzureDevopsProject>> listProjectsWithServiceV2(
      @QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactSpecFromService;

      if (StringUtils.isBlank(azureConnectorRef)) {
        azureConnectorRef = (String) azureArtifactsConfig.getConnectorRef().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAzureArtifactsConnector =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, azureConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAzureArtifactsConnector, accountId, orgIdentifier, projectIdentifier);

    List<AzureDevopsProject> response = azureArtifactsResourceService.listAzureArtifactsProjects(
        connectorRef, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Azure Artifacts Feeds
  @GET
  @Path("feeds")
  @ApiOperation(value = "Lists Feeds for Azure Artifacts Org or Project", nickname = "listFeedsForAzureArtifacts")
  public ResponseDTO<List<AzureArtifactsFeed>> listFeeds(@QueryParam("connectorRef") String azureConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("org") String org,
      @QueryParam("project") String project, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(azureConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsFeed> response = azureArtifactsResourceService.listAzureArtifactsFeeds(
        connectorRef, accountId, orgIdentifier, projectIdentifier, project);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Azure Artifacts Feeds with ServiceV2
  @POST
  @Path("v2/feeds")
  @ApiOperation(
      value = "Lists Feeds for Azure Artifacts Org or Project", nickname = "listFeedsForAzureArtifactsWithServiceV2")
  public ResponseDTO<List<AzureArtifactsFeed>>
  listFeedsWithServiceV2(@QueryParam("connectorRef") String azureConnectorRef, @QueryParam("project") String project,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactSpecFromService;

      if (StringUtils.isBlank(azureConnectorRef)) {
        azureConnectorRef = (String) azureArtifactsConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(project)) {
        project = (String) azureArtifactsConfig.getProject().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAzureArtifactsConnector =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, azureConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project  in case of expressions
    String resolvedProject = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAzureArtifactsConnector, accountId, orgIdentifier, projectIdentifier);

    List<AzureArtifactsFeed> response = azureArtifactsResourceService.listAzureArtifactsFeeds(
        connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedProject);

    return ResponseDTO.newResponse(response);
  }
}
