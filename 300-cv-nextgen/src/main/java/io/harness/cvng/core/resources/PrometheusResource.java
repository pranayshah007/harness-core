/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.PrometheusSampleData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.PrometheusService;
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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("prometheus")
@Path("/prometheus")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class PrometheusResource {
  @Inject private PrometheusService prometheusService;

  @GET
  @Path("/metric-list")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all metric names", nickname = "getMetricNames")
  public ResponseDTO<List<String>> getMetricNames(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("filter") @DefaultValue("") String filter, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(prometheusService.getMetricNames(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), tracingId));
  }

  @GET
  @Path("/label-names")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all label names", nickname = "getLabelNames")
  public ResponseDTO<List<String>> getLabelNames(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(prometheusService.getLabelNames(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), tracingId));
  }

  @GET
  @Path("/label-values")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all label values", nickname = "getLabeValues")
  public ResponseDTO<List<String>> getLabeValues(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("labelName") @NotNull String labelName, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        prometheusService.getLabelValues(projectParams.getAccountIdentifier(), connectorIdentifier,
            projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), labelName, tracingId));
  }

  @GET
  @Path("/sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data", nickname = "getSampleData")
  public ResponseDTO<List<PrometheusSampleData>> getSampleData(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("query") @NotNull String query, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(prometheusService.getSampleData(projectParams.getAccountIdentifier(),
        connectorIdentifier, projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), query, tracingId));
  }
}
