/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.licensing.api;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.LICENSE_TYPE_KEY;
import static io.harness.NGCommonEntityConstants.LICENSE_TYPE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SORT;
import static io.harness.NGCommonEntityConstants.SORT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.TIMESTAMP;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.springframework.data.domain.Page;

@OwnedBy(HarnessTeam.CI)
@Api("license-usage")
@Path("/license-usage")
@NextGenManagerAuth
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Cache Management", description = "Contains APIs related for cache management.")
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
public interface CILicenseUsageResource {
  String ACTIVE_DEVELOPERS_FILTER_PARAM_MESSAGE = "Details of the Active Developers Filter";
  @POST
  @ApiOperation(value = "Get CI Licence Usage", nickname = "ciLicenseUsage")
  @Operation(operationId = "ciLicenseUsage", summary = "Gets the CI Information by accountIdentifier and filters.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns cached object metadata.")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  ResponseDTO<Page<ActiveDevelopersDTO>>
  ciLicenseUsage(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int page,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("30")
      @Max(50) int size, @Parameter(description = SORT_PARAM_MESSAGE) @QueryParam(SORT) List<String> sort,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs,
      @Valid @RequestBody(description = ACTIVE_DEVELOPERS_FILTER_PARAM_MESSAGE) CIDevelopersFilterParams filterParams);

  @GET
  @Path("/csv/download")
  @ApiOperation(value = "Download CSV Active Developers report", nickname = "downloadActiveDevelopersCSVReport")
  @Operation(operationId = "downloadActiveDevelopersCSVReport", summary = "Download CSV Active Developers report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Download CSV Active Developers report")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  Response
  downloadActiveDevelopersCSVReport(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                        ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs);

  @GET
  @Path("/developers")
  @ApiOperation(value = "List Active Developers", nickname = "listActiveDevelopers")
  @Operation(operationId = "listActiveDevelopers", summary = "List Active Developers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List Active Developers")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  ResponseDTO<Set<String>>
  listActiveDevelopers(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                           ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs);

  @POST
  @Path("history")
  @ApiOperation(value = "Get license date usage in CI Module", nickname = "getLicenseHistoryUsage")
  @Hidden
  @Operation(operationId = "getLicenseHistoryUsage", summary = "Get license usage of 30 days",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns license usage per day") })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  ResponseDTO<CILicenseHistoryDTO>
  getLicenseHistoryUsage(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                             ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = LICENSE_TYPE_PARAM_MESSAGE) @NotNull @QueryParam(
          LICENSE_TYPE_KEY) CILicenseType licenseType,
      @Valid @RequestBody(description = ACTIVE_DEVELOPERS_FILTER_PARAM_MESSAGE) CIDevelopersFilterParams filterParams);
}
