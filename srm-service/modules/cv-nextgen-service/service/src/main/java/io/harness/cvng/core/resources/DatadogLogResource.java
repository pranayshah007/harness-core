/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.LogSampleRequestDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DatadogService;
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
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("datadog-logs")
@Path("/datadog-logs")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class DatadogLogResource {
  @Inject private DatadogService datadogService;

  @POST
  @Path("/sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data for a query", nickname = "getDatadogLogSampleData")
  public ResponseDTO<List<LinkedHashMap>> getDatadogSampleData(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId, @Body LogSampleRequestDTO logSampleRequestDTO) {
    return ResponseDTO.newResponse(
        datadogService.getSampleLogData(projectParams, connectorIdentifier, logSampleRequestDTO.getQuery(), tracingId));
  }

  @GET
  @Path("/log-indexes")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get datadog log indexes", nickname = "getDatadogLogIndexes")
  public ResponseDTO<List<String>> getDatadogLogIndexes(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(datadogService.getLogIndexes(projectParams, connectorIdentifier, tracingId));
  }
}
