/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.dashboard.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIUsageResult;
import io.harness.app.beans.entities.DashboardBuildExecutionInfo;
import io.harness.app.beans.entities.DashboardBuildRepositoryInfo;
import io.harness.app.beans.entities.DashboardBuildsActiveAndFailedInfo;
import io.harness.app.beans.entities.DashboardBuildsHealthInfo;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.dashboards.GroupBy;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(HarnessTeam.CI)
@Api("ci")
@Path("/ci")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Builds Overview", description = "Contains APIs related to builds overview.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface CIDashboardOverviewResource {
  String PROJECT_RESOURCE_TYPE = "PROJECT";
  String VIEW_PROJECT_PERMISSION = "core_project_view";

  @GET
  @Path("/buildHealth")
  @ApiOperation(value = "Get build health", nickname = "getBuildHealth")
  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  @Operation(operationId = "getBuildHealth",
      summary = "Gets the Build Health by accountIdentifier, orgIdentifier and projectIdentifier for an interval.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns build health with success and failure rate.")
      })
  ResponseDTO<DashboardBuildsHealthInfo>
  getBuildHealth(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval);

  @GET
  @Path("/buildExecution")
  @Operation(operationId = "getBuildExecution",
      summary =
          "Gets the list of Build Execution count by accountIdentifier, orgIdentifier and projectIdentifier for an interval.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of build Execution count per day.")
      })
  @ApiOperation(value = "Get build execution", nickname = "getBuildExecution")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  ResponseDTO<DashboardBuildExecutionInfo>
  getBuildExecution(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("groupBy") GroupBy groupBy,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval);

  @GET
  @Path("/repositoryBuild")
  @Operation(operationId = "getRepositoryBuild",
      summary =
          "Gets the list of Build Execution health of each repository by accountIdentifier, orgIdentifier and projectIdentifier for an interval.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of build Execution count and health of each repository for an interval.")
      })
  @ApiOperation(value = "Get build getRepositoryBuild", nickname = "getRepositoryBuild")
  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  ResponseDTO<DashboardBuildRepositoryInfo>
  getRepositoryBuild(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval);
  @GET
  @Path("/getBuilds")
  @Operation(operationId = "getActiveAndFailedBuild",
      summary =
          "Gets the list of Active and Failed Builds in specified days by accountIdentifier, orgIdentifier and projectIdentifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of build Execution count and health of each repository for an interval.")
      })
  @ApiOperation(value = "Get builds", nickname = "getBuilds")
  @NGAccessControlCheck(resourceType = PROJECT_RESOURCE_TYPE, permission = VIEW_PROJECT_PERMISSION)
  ResponseDTO<DashboardBuildsActiveAndFailedInfo>
  getActiveAndFailedBuild(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "Retrieves build status for the specified number of days. The default value is 20 days.")
      @QueryParam("top") @DefaultValue("20") long days);

  @GET
  @Path("/usage/ci")
  @ApiOperation(value = "Get usage", nickname = "getUsage")
  @NGAccessControlCheck(resourceType = "ACCOUNT", permission = "core_account_view")
  ResponseDTO<CIUsageResult> getCIUsageData(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.TIMESTAMP) long timestamp);
}
