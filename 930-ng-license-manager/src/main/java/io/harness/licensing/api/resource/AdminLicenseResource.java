/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.api.resource;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.subscription.services.SubscriptionService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/admin/licenses")
@Path("/admin/licenses")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@Hidden
public class AdminLicenseResource {
  private static final String MODULE_TYPE_KEY = "moduleType";
  private final LicenseService licenseService;
  private final SubscriptionService subscriptionService;

  @Inject
  public AdminLicenseResource(LicenseService licenseService, SubscriptionService subscriptionService) {
    this.licenseService = licenseService;
    this.subscriptionService = subscriptionService;
  }

  @GET
  @Path("{accountIdentifier}")
  @InternalApi
  @ApiOperation(value = "Get All Module License Under Account", nickname = "queryAccountLicense", hidden = true)
  public ResponseDTO<AccountLicenseDTO> getAccountLicensesDTO(
      @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    // TODO change to Admin Auth when it's ready
    AccountLicenseDTO accountLicenses = licenseService.getAccountLicense(accountIdentifier);
    return ResponseDTO.newResponse(accountLicenses);
  }

  @POST
  @InternalApi
  @ApiOperation(value = "Create Module License", nickname = "createModuleLicense", hidden = true)
  public ResponseDTO<ModuleLicenseDTO> create(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @Valid ModuleLicenseDTO moduleLicenseDTO) {
    // TODO change to Admin Auth when it's ready
    if (!moduleLicenseDTO.isSelfService()) {
      subscriptionService.cancelAllSubscriptions(accountIdentifier);
    }
    ModuleLicenseDTO created = licenseService.createModuleLicense(moduleLicenseDTO);
    return ResponseDTO.newResponse(created);
  }

  @PUT
  @Path("{identifier}")
  @InternalApi
  @ApiOperation(value = "Update Module License", nickname = "updateModuleLicense", hidden = true)
  public ResponseDTO<ModuleLicenseDTO> update(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @PathParam("identifier") String identifier, @NotNull @Valid ModuleLicenseDTO moduleLicenseDTO) {
    // TODO change to Admin Auth when it's ready
    if (!moduleLicenseDTO.isSelfService()) {
      subscriptionService.cancelAllSubscriptions(accountIdentifier);
    }
    ModuleLicenseDTO updated = licenseService.updateModuleLicense(moduleLicenseDTO);
    return ResponseDTO.newResponse(updated);
  }

  @DELETE
  @Path("{identifier}")
  @InternalApi
  @ApiOperation(value = "Delete Module License", nickname = "deleteModuleLicense", hidden = true)
  public ResponseDTO<Void> delete(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @PathParam("identifier") String identifier) {
    // TODO change to Admin Auth when it's ready
    licenseService.deleteModuleLicense(identifier);
    return ResponseDTO.newResponse();
  }
}
