/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.aws.AwsPrometheusWorkspaceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.AwsService;
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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("aws")
@Path("/aws")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(CV)
public class AwsResource {
  @Inject private AwsService awsService;

  @GET
  @Path("/regions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get regions", nickname = "getAllAwsRegions")
  public ResponseDTO<List<String>> getRegions() {
    return ResponseDTO.newResponse(awsService.fetchRegions());
  }

  @GET
  @Path("/prometheus/workspaces")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get Prometheus Workspaces", nickname = "getPrometheusWorkspaces")
  public ResponseDTO<List<AwsPrometheusWorkspaceDTO>> getPrometheusWorkspaces(
      @NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull @NotBlank String connectorIdentifier,
      @QueryParam("region") @NotNull @NotBlank String region,
      @QueryParam("tracingId") @NotNull @NotBlank String tracingId) {
    return ResponseDTO.newResponse(
        awsService.fetchAllWorkspaces(projectParams, connectorIdentifier, region, tracingId));
  }
}
