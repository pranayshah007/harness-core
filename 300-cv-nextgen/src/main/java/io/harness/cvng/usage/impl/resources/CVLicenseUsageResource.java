/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl.resources;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.cvng.usage.impl.CVLicenseUsageDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
@Api("/usage")
@Path("/usage")
@Produces("application/json")
@NextGenManagerAuth
public class CVLicenseUsageResource {
  @Inject LicenseUsageInterface licenseUsageInterface;

  @GET
  @Path("/CV")
  @ApiOperation(value = "Gets License Usage for CV", nickname = "getLicenseUsage")
  public ResponseDTO<CVLicenseUsageDTO> getLicenseUsage(
      @QueryParam("accountIdentifier") @AccountIdentifier @NotNull String accountIdentifier,
      @QueryParam("timestamp") long timestamp) {
    try {
      ModuleType moduleType = ModuleType.fromString("CV");

      return ResponseDTO.newResponse((CVLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(
          accountIdentifier, moduleType, timestamp, UsageRequestParams.builder().build()));
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Module is invalid", e);
    }
  }
}
