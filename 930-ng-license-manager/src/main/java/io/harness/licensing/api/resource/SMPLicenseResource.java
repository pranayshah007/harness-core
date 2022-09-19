/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.api.resource;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPDecLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;

@Api("/smp/licenses")
@Path("/smp/licenses")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
        {
                @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
                , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
        })
@NextGenManagerAuth
@Hidden
public class SMPLicenseResource {
    private final LicenseService licenseService;

    @Inject
    public SMPLicenseResource(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @POST
    @Path("validate/{accountIdentifier}")
    @InternalApi
    @ApiOperation(value = "Validate License Under Account", nickname = "validateSMPLicense", hidden = true)
    public ResponseDTO<SMPDecLicenseDTO> validateSMPLicense(
            @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
            @NotNull @Valid SMPEncLicenseDTO licenseDTO) {
        SMPDecLicenseDTO decryptedLicense = licenseService.validateSMPLicense(licenseDTO);
        return ResponseDTO.newResponse(decryptedLicense);
    }

    @POST
    @Path("generate/{accountIdentifier}")
    @InternalApi
    @ApiOperation(value = "Generate License Under Account", nickname = "generateSMPLicense", hidden = true)
    public ResponseDTO<SMPEncLicenseDTO> generateSMPLicense(
            @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
        SMPEncLicenseDTO encryptedLicense = licenseService.generateSMPLicense(accountIdentifier);
        return ResponseDTO.newResponse(encryptedLicense);
    }

    @POST
    @Path("apply/{accountIdentifier}")
    @InternalApi
    @ApiOperation(value = "Apply License Under Account", nickname = "applySMPLicense", hidden = true)
    public ResponseDTO<String> applySMPLicense(
            @PathParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
            @NotNull @Valid SMPEncLicenseDTO licenseDTO) {
        licenseService.applySMPLicense(licenseDTO);
        return ResponseDTO.newResponse("Successfully applied license");
    }
}
