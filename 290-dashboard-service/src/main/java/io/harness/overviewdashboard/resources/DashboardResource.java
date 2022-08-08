/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.resources;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dashboards.SortBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.overviewdashboard.dashboardaggregateservice.service.OverviewDashboardService;
import io.harness.overviewdashboard.dtos.CountOverview;
import io.harness.overviewdashboard.dtos.DeploymentsStatsOverview;
import io.harness.overviewdashboard.dtos.ExecutionResponse;
import io.harness.overviewdashboard.dtos.ExecutionStatus;
import io.harness.overviewdashboard.dtos.TopProjectsPanel;
import io.harness.pms.dashboards.GroupBy;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.PrincipalType;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Api("overview")
@Path("/overview")
@NextGenManagerAuth
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DashboardResource {
  private final String FAILURE_MESSAGE = "Failed to get userId";

  private final OverviewDashboardService overviewDashboardService;

  private Optional<String> getUserIdentifierFromSecurityContext() {
    Optional<String> userId = Optional.empty();
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      userId = Optional.of(SourcePrincipalContextBuilder.getSourcePrincipal().getName());
    }
    return userId;
  }

  @GET
  @Path("/top-projects")
  @ApiOperation(value = "Get Top Projects", nickname = "getTopProjects")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ExecutionResponse<TopProjectsPanel>> getTopProjects(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    Optional<String> userId = getUserIdentifierFromSecurityContext();
    if (!userId.isPresent()) {
      return ResponseDTO.newResponse(ExecutionResponse.<TopProjectsPanel>builder()
                                         .executionStatus(ExecutionStatus.FAILURE)
                                         .executionMessage(FAILURE_MESSAGE)
                                         .build());
    }
    return ResponseDTO.newResponse(
        overviewDashboardService.getTopProjectsPanel(accountIdentifier, userId.get(), startInterval, endInterval));
  }

  @GET
  @Path("/deployment-stats")
  @ApiOperation(value = "Get deployment stats summary", nickname = "getDeploymentStatsOverview")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ExecutionResponse<DeploymentsStatsOverview>> getDeploymentStatsSummary(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.GROUP_BY) GroupBy groupBy,
      @NotNull @QueryParam(NGResourceFilterConstants.SORT_BY) SortBy sortBy) throws Exception {
    Optional<String> userId = getUserIdentifierFromSecurityContext();
    if (!userId.isPresent()) {
      return ResponseDTO.newResponse(ExecutionResponse.<DeploymentsStatsOverview>builder()
                                         .executionStatus(ExecutionStatus.FAILURE)
                                         .executionMessage(FAILURE_MESSAGE)
                                         .build());
    }
    return ResponseDTO.newResponse(overviewDashboardService.getDeploymentStatsOverview(
        accountIdentifier, userId.get(), startInterval, endInterval, groupBy, sortBy));
  }

  @GET
  @Path("/resources-overview-count")
  @ApiOperation(value = "Get count of projects, services, envs, pipelines", nickname = "getCounts")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ExecutionResponse<CountOverview>> getCounts(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) throws Exception {
    Optional<String> userId = getUserIdentifierFromSecurityContext();
    if (!userId.isPresent()) {
      return ResponseDTO.newResponse(ExecutionResponse.<CountOverview>builder()
                                         .executionStatus(ExecutionStatus.FAILURE)
                                         .executionMessage(FAILURE_MESSAGE)
                                         .build());
    }
    return ResponseDTO.newResponse(
        overviewDashboardService.getCountOverview(accountIdentifier, userId.get(), startInterval, endInterval));
  }
}
