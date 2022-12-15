/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.manifests.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.resources.HelmChartService;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
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
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
  @GET
  @Path("chart/version")
  @ApiOperation(value = "Gets helm chart version details", nickname = "getHelmChartVersionDetails")
  public ResponseDTO<HelmChartResponseDTO> getHelmChartVersionDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @NotNull @QueryParam("fqnPath") String fqnPath, @QueryParam("connectorRef") String connectorIdentifier,
      @QueryParam("chartName") String chartName, @QueryParam("region") String region,
      @QueryParam("bucketName") String bucketName, @QueryParam("folderPath") String folderPath) {
    HelmChartResponseDTO helmChartResponseDTO = helmChartService.getHelmChartVersionDetails(accountId, orgIdentifier,
        projectIdentifier, serviceRef, fqnPath, connectorIdentifier, chartName, region, bucketName, folderPath);
    return ResponseDTO.newResponse(helmChartResponseDTO);
  }
}
