/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.resourceconstraints.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.resourceconstraints.response.ResourceConstraintExecutionInfoDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/resource-constraints")
@Path("/resource-constraints")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.PIPELINE)
public interface PMSResourceConstraintResource {
  @GET
  @Path("/executionInfo")
  @ApiOperation(value = "Gets resource constraints execution info", nickname = "getResourceConstraintsExecutionInfo")
  ResponseDTO<ResourceConstraintExecutionInfoDTO> getResourceConstraintsExecutionInfo(
      @NotNull @QueryParam("accountId") String accountId, @NotNull @QueryParam("resourceUnit") String resourceUnit);
}
