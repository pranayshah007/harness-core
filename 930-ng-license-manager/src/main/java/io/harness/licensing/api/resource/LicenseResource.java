/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.api.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.licensing.accesscontrol.LicenseAccessControlPermissions.EDIT_LICENSE_PERMISSION;
import static io.harness.licensing.accesscontrol.LicenseAccessControlPermissions.VIEW_LICENSE_PERMISSION;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.WingsException;
import io.harness.licensing.Edition;
import io.harness.licensing.NGLicensingEntityConstants;
import io.harness.licensing.accesscontrol.ResourceTypes;
import io.harness.licensing.beans.EditionActionDTO;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.StartTrialDTO;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("licenses")
@Path("licenses")
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Licenses", description = "This contains APIs related to licenses as defined in Harness")
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
@NextGenManagerAuth
@Hidden
public class LicenseResource {
  private static final String MODULE_TYPE_KEY = "moduleType";
  private final LicenseService licenseService;

  @Inject
  public LicenseResource(LicenseService licenseService) {
    this.licenseService = licenseService;
  }

  @GET
  @Path("/modules/{accountIdentifier}")
  @ApiOperation(
      value = "Gets Module Licenses By Account And ModuleType", nickname = "getModuleLicensesByAccountAndModuleType")
  @Operation(operationId = "getModuleLicensesByAccountAndModuleType",
      summary = "Gets Module Licenses By Account And ModuleType",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all of a module's licenses")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<List<ModuleLicenseDTO>>
  getModuleLicenses(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @PathParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "A Harness Platform module.") @NotNull @QueryParam(
          MODULE_TYPE_KEY) ModuleType moduleType) {
    validateModuleType(moduleType);
    return ResponseDTO.newResponse(licenseService.getModuleLicenses(accountIdentifier, moduleType));
  }

  @GET
  @Path("{accountIdentifier}/summary")
  @ApiOperation(
      value = "Gets Module Licenses With Summary By Account And ModuleType", nickname = "getLicensesAndSummary")
  @Operation(operationId = "getLicensesAndSummary",
      summary = "Gets Module Licenses With Summary By Account And ModuleType",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a module's license summary")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<LicensesWithSummaryDTO>
  getLicensesWithSummary(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @PathParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "A Harness Platform module.") @NotNull @QueryParam(
          MODULE_TYPE_KEY) ModuleType moduleType) {
    validateModuleType(moduleType);
    return ResponseDTO.newResponse(licenseService.getLicenseSummary(accountIdentifier, moduleType));
  }

