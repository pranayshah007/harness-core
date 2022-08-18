/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.usage.CELicenseUsageDTO;
import io.harness.ccm.service.impl.LicenseUsageInterfaceImpl;
import io.harness.licensing.usage.params.UsageRequestParams;
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

@Api("license-util")
@Path("/license-util")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class CELicenseUtilisationResource {
  @Inject LicenseUsageInterfaceImpl licenseUsageInterface;

  @GET
  @ApiOperation(value = "Gets License Usage from a Particular Timestamp", nickname = "getCCMLicenseUsage")
  @Operation(operationId = "getCCMLicenseUsage",
      summary = "Gets License Usage From a Timestamp, per Account Identifier",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a license usage object") })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<CELicenseUsageDTO>
  getLicenseUsage(@Parameter(description = "Account id to get the license usage.") @QueryParam("accountIdentifier")
                  @AccountIdentifier String accountIdentifier, @QueryParam("timestamp") long timestamp) {
    return ResponseDTO.newResponse(licenseUsageInterface.getLicenseUsage(
        accountIdentifier, ModuleType.CE, timestamp, UsageRequestParams.builder().build()));
  }
}
