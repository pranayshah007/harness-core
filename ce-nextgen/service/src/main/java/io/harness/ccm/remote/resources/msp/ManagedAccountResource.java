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
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("managed-account")
@Path("/managed-account")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Hidden
@Slf4j
@Service
@OwnedBy(CE)
public class ManagedAccountResource {
  @Inject private ManagedAccountService managedAccountService;

  @POST
  @ApiOperation(value = "Create managed account record", nickname = "createManagedAccount")
  @Operation(operationId = "createManagedAccount", summary = "Create managed account record",
      responses = { @ApiResponse(description = "Returns id of object created") })
  public ResponseDTO<String>
  save(@Parameter(description = "Account id of the msp account") @QueryParam(
           "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "Managed Account") @NotNull @Valid ManagedAccount managedAccount) {
    return ResponseDTO.newResponse(managedAccountService.save(managedAccount));
  }

  @GET
  @ApiOperation(value = "Get managed account", nickname = "getManagedAccount")
  @Operation(operationId = "getManagedAccount", summary = "Get managed account details for given managed account id",
      responses = { @ApiResponse(description = "Returns managed account record") })
  public ResponseDTO<ManagedAccount>
  get(@Parameter(description = "Account id of the msp account") @QueryParam("accountIdentifier")
      @AccountIdentifier String accountIdentifier, @QueryParam("managedAccountId") String managedAccountId) {
    return ResponseDTO.newResponse(managedAccountService.get(accountIdentifier, managedAccountId));
  }

  @GET
  @Path("list")
  @ApiOperation(value = "List managed accounts", nickname = "listManagedAccounts")
  @Operation(operationId = "listManagedAccounts", summary = "List managed accounts for given msp account",
      responses = { @ApiResponse(description = "Returns list of managed accounts for the msp account") })
  public ResponseDTO<List<ManagedAccount>>
  list(@Parameter(description = "Account id of the msp account") @QueryParam(
      "accountIdentifier") @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(managedAccountService.list(accountIdentifier));
  }

  @PUT
  @ApiOperation(value = "Update managed account", nickname = "updateManagedAccount")
  @Operation(operationId = "updateManagedAccount", summary = "Update managed account record",
      responses = { @ApiResponse(description = "Returns managed account record") })
  public ResponseDTO<ManagedAccount>
  update(@Parameter(description = "Account id of the msp account") @QueryParam(
             "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "Managed Account") @NotNull @Valid ManagedAccount managedAccount) {
    return ResponseDTO.newResponse(managedAccountService.update(managedAccount));
  }

  @DELETE
  @ApiOperation(value = "Delete managed account", nickname = "deleteManagedAccount")
  @Operation(operationId = "deleteManagedAccount", summary = "Delete managed account record",
      responses = { @ApiResponse(description = "Returns boolean indicating deletion status") })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Account id of the msp account") @QueryParam("accountIdentifier")
         @AccountIdentifier String accountIdentifier, @QueryParam("managedAccountId") String managedAccountId) {
    return ResponseDTO.newResponse(managedAccountService.delete(managedAccountId));
  }
}
