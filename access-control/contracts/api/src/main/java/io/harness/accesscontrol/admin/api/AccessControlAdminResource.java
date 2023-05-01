/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.admin.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.security.annotations.InternalApi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@OwnedBy(PL)
@Api(value = "/admin", hidden = true)
@Path("/admin")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public interface AccessControlAdminResource {
  @POST
  @Path("block")
  @ApiOperation(value = "Blocks processing ACLs for a given account", nickname = "blockAccount", hidden = true)
  @Operation(operationId = "blockAccount", summary = "Blocks processing ACLs for a given account", hidden = true)
  @InternalApi
  Response blockAccount(@Valid BlockAccountDTO blockAccountDTO);

  @POST
  @Path("unblock")
  @ApiOperation(value = "Unblocks processing ACLs for a given account", nickname = "unblockAccount", hidden = true)
  @Operation(operationId = "unblockAccount", summary = "UnBlocks processing ACLs for a given account", hidden = true)
  @InternalApi
  Response unblockAccount(@Valid UnblockAccountDTO unblockAccountDTO);

  @GET
  @Path("blocked")
  @ApiOperation(value = "Get list of blocked accounts", nickname = "getBlockedAccounts", hidden = true)
  @Operation(operationId = "getBlockedAccounts", summary = "Get list of blocked accounts", hidden = true)
  @InternalApi
  Response getBlockedAccounts();
}
