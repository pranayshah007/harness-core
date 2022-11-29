/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDTO;
import io.harness.cvng.core.beans.datadog.DatadogDashboardDetail;
import io.harness.cvng.core.beans.datadog.MetricTagResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DatadogService;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("datadog-metrics")
@Path("/datadog-metrics")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class DatadogMetricsResource {
  @Inject private DatadogService datadogService;

  @GET
  @Path("/dashboards")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all datadog dashboards", nickname = "getDatadogDashboards")
  public ResponseDTO<PageResponse<DatadogDashboardDTO>> getDatadogDashboards(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @QueryParam("pageSize") @NotNull int pageSize, @QueryParam("offset") @NotNull int offset,
      @QueryParam("filter") String filter, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        datadogService.getAllDashboards(projectParams, connectorIdentifier, pageSize, offset, filter, tracingId));
  }

  @GET
  @Path("/dashboard-details")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog dashboard details", nickname = "getDatadogDashboardDetails")
  public ResponseDTO<List<DatadogDashboardDetail>> getDatadogDashboardDetails(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("dashboardId") String dashboardId, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        datadogService.getDashboardDetails(projectParams, connectorIdentifier, dashboardId, tracingId));
  }

  @GET
  @Path("/active-metrics")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog active metrics", nickname = "getDatadogActiveMetrics")
  public ResponseDTO<List<String>> getActiveMetrics(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier, @QueryParam("filter") String filter,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        datadogService.getActiveMetrics(projectParams, connectorIdentifier, filter, tracingId));
  }

  @GET
  @Path("/metric-tags")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog metric tag list", nickname = "getDatadogMetricTagsList")
  public ResponseDTO<List<String>> getDatadogMetricTagsList(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("metric") String metricName, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        datadogService.getMetricTagsList(projectParams, connectorIdentifier, metricName, tracingId));
  }

  @GET
  @Path("/metric-tags-v2")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog metric tag list", nickname = "getDatadogMetricTags")
  public ResponseDTO<MetricTagResponseDTO> getDatadogMetricTags(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("metric") String metricName, @QueryParam("filter") String filter,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        datadogService.getMetricTagsResponse(projectParams, connectorIdentifier, metricName, filter, tracingId));
  }

  @GET
  @Path("/sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog sample data", nickname = "getDatadogSampleData")
  public ResponseDTO<List<TimeSeriesSampleDTO>> getDatadogSampleData(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId, @NotNull @QueryParam("query") String query) {
    return ResponseDTO.newResponse(
        datadogService.getTimeSeriesPoints(projectParams, connectorIdentifier, query, tracingId));
  }
}
