/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("slo-dashboard")
@Path("slo-dashboard")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class SLODashboardResource {
  @Inject private SLODashboardService sloDashboardService;

  public static final String SLO = "SLO";
  public static final String VIEW_PERMISSION = "chi_slo_view";

  @GET
  @Path("widgets")
  @ExceptionMetered
  @ApiOperation(value = "get widget list", nickname = "getSLODashboardWidgets")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLODashboardWidget>> getSloDashboardWidgets(
      @NotNull @BeanParam ProjectParams projectParams, @BeanParam SLODashboardApiFilter filter,
      @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(sloDashboardService.getSloDashboardWidgets(projectParams, filter, pageParams));
  }

  @GET
  @Path("widgets/list")
  @ExceptionMetered
  @ApiOperation(value = "get slo list view", nickname = "getSLOHealthListView")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLOHealthListView>> getSloHealthListView(
      @NotNull @BeanParam ProjectParams projectParams, @BeanParam SLODashboardApiFilter filter,
      @BeanParam PageParams pageParams, @QueryParam("filter") String filterByName) {
    return ResponseDTO.newResponse(
        sloDashboardService.getSloHealthListView(projectParams, filter, pageParams, filterByName));
  }

  @GET
  @Path("widget/{identifier}")
  @ExceptionMetered
  @ApiOperation(value = "get SLO Dashboard Detail", nickname = "getSLODetails")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<SLODashboardDetail> getSloDashboardWidget(
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @QueryParam("startTime") Long startTime, @QueryParam("endTime") Long endTime,
      @NotNull @Valid @BeanParam ProjectParams projectParams) {
    return ResponseDTO.newResponse(
        sloDashboardService.getSloDashboardDetail(projectParams, identifier, startTime, endTime));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("risk-count")
  @ApiOperation(
      value = "get all service level objectives count by risk", nickname = "getServiceLevelObjectivesRiskCount")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<SLORiskCountResponse>
  getServiceLevelObjectivesRiskCount(
      @NotNull @BeanParam ProjectParams projectParams, @BeanParam SLODashboardApiFilter serviceLevelObjectiveFilter) {
    return ResponseDTO.newResponse(sloDashboardService.getRiskCount(projectParams, serviceLevelObjectiveFilter));
  }
}
