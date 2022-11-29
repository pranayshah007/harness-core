/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDTO;
import io.harness.cvng.core.beans.stackdriver.StackdriverDashboardDetail;
import io.harness.cvng.core.services.api.StackdriverService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("stackdriver")
@Path("/stackdriver")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class StackdriverResource {
  @Inject private StackdriverService stackdriverService;

  @GET
  @Path("/dashboards")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all stackdriver dashboards", nickname = "getStackdriverDashboards")
  public ResponseDTO<PageResponse<StackdriverDashboardDTO>> getStackdriverDashboards(
      @NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("pageSize") @NotNull int pageSize, @QueryParam("offset") @NotNull int offset,
      @QueryParam("filter") String filter, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(stackdriverService.listDashboards(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), pageSize, offset,
        filter, tracingId));
  }

  @GET
  @Path("/dashboard-detail")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get details about one dashboard", nickname = "getStackdriverDashboardDetail")
  public ResponseDTO<List<StackdriverDashboardDetail>> getStackdriverDashboardDetail(
      @NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("path") @NotNull String path, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(stackdriverService.getDashboardDetails(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), path, tracingId));
  }

  @POST
  @Path("/sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data for one metric", nickname = "getStackdriverSampleData")
  public ResponseDTO<Set<TimeSeriesSampleDTO>> getStackdriverSampleData(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId, @NotNull Object metricDefinitionDTO) {
    return ResponseDTO.newResponse(
        stackdriverService.getSampleData(projectParams.getAccountIdentifier(), connectorIdentifier,
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), metricDefinitionDTO, tracingId));
  }
}
