/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.Dashboard;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.service.PipelineDashboardService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Api("dashboard")
@Path("dashboard")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline Dashboard", description = "This contains APIs related to Pipeline Dashboard")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@PipelineServiceAuth
@Slf4j
public class PipelineDashboardOverviewResourceV2 {
  private final PipelineDashboardService pipelineDashboardService;
  @GET
  @Path("/pipelineHealth")
  @ApiOperation(value = "Get pipeline health", nickname = "fetchPipelineHealth")
  @Operation(operationId = "getPipelinedHealth",
      summary = "Fetches Pipeline Health data for a given Interval and will be presented in day wise format",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Pipeline Health data for a given Interval and will be presented in day wise format")
      })
  @NGAccessControlCheck(resourceType = "PROJECT", permission = "core_project_view")
  @Hidden
  public ResponseDTO<DashboardPipelineHealthInfo>
  fetchPipelinedHealth(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam("moduleInfo") String moduleInfo,
      @Parameter(description = PipelineResourceConstants.START_TIME_EPOCH_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @Parameter(description = PipelineResourceConstants.END_TIME_EPOCH_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    log.info("Getting pipeline health");
    long previousInterval = startInterval - (endInterval - startInterval + DAY_IN_MS);

    return ResponseDTO.newResponse(
        pipelineDashboardService.getDashboardPipelineHealthInfo(accountIdentifier, orgIdentifier, projectIdentifier,
            pipelineIdentifier, startInterval, endInterval, previousInterval, moduleInfo));
  }

  @GET
  @Path("/pipelineExecution")
  @ApiOperation(value = "Get pipeline dashboard Execution", nickname = "getPipelineDashboardExecution")
  @Operation(operationId = "getPipelineDashboardExecution",
      summary = "Fetches Pipeline Executions details for a given Interval and will be presented in day wise format",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Fetches Pipeline Executions details for a given Interval and will be presented in day wise format")
      })
  @NGAccessControlCheck(resourceType = "PROJECT", permission = "core_project_view")
  @Hidden
  public ResponseDTO<DashboardPipelineExecutionInfo>
  getPipelineDashboardExecution(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam("moduleInfo") String moduleInfo,
      @Parameter(description = PipelineResourceConstants.START_TIME_EPOCH_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @Parameter(description = PipelineResourceConstants.END_TIME_EPOCH_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    log.info("getting pipeline execution");
    return ResponseDTO.newResponse(pipelineDashboardService.getDashboardPipelineExecutionInfo(accountIdentifier,
        orgIdentifier, projectIdentifier, pipelineIdentifier, startInterval, endInterval, moduleInfo));
  }
}
