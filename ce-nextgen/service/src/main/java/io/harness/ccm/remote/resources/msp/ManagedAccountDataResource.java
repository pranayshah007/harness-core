/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.msp;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.msp.entities.ManagedAccountTimeSeriesData;
import io.harness.ccm.msp.entities.ManagedAccountsOverview;
import io.harness.ccm.msp.service.intf.MarginDetailsService;
import io.harness.ccm.service.intf.MSPManagedAccountDataService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("managed-account-data")
@Path("/managed-account-data")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class ManagedAccountDataResource {
  @Inject MSPManagedAccountDataService mspManagedAccountDataService;
  @Inject MarginDetailsService marginDetailsService;

  @GET
  @Path("entity-values")
  @ApiOperation(value = "Get entity list", nickname = "getEntityList")
  @Operation(operationId = "getEntityList", summary = "Get Entity list",
      responses = { @ApiResponse(description = "Returns entity list for given child account and entity") })
  public ResponseDTO<List<String>>
  getEntityList(@Parameter(description = "Account id of the msp account") @QueryParam("accountIdentifier")
                @AccountIdentifier String accountIdentifier, @QueryParam("managedAccountId") String managedAccountId,
      @QueryParam("entity") CCMField entity, @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
    return ResponseDTO.newResponse(mspManagedAccountDataService.getEntityList(managedAccountId, entity, limit, offset));
  }

  @GET
  @Path("total-markup-and-spend")
  @ApiOperation(value = "Get total markup and spend", nickname = "getTotalMarkupAndSpend")
  @Operation(operationId = "getTotalMarkupAndSpend", summary = "Get total markup and spend for MSP managed accounts",
      responses = { @ApiResponse(description = "Returns margin details for given uuid") })
  public ResponseDTO<ManagedAccountsOverview>
  getTotalMarkupAndSpend(@Parameter(description = "Account id of the msp account") @QueryParam(
      "accountIdentifier") @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(marginDetailsService.getTotalMarkupAndSpend(accountIdentifier));
  }

  //  @GET
  //  @Path("stats")
  //  @ApiOperation(value = "Get total markup and spend", nickname = "getTotalMarkupAndSpend")
  //  @Operation(operationId = "getTotalMarkupAndSpend", summary = "Get total markup and spend for MSP managed
  //  accounts",
  //      responses = { @ApiResponse(description = "Returns spend stats for MSP managed accounts") })
  //  public ResponseDTO<ManagedAccountsOverview>
  //  getTotalMarkupAndSpend(@Parameter(description = "Account id of the msp account") @QueryParam(
  //                             "accountIdentifier") @AccountIdentifier String accountIdentifier,
  //      @Parameter(required = true, description = "Unique identifier for the managed account") @QueryParam(
  //          "managedAccountId") String managedAccountId,
  //      @QueryParam("startTime") @Parameter(required = true, description = "Start time of the period") long startTime,
  //      @QueryParam("endTime") @Parameter(required = true, description = "End time of the period") long endTime) {
  //    return ResponseDTO.newResponse(marginDetailsService.getTotalMarkupAndSpend(managedAccountId,
  //    accountIdentifier));
  //  }

  @GET
  @Path("timeseries")
  @ApiOperation(value = "Get timeseries data for managed account", nickname = "getManagedAccountTimeSeriesData")
  @Operation(operationId = "getManagedAccountTimeSeriesData",
      summary = "Get timeseries data related to total spend and markup for managed account",
      responses = { @ApiResponse(description = "Returns timeseries data for the managed account") })
  public ResponseDTO<ManagedAccountTimeSeriesData>
  getManagedAccountTimeSeriesData(@Parameter(description = "Account id of the msp account") @QueryParam(
                                      "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "Unique identifier for the managed account") @QueryParam(
          "managedAccountId") String managedAccountId,
      @QueryParam("startTime") @Parameter(required = true, description = "Start time of the period") long startTime,
      @QueryParam("endTime") @Parameter(required = true, description = "End time of the period") long endTime) {
    return ResponseDTO.newResponse(marginDetailsService.getManagedAccountTimeSeriesData(managedAccountId));
  }
}
