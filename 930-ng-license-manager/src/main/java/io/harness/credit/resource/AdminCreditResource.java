/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.services.CreditService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/admin/credits")
@Path("/admin/credits")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Hidden
@OwnedBy(GTM)
public class AdminCreditResource {
  private final CreditService creditService;

  @Inject
  public AdminCreditResource(CreditService creditService) {
    this.creditService = creditService;
  }

  @POST
  @Path("/create")
  @ApiOperation(value = "Admin Level purchase credit for an account", nickname = "adminCreateCredit")
  @Operation(operationId = "adminCreateCredit", summary = "Admin level purchase credit for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Purchased credits of the account")
      })
  @InternalApi
  public ResponseDTO<CreditDTO>
  createCredits(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Valid CreditDTO creditDTO) {
    CreditDTO created = creditService.purchaseCredit(accountIdentifier, creditDTO);
    return ResponseDTO.newResponse(created);
  }

  @GET
  @Path("{accountIdentifier}")
  @ApiOperation(
      value = "Admin level get purchase history of credits for an Account in an ascending order of the expiry time",
      nickname = "adminGetCreditsByAccount")
  @Operation(operationId = "adminGetCreditsByAccount",
      summary = "Admin level get purchase history of credits for an Account in an ascending order of the expiry time",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all of a credits purchase of an account")
      })
  @InternalApi
  public ResponseDTO<List<CreditDTO>>
  getCredits(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @PathParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(creditService.getCredits(accountIdentifier));
  }
}
