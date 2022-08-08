/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.usage.resources;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.cd.CDLicenseType;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.beans.LicenseUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("usage")
@Path("usage")
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Usage", description = "This contains APIs related to license usage as defined in Harness")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class LicenseUsageResource {
  @Inject LicenseUsageInterface licenseUsageInterface;

  @GET
  @Path("{module}")
  @ApiOperation(value = "Gets License Usage By Module and Timestamp", nickname = "getLicenseUsage")
  @Operation(operationId = "getLicenseUsage",
      summary = "Gets License Usage By Module, Timestamp, and Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a license usage object")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<LicenseUsageDTO>
  getLicenseUsage(@Parameter(description = "Account id to get the license usage.") @QueryParam(
                      "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @Parameter(description = "A Harness platform module.") @PathParam("module") String module,
      @QueryParam("timestamp") long timestamp, @QueryParam("CDLicenseType") String cdLicenseType) {
    try {
      ModuleType moduleType = ModuleType.fromString(module);
      if (ModuleType.CD.equals(moduleType)) {
        CDLicenseType type = CDLicenseType.valueOf(cdLicenseType);
        return ResponseDTO.newResponse(licenseUsageInterface.getLicenseUsage(
            accountIdentifier, moduleType, timestamp, CDUsageRequestParams.builder().cdLicenseType(type).build()));
      }

      return ResponseDTO.newResponse(licenseUsageInterface.getLicenseUsage(
          accountIdentifier, moduleType, timestamp, UsageRequestParams.builder().build()));
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Module is invalid", e);
    }
  }

  @GET
  @Path("CD/servicesLicense")
  @ApiOperation(
      value = "Gets License Usage By Timestamp for Services in CD Module", nickname = "getCDLicenseUsageForServices")
  @Operation(operationId = "getCDLicenseUsageForServices",
      summary = "Gets License Usage By Module, Timestamp, and Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a license usage object")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<ServiceUsageDTO>
  getCDLicenseUsageForServices(@Parameter(description = "Account id to get the license usage.") @QueryParam(
                                   "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @QueryParam("timestamp") long timestamp) {
    return ResponseDTO.newResponse((ServiceUsageDTO) licenseUsageInterface.getLicenseUsage(accountIdentifier,
        ModuleType.CD, timestamp, CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICES).build()));
  }

  @GET
  @Path("CD/serviceInstancesLicense")
  @ApiOperation(value = "Gets License Usage By Timestamp for Service Instances in CD Module",
      nickname = "getCDLicenseUsageForServiceInstances")
  @Operation(operationId = "getCDLicenseUsageForServiceInstances",
      summary = "Gets License Usage By Module, Timestamp, and Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a license usage object")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<ServiceInstanceUsageDTO>
  getCDLicenseUsageForServiceInstances(@Parameter(description = "Account id to get the license usage.") @QueryParam(
                                           "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @QueryParam("timestamp") long timestamp) {
    return ResponseDTO.newResponse(
        (ServiceInstanceUsageDTO) licenseUsageInterface.getLicenseUsage(accountIdentifier, ModuleType.CD, timestamp,
            CDUsageRequestParams.builder().cdLicenseType(CDLicenseType.SERVICE_INSTANCES).build()));
  }
}
