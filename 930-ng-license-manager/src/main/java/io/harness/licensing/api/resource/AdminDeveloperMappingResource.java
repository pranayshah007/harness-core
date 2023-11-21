/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.licensing.api.resource;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.beans.modules.DeveloperMappingDTO;
import io.harness.licensing.services.DeveloperMappingService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api("admin/developer-license-mapping")
@Path("admin/developer-license-mapping")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@Hidden
@OwnedBy(HarnessTeam.GTM)
public class AdminDeveloperMappingResource {
  private final DeveloperMappingService developerMappingService;
  @Inject
  public AdminDeveloperMappingResource(DeveloperMappingService developerMappingService) {
    this.developerMappingService = developerMappingService;
  }

  @GET
  @Path("{accountIdentifier}")
  @InternalApi
  @Operation(operationId = "queryAccountLevelDeveloperMapping",
      summary = "Admin retrieves developer to secondary entitlement mapping for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of created account level developer mappings of the account")
      })
  public ResponseDTO<List<DeveloperMappingDTO>>
  getAccountLevelDeveloperMapping(
      @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(developerMappingService.getAccountLevelDeveloperMapping(accountIdentifier));
  }

  @POST
  @Path("{accountIdentifier}")
  @InternalApi
  @Operation(operationId = "createAccountLevelDeveloperMapping",
      summary = "Admin creates developer to secondary entitlement mapping for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the created account level developer mapping of the account")
      })
  public ResponseDTO<DeveloperMappingDTO>
  createAccountLevelDeveloperMapping(
      @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Body DeveloperMappingDTO developerMappingDTO) {
    DeveloperMappingDTO createdDeveloperMappingDTO =
        developerMappingService.createAccountLevelDeveloperMapping(accountIdentifier, developerMappingDTO);
    return ResponseDTO.newResponse(createdDeveloperMappingDTO);
  }
}
