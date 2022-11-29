/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceMetricDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceSampleDataRequestDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceServiceDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceValidateDataRequestDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DynatraceService;
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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("dynatrace")
@Path("/dynatrace")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class DynatraceResource {
  @Inject() private DynatraceService dynatraceService;

  @GET
  @Path("/services")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all dynatrace services", nickname = "getDynatraceServices")
  public ResponseDTO<List<DynatraceServiceDTO>> getDynatraceServices(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(dynatraceService.getAllServices(projectParams, connectorIdentifier, tracingId));
  }

  @GET
  @Path("/metrics")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all dynatrace service metrics", nickname = "getAllDynatraceServiceMetrics")
  public ResponseDTO<List<DynatraceMetricDTO>> getDynatraceMetrics(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,

      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(dynatraceService.getAllMetrics(projectParams, connectorIdentifier, tracingId));
  }

  @GET
  @Path("/service-details")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get dynatrace service details", nickname = "getDynatraceServiceDetails")
  public ResponseDTO<DynatraceServiceDTO> getDynatraceServiceDetails(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("serviceId") @NotNull String serviceId, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(
        dynatraceService.getServiceDetails(projectParams, connectorIdentifier, serviceId, tracingId));
  }

  @POST
  @Path("/metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metric data for given metric packs", nickname = "getDynatraceMetricData")
  public ResponseDTO<Set<MetricPackValidationResponse>> getDynatraceMetricData(
      @NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("tracingId") @NotNull String tracingId,
      @NotNull @Valid @Body DynatraceValidateDataRequestDTO validateDataRequestDTO) {
    return ResponseDTO.newResponse(dynatraceService.validateData(projectParams, connectorIdentifier,
        validateDataRequestDTO.getServiceMethodsIds(), validateDataRequestDTO.getMetricPacks(), tracingId));
  }

  @POST
  @Path("/fetch-sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get dynatrace sample data", nickname = "getDynatraceSampleData")
  public ResponseDTO<List<TimeSeriesSampleDTO>> getDynatraceSampleData(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("tracingId") @NotNull String tracingId,
      @NotNull @Body DynatraceSampleDataRequestDTO sampleDataRequestDTO) {
    return ResponseDTO.newResponse(dynatraceService.fetchSampleData(projectParams, connectorIdentifier,
        sampleDataRequestDTO.getServiceId(), sampleDataRequestDTO.getMetricSelector(), tracingId));
  }
}
