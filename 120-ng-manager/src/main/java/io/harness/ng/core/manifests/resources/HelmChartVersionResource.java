/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests.resources;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.resources.HelmChartService;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OciHelmChartStoreEcrConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
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
  private final HelmChartVersionResourceUtils helmChartVersionResourceUtils;

  // this is kept for compatibility reasons.
  // Once the UI completely moves the code to use getHelmChartVersionDetailsV1 and getHelmChartVersionDetailsV2 then
  // this method can be removed.
  @GET
  @Path("chart/version")
  @ApiOperation(value = "Gets helm chart version details", nickname = "getHelmChartVersionDetails")
  public ResponseDTO<HelmChartResponseDTO> getHelmChartVersionDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @QueryParam("fqnPath") String fqnPath,
      @QueryParam("connectorRef") String connectorIdentifier, @QueryParam("chartName") String chartName,
      @QueryParam("region") String region, @QueryParam("bucketName") String bucketName,
      @QueryParam("folderPath") String folderPath, @QueryParam("lastTag") String lastTag,
      @QueryParam("registryId") String registryId) {
    HelmChartResponseDTO helmChartResponseDTO =
        helmChartService.getHelmChartVersionDetailsV2(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath,
            connectorIdentifier, chartName, region, bucketName, folderPath, lastTag, registryId);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }

  @GET
  @Path("v1/chart/version")
  @ApiOperation(value = "Gets helm chart version details", nickname = "getHelmChartVersionDetailsV1")
  public ResponseDTO<HelmChartResponseDTO> getHelmChartVersionDetailsV1(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("connectorRef") String connectorIdentifier,
      @NotNull @QueryParam("chartName") String chartName, @QueryParam("region") String region,
      @QueryParam("bucketName") String bucketName, @QueryParam("folderPath") String folderPath,
      @QueryParam("lastTag") String lastTag, @NotNull @QueryParam("storeType") String storeType,
      @QueryParam("ociHelmChartStoreConfigType") String ociHelmChartStoreConfigType,
      @QueryParam("helmVersion") String helmVersion, @QueryParam("registryId") String registryId) {
    HelmChartResponseDTO helmChartResponseDTO = helmChartService.getHelmChartVersionDetails(accountId, orgIdentifier,
        projectIdentifier, connectorIdentifier, chartName, region, bucketName, folderPath, lastTag, storeType,
        ociHelmChartStoreConfigType, helmVersion, registryId);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }

  @POST
  @Path("v2/chart/version")
  @ApiOperation(value = "Gets helm chart version details with yaml input for expression resolution",
      nickname = "getHelmChartVersionDetailsWithYaml")
  public ResponseDTO<HelmChartResponseDTO>
  getHelmChartVersionDetailsWithYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @NotNull @QueryParam("fqnPath") String fqnPath, @QueryParam("connectorRef") String connectorIdentifier,
      @QueryParam("chartName") String chartName, @QueryParam("region") String region,
      @QueryParam("bucketName") String bucketName, @QueryParam("folderPath") String folderPath,
      @QueryParam("lastTag") String lastTag, @QueryParam("registryId") String registryId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull String runtimeInputYaml) {
    if (isNotEmpty(serviceRef)) {
      HelmChartManifest helmChartManifest =
          (HelmChartManifest) helmChartService
              .locateManifestInService(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath)
              .getSpec();
      StoreConfig storeConfig = helmChartManifest.getStoreConfig();

      connectorIdentifier = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, connectorIdentifier,
          (String) storeConfig.getConnectorReference().fetchFinalValue(), fqnPath, gitEntityBasicInfo, serviceRef);

      chartName = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, runtimeInputYaml, chartName, (String) helmChartManifest.getChartName().fetchFinalValue(),
          fqnPath, gitEntityBasicInfo, serviceRef);

      if (storeConfig instanceof S3StoreConfig) {
        S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
        region = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, region, (String) s3StoreConfig.getRegion().fetchFinalValue(), fqnPath,
            gitEntityBasicInfo, serviceRef);

        bucketName = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, bucketName, (String) s3StoreConfig.getBucketName().fetchFinalValue(),
            fqnPath, gitEntityBasicInfo, serviceRef);

        folderPath = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, folderPath, (String) s3StoreConfig.getFolderPath().fetchFinalValue(),
            fqnPath, gitEntityBasicInfo, serviceRef);
      }

      if (storeConfig instanceof GcsStoreConfig) {
        GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
        bucketName = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, bucketName, (String) gcsStoreConfig.getBucketName().fetchFinalValue(),
            fqnPath, gitEntityBasicInfo, serviceRef);

        folderPath = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, folderPath, (String) gcsStoreConfig.getFolderPath().fetchFinalValue(),
            fqnPath, gitEntityBasicInfo, serviceRef);
      }

      if (storeConfig instanceof OciHelmChartConfig) {
        OciHelmChartConfig ociHelmChartConfig = (OciHelmChartConfig) storeConfig;

        if (ParameterFieldHelper.getParameterFieldValue(ociHelmChartConfig.getConfig()).getSpec()
                instanceof OciHelmChartStoreEcrConfig) {
          OciHelmChartStoreEcrConfig ociHelmChartStoreEcrConfig =
              (OciHelmChartStoreEcrConfig) ParameterFieldHelper.getParameterFieldValue(ociHelmChartConfig.getConfig())
                  .getSpec();

          region = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, runtimeInputYaml, region,
              (String) ociHelmChartStoreEcrConfig.getRegion().fetchFinalValue(), fqnPath, gitEntityBasicInfo,
              serviceRef);

          registryId = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, runtimeInputYaml, registryId,
              (String) ociHelmChartStoreEcrConfig.getRegistryId().fetchFinalValue(), fqnPath, gitEntityBasicInfo,
              serviceRef);
        }

        folderPath = helmChartVersionResourceUtils.resolveExpression(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, runtimeInputYaml, folderPath,
            (String) ociHelmChartConfig.getBasePath().fetchFinalValue(), fqnPath, gitEntityBasicInfo, serviceRef);
      }
    }
    HelmChartResponseDTO helmChartResponseDTO =
        helmChartService.getHelmChartVersionDetailsV2(accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath,
            connectorIdentifier, chartName, region, bucketName, folderPath, lastTag, registryId);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }
}
