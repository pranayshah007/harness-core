/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.cvng.core.beans.LogSampleRequestDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.ELKService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("elk/")
@Path("elk")
@Produces("application/json")
@NextGenManagerAuth
public class ELKResource {
  @Inject private ELKService elkService;

  @GET
  @Path("indices")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "gets indices in ELK", nickname = "getELKIndices")
  public ResponseDTO<List<String>> getIndices(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") String connectorIdentifier,
      @QueryParam("tracingId") @NotNull String tracingId) {
    return ResponseDTO.newResponse(elkService.getLogIndexes(projectParams, connectorIdentifier, tracingId));
  }

  @GET
  @Path("sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data for a query", nickname = "getELKLogSampleData")
  public ResponseDTO<List<LinkedHashMap>> getDatadogSampleData(@BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @NotNull @QueryParam("tracingId") String tracingId, @NotNull @QueryParam("index") String index,
      @Body LogSampleRequestDTO logSampleRequestDTO) {
    return ResponseDTO.newResponse(
        elkService.getSampleData(projectParams, connectorIdentifier, logSampleRequestDTO.getQuery(), index, tracingId));
  }
}
