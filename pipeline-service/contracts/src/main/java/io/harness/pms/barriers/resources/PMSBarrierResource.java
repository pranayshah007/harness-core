/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.resources;

import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.barriers.response.BarrierExecutionInfoDTO;
import io.harness.pms.barriers.response.BarrierInfoDTO;
import io.harness.pms.barriers.response.BarrierSetupInfoDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/barriers")
@Path("/barriers")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public interface PMSBarrierResource {
  @PUT
  @Path("/setupInfo")
  @ApiOperation(value = "Gets barriers setup info list", nickname = "getBarriersSetupInfoList")
  ResponseDTO<List<BarrierSetupInfoDTO>> getBarriersSetupInfoList(@NotNull @ApiParam(hidden = true) String yaml);

  @GET
  @Path("/executionInfo")
  @ApiOperation(value = "Gets barriers execution info list", nickname = "getBarriersExecutionInfo")
  ResponseDTO<List<BarrierExecutionInfoDTO>> getBarriersExecutionInfo(
      @NotNull @QueryParam("stageSetupId") String stageSetupId,
      @NotNull @QueryParam("planExecutionId") String planExecutionId);

  @GET
  @Path("/info")
  @ApiOperation(value = "Gets barriers info", nickname = "getBarrierInfo")
  ResponseDTO<BarrierInfoDTO> getBarriersInfo(@NotNull @QueryParam("barrierSetupId") String barrierSetupId,
      @NotNull @QueryParam("planExecutionId") String planExecutionId);
}
