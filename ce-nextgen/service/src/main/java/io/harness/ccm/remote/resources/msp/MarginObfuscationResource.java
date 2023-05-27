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
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("margin-details")
@Path("/margin-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class MarginObfuscationResource {
  @Inject MarginDetailsService marginDetailsService;

  @POST
  @ApiOperation(value = "Create margin details", nickname = "createMarginDetails")
  @Operation(operationId = "createMarginDetails", summary = "Create Margin details",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns id of object created") })
  public ResponseDTO<String>
  save(@Parameter(description = "Account id of the msp account") @QueryParam(
           "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "Margin details") @NotNull @Valid MarginDetails marginDetails) {
    return ResponseDTO.newResponse(marginDetailsService.save(marginDetails));
  }

  @GET
  @ApiOperation(value = "Get margin details", nickname = "getMarginDetails")
  @Operation(operationId = "getMarginDetails", summary = "Get Margin details",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns margin details for given uuid") })
  public ResponseDTO<MarginDetails>
  get(@Parameter(description = "Account id of the msp account") @QueryParam("accountIdentifier")
      @AccountIdentifier String accountIdentifier, @QueryParam("managedAccountId") String managedAccountId) {
    return ResponseDTO.newResponse(marginDetailsService.get(accountIdentifier, managedAccountId));
  }

  @GET
  @Path("list")
  @ApiOperation(value = "List margin details", nickname = "listMarginDetails")
  @Operation(operationId = "listMarginDetails", summary = "List Margin details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns list of margin details for the msp account")
      })
  public ResponseDTO<List<MarginDetails>>
  list(@Parameter(description = "Account id of the msp account") @QueryParam(
      "accountIdentifier") @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(marginDetailsService.list(accountIdentifier));
  }

  @PUT
  @ApiOperation(value = "Update margin details", nickname = "updateMarginDetails")
  @Operation(operationId = "updateMarginDetails", summary = "Update Margin details",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns id of object created") })
  public ResponseDTO<MarginDetails>
  update(@Parameter(description = "Account id of the msp account") @QueryParam(
             "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "Margin details") @NotNull @Valid MarginDetails marginDetails) {
    return ResponseDTO.newResponse(marginDetailsService.update(marginDetails));
  }

  @PUT
  @Path("unset-margins")
  @ApiOperation(value = "Unset margin details", nickname = "unsetMarginDetails")
  @Operation(operationId = "unsetMarginDetails", summary = "Unset Margin details",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns updated margin details") })
  public ResponseDTO<MarginDetails>
  unsetMarginDetails(@Parameter(description = "Account id of the msp account") @QueryParam("accountIdentifier")
                     @AccountIdentifier String accountIdentifier, @QueryParam("id") String uuid) {
    return ResponseDTO.newResponse(marginDetailsService.unsetMargins(uuid));
  }
}