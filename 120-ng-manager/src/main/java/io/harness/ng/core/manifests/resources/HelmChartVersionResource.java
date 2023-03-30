/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.resources.HelmChartService;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

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

@OwnedBy(CDP)
@Api("/manifests/helm")
@Path("/manifests/helm")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class HelmChartVersionResource {
  private final HelmChartService helmChartService;
  private final ArtifactResourceUtils artifactResourceUtils;

  // this is kept for compatibility reasons.
  // Once the UI completely moves the code to use getHelmChartVersionDetailsV1 and getHelmChartVersionDetailsV2 then
  // this method can be removed.
  @GET
  @Path("chart/version")
  @ApiOperation(value = "Gets helm chart version details", nickname = "getHelmChartVersionDetails")
  public ResponseDTO<HelmChartResponseDTO> getHelmChartVersionDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @QueryParam("fqnPath") String fqnPath,
      @QueryParam("connectorRef") String connectorIdentifier, @QueryParam("chartName") String chartName,
      @QueryParam("region") String region, @QueryParam("bucketName") String bucketName,
      @QueryParam("folderPath") String folderPath, @QueryParam("lastTag") String lastTag) {
    HelmChartResponseDTO helmChartResponseDTO =
        helmChartService.getHelmChartVersionDetailsV2(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath,
            connectorIdentifier, chartName, region, bucketName, folderPath, lastTag);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }

  @GET
  @Path("v1/chart/version")
  @ApiOperation(value = "Gets helm chart version details", nickname = "getHelmChartVersionDetailsV1")
  public ResponseDTO<HelmChartResponseDTO> getHelmChartVersionDetailsV1(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("connectorRef") String connectorIdentifier,
      @NotNull @QueryParam("chartName") String chartName, @QueryParam("region") String region,
      @QueryParam("bucketName") String bucketName, @QueryParam("folderPath") String folderPath,
      @QueryParam("lastTag") String lastTag, @NotNull @QueryParam("storeType") String storeType,
      @QueryParam("helmVersion") String helmVersion) {
    HelmChartResponseDTO helmChartResponseDTO =
        helmChartService.getHelmChartVersionDetails(accountId, orgIdentifier, projectIdentifier, connectorIdentifier,
            chartName, region, bucketName, folderPath, lastTag, storeType, helmVersion);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }

  @POST
  @Path("v2/chart/version")
  @ApiOperation(value = "Gets helm chart version details with yaml input for expression resolution",
      nickname = "getHelmChartVersionDetailsWithYaml")
  public ResponseDTO<HelmChartResponseDTO>
  getHelmChartVersionDetailsWithYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @NotNull @QueryParam("fqnPath") String fqnPath, @QueryParam("connectorRef") String connectorIdentifier,
      @QueryParam("chartName") String chartName, @QueryParam("region") String region,
      @QueryParam("bucketName") String bucketName, @QueryParam("folderPath") String folderPath,
      @QueryParam("lastTag") String lastTag, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull String runtimeInputYaml) {
    if (isNotEmpty(serviceRef)) {
      HelmChartManifest helmChartManifest =
          (HelmChartManifest) helmChartService
              .locateManifestInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath)
              .getSpec();
      StoreConfig storeConfig = helmChartManifest.getStoreConfig();

      if (isEmpty(connectorIdentifier)) {
        connectorIdentifier = (String) storeConfig.getConnectorReference().fetchFinalValue();
      }

      if (isEmpty(chartName)) {
        chartName = (String) helmChartManifest.getChartName().fetchFinalValue();
      }

      if (storeConfig instanceof S3StoreConfig) {
        S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
        if (isEmpty(region)) {
          region = (String) s3StoreConfig.getRegion().fetchFinalValue();
        }

        if (isEmpty(bucketName)) {
          bucketName = (String) s3StoreConfig.getBucketName().fetchFinalValue();
        }

        if (isEmpty(folderPath)) {
          folderPath = (String) s3StoreConfig.getFolderPath().fetchFinalValue();
        }

        region = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef);

        bucketName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, bucketName, fqnPath, gitEntityBasicInfo, serviceRef);

        folderPath = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, folderPath, fqnPath, gitEntityBasicInfo, serviceRef);
      }

      if (storeConfig instanceof GcsStoreConfig) {
        GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
        if (isEmpty(bucketName)) {
          bucketName = (String) gcsStoreConfig.getBucketName().fetchFinalValue();
        }

        if (isEmpty(folderPath)) {
          folderPath = (String) gcsStoreConfig.getFolderPath().fetchFinalValue();
        }

        bucketName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, bucketName, fqnPath, gitEntityBasicInfo, serviceRef);

        folderPath = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, folderPath, fqnPath, gitEntityBasicInfo, serviceRef);
      }

      connectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, connectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

      chartName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, chartName, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    HelmChartResponseDTO helmChartResponseDTO =
        helmChartService.getHelmChartVersionDetailsV2(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath,
            connectorIdentifier, chartName, region, bucketName, folderPath, lastTag);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }
}
