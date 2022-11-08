/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.service.intf.CCMActiveSpendService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("active-spend")
@Path("/active-spend")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class CCMActiveSpendResource {
  @Inject CCMActiveSpendService activeSpendService;

  @GET
  @ApiOperation(value = "Get Active spend for given time period", nickname = "getActiveSpend")
  @Operation(operationId = "getActiveSpend",
      summary = "Get Active spend for given start time and end time, per Account Identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns stats description object") })
  public ResponseDTO<io.harness.ccm.remote.beans.CostOverviewDTO>
  getActiveSpend(@Parameter(description = "Account id to get the active spend.") @QueryParam(
                     "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @QueryParam("startTime") long startTime, @QueryParam("endTime") long endTime) {
    return ResponseDTO.newResponse(activeSpendService.getActiveSpendStats(startTime, endTime, accountIdentifier));
  }

  @GET
  @Path("forecast")
  @ApiOperation(value = "Get Forecasted spend for the next time period", nickname = "getForecastedSpend")
  @Operation(operationId = "getForecastedSpend",
      summary =
          "Get Forecasted spend for the next time period corresponding to given start time and end time, per Account Identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns stats description object") })
  public ResponseDTO<io.harness.ccm.remote.beans.CostOverviewDTO>
  getForecastedSpend(@Parameter(description = "Account id to get the forecasted spend.") @QueryParam(
                         "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @QueryParam("startTime") long startTime, @QueryParam("endTime") long endTime) {
    return ResponseDTO.newResponse(activeSpendService.getForecastedSpendStats(startTime, endTime, accountIdentifier));
  }
}
