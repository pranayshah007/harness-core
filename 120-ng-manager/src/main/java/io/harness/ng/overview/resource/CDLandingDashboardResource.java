/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.resource;

import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ACCOUNT;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dashboards.DeploymentStatsSummary;
import io.harness.dashboards.EnvCount;
import io.harness.dashboards.LandingDashboardRequestCD;
import io.harness.dashboards.PipelinesExecutionDashboardInfo;
import io.harness.dashboards.ProjectsDashboardInfo;
import io.harness.dashboards.ServicesCount;
import io.harness.dashboards.ServicesDashboardInfo;
import io.harness.dashboards.SortBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.overview.service.CDLandingDashboardService;
import io.harness.pms.dashboards.GroupBy;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Api("landingDashboards")
@Path("/landingDashboards")
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
public class CDLandingDashboardResource {
  private final CDLandingDashboardService cdLandingDashboardService;
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  private long epochShouldBeOfStartOfDay(long epoch) {
    return epoch - epoch % DAY_IN_MS;
  }

  @POST
  @Path("/activeServices")
  @ApiOperation(value = "Get Most Active Services", nickname = "getActiveServices")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ServicesDashboardInfo> getActiveServices(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @QueryParam("sortBy") SortBy sortBy, @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    log.info("Getting most active services by: " + sortBy);
    return ResponseDTO.newResponse(cdLandingDashboardService.getActiveServices(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers(), startInterval, endInterval, sortBy));
  }

  @POST
  @Path("/topProjects")
  @ApiOperation(value = "Get Top Projects as per Deployments", nickname = "getTopProjects")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ProjectsDashboardInfo> getTopProjects(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    log.info("Getting top projects");
    return ResponseDTO.newResponse(cdLandingDashboardService.getTopProjects(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers(), startInterval, endInterval));
  }

  @POST
  @Path("/deploymentStatsSummary")
  @ApiOperation(value = "Get deployment stats summary", nickname = "getDeploymentStatsSummary")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<DeploymentStatsSummary> getDeploymentStatsSummary(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull @QueryParam("groupBy") GroupBy groupBy, @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    return ResponseDTO.newResponse(cdLandingDashboardService.getDeploymentStatsSummary(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers(), startInterval, endInterval, groupBy));
  }

  @POST
  @Path("/activeDeploymentStats")
  @ApiOperation(value = "Get active deployment stats", nickname = "getActiveDeploymentStats")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<PipelinesExecutionDashboardInfo> getActiveDeploymentStats(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    return ResponseDTO.newResponse(cdLandingDashboardService.getActiveDeploymentStats(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers()));
  }

  @POST
  @Path("/servicesCount")
  @ApiOperation(value = "Get services count", nickname = "getServicesCount")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<ServicesCount> getServicesCount(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    return ResponseDTO.newResponse(cdLandingDashboardService.getServicesCount(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers(), startInterval, endInterval));
  }

  @POST
  @Path("/envCount")
  @ApiOperation(value = "Get environments count", nickname = "getEnvCount")
  @NGAccessControlCheck(resourceType = ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<EnvCount> getEnvCount(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval,
      @NotNull LandingDashboardRequestCD landingDashboardRequestCD) {
    return ResponseDTO.newResponse(cdLandingDashboardService.getEnvCount(
        accountIdentifier, landingDashboardRequestCD.getOrgProjectIdentifiers(), startInterval, endInterval));
  }
}