  @GET
  @Path("account")
  @ApiOperation(value = "Gets All Module License Information in Account", nickname = "getAccountLicenses")
  @Operation(operationId = "getAccountLicenses", summary = "Gets All Module License Information in Account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all licenses for an account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<AccountLicenseDTO>
  getAccountLicensesDTO(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    AccountLicenseDTO accountLicenses = licenseService.getAccountLicense(accountIdentifier);
    return ResponseDTO.newResponse(accountLicenses);
  }

  @GET
  @Path("account-license")
  @ApiOperation(value = "Gets All Module Licenses for an Account", nickname = "getAllAccountModuleLicenses")
  @Operation(operationId = "getAllAccountModuleLicenses", summary = "Gets All Module License Information in Account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all module licenses for an account")
      })
  @InternalApi
  public ResponseDTO<List<ModuleLicenseDTO>>
  getAllAccountModuleLicenses(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    List<ModuleLicenseDTO> moduleLicenses = licenseService.getAllModuleLicences(accountIdentifier);
    return ResponseDTO.newResponse(moduleLicenses);
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Gets Module License", nickname = "getModuleLicenseById")
  @Operation(operationId = "getModuleLicenseById", summary = "Gets Module License",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a module's license")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<ModuleLicenseDTO>
  get(@Parameter(required = true, description = "The module license identifier") @PathParam(
          "identifier") String identifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          required = true, description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountIdentifier) {
    ModuleLicenseDTO moduleLicense = licenseService.getModuleLicenseById(identifier);
    return ResponseDTO.newResponse(moduleLicense);
  }

  @POST
  @Path("free")
  @ApiOperation(value = "Starts Free License For A Module", nickname = "startFreeLicense")
  @Operation(operationId = "startFreeLicense", summary = "Starts Free License For A Module",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Free License of the specified Module.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = EDIT_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<ModuleLicenseDTO>
  startFreeLicense(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "A Harness Platform module.") @NotNull @QueryParam(
          NGCommonEntityConstants.MODULE_TYPE) ModuleType moduleType,
      @Parameter(required = true, description = "Referer URL") @QueryParam(
          NGCommonEntityConstants.REFERER) String referer,
      @Parameter(required = true, description = "Google Analytics Client Id") @QueryParam(
          NGLicensingEntityConstants.GA_CLIENT_ID) String gaClientId) {
    return ResponseDTO.newResponse(licenseService.startFreeLicense(accountIdentifier, moduleType, referer, gaClientId));
  }

  @POST
  @Path("community")
  @ApiOperation(value = "Starts Community License For A Module", nickname = "startCommunityLicense")
  @InternalApi
  public ResponseDTO<ModuleLicenseDTO> startCommunityLicense(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.MODULE_TYPE) ModuleType moduleType) {
    return ResponseDTO.newResponse(licenseService.startCommunityLicense(accountIdentifier, moduleType));
  }

  @POST
  @Path("trial")
  @ApiOperation(value = "Starts Trial License For A Module", nickname = "startTrialLicense")
  @Operation(operationId = "startTrialLicense", summary = "Starts Trial License For A Module",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Trial License of the specified Module.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = EDIT_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<ModuleLicenseDTO>
  startTrialLicense(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description = "This is the details of the Trial License. ModuleType and edition are mandatory") @NotNull
      @Valid @Body StartTrialDTO startTrialRequestDTO,
      @Parameter(description = "Referrer URL") @QueryParam(NGCommonEntityConstants.REFERER) String referer) {
    return ResponseDTO.newResponse(licenseService.startTrialLicense(accountIdentifier, startTrialRequestDTO, referer));
  }

  @POST
  @Path("extend-trial")
  @ApiOperation(value = "Extends Trail License For A Module", nickname = "extendTrialLicense")
  @Operation(operationId = "extendTrialLicense", summary = "Extends Trial License For A Module",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Trial License of the specified Module.")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = EDIT_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<ModuleLicenseDTO>
  extendTrialLicense(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description = "This is the details of the Trial License. ModuleType and edition are mandatory") @NotNull
      @Valid @Body StartTrialDTO startTrialRequestDTO) {
    return ResponseDTO.newResponse(licenseService.extendTrialLicense(accountIdentifier, startTrialRequestDTO));
  }

  @GET
  @Path("actions")
  @ApiOperation(value = "Get Allowed Actions Under Each Edition", nickname = "getEditionActions")
  @Operation(operationId = "getEditionActions", summary = "Get Allowed Actions Under Each Edition",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all actions under each edition")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<Map<Edition, Set<EditionActionDTO>>>
  getEditionActions(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true, description = "A Harness Platform module.") @NotNull @QueryParam(
          NGCommonEntityConstants.MODULE_TYPE) ModuleType moduleType) {
    return ResponseDTO.newResponse(licenseService.getEditionActions(accountIdentifier, moduleType));
  }

  @POST
  @Path("versions")
  @ApiOperation(
      value = "Get Last Modified Time For All Module Types", nickname = "getLastModifiedTimeForAllModuleTypes")
  @Operation(operationId = "getLastModifiedTimeForAllModuleTypes",
      summary = "Get Last Modified Time Under Each ModuleType",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns last modified time under each module type")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.LICENSE, permission = VIEW_LICENSE_PERMISSION)
  @InternalApi
  public ResponseDTO<Map<ModuleType, Long>>
  getLastModifiedTimeForAllModuleTypes(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @NotNull
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(licenseService.getLastUpdatedAtMap(accountIdentifier));
  }

  @GET
  @Path("{accountId}/check-expiry")
  @ApiOperation(
      value = "Deprecated Check All Inactive", nickname = "checkNGLicensesAllInactiveDeprecated", hidden = true)
  @InternalApi
  public ResponseDTO<CheckExpiryResultDTO>
  checkExpiry(@PathParam("accountId") String accountId) {
    return ResponseDTO.newResponse(licenseService.checkExpiry(accountId));
  }

  @GET
  @Path("{accountId}/soft-delete")
  @ApiOperation(value = "Deprecated Soft Delete", nickname = "softDeleteDeprecated", hidden = true)
  @InternalApi
  public ResponseDTO<Boolean> softDelete(@PathParam("accountId") String accountId) {
    licenseService.softDelete(accountId);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @GET
  @Path("{moduleType}/enabled")
  @ApiOperation(
      value = "Get Module Licenses for Specific Module Type which Expiry Time is Greater or Equal to the Provided Time",
      nickname = "getModuleLicenseForSpecificModuleType", hidden = true)
  @InternalApi
  public ResponseDTO<List<ModuleLicenseDTO>>
  getModuleLicensesByModuleType(@Parameter(required = true, description = "A Harness Platform module.") @NotNull
                                @PathParam(NGCommonEntityConstants.MODULE_TYPE) ModuleType moduleType,
      @Parameter(
          required = true, description = "Find All module licenses expiry time greater or equals to provided time")
      @NotNull @QueryParam(NGCommonEntityConstants.EXPIRY_TIME) long expiryTime) {
    return ResponseDTO.newResponse(licenseService.getEnabledModuleLicensesByModuleType(moduleType, expiryTime));
  }

  private void validateModuleType(ModuleType moduleType) {
    if (moduleType.isInternal()) {
      throw new IllegalArgumentException("ModuleType is invalid", WingsException.USER);
    }
  }
}
