/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SORT;
import static io.harness.NGCommonEntityConstants.SORT_PARAM_MESSAGE;
import static io.harness.licensing.usage.beans.idp.IDPLicenseUsageConstants.ACTIVE_DEVELOPERS_FILTER_PARAM_MESSAGE;
import static io.harness.licensing.usage.utils.PageableUtils.validateSort;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPActiveDevelopersDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveDevelopersFilterParams;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Api("/usage")
@Path("/usage")
@Produces("application/json")
@NextGenManagerAuth
@Tag(name = "IDPLicenseUsage", description = "This contains APIs related to IDP License Usage.")
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageResource {
  @Inject LicenseUsageInterface licenseUsageInterface;
  @Inject IDPModuleLicenseUsage idpModuleLicenseUsage;

  @POST
  @Path("/IDP/active-developers")
  @ApiOperation(value = "List Active Developers in IDP Module", nickname = "listIDPActiveDevelopers")
  @Hidden
  @Operation(operationId = "listIDPActiveDevelopers",
      summary = "List Active Developers with user name, user email and last accessed at on Account level",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a list of active developers") })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<Page<IDPActiveDevelopersDTO>>
  listIDPActiveDevelopers(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int page,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("30")
      @Max(50) int size, @Parameter(description = SORT_PARAM_MESSAGE) @QueryParam(SORT) List<String> sort,
      @Valid @RequestBody(
          description = ACTIVE_DEVELOPERS_FILTER_PARAM_MESSAGE) ActiveDevelopersFilterParams filterParams) {
    Pageable pageRequest =
        PageableUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, "lastAccessedAt"));
    validateSort(pageRequest.getSort(), Collections.singletonList("lastAccessedAt"));
    DefaultPageableUsageRequestParams usageRequest =
        DefaultPageableUsageRequestParams.builder().filterParams(filterParams).pageRequest(pageRequest).build();
    return ResponseDTO.newResponse((Page<IDPActiveDevelopersDTO>) licenseUsageInterface.listLicenseUsage(
        accountIdentifier, ModuleType.IDP, System.currentTimeMillis(), usageRequest));
  }

  @POST
  @Path("/IDP/active-developers/history")
  @ApiOperation(value = "List Active Developers history for last 30 days in IDP Module",
      nickname = "listIDPActiveDevelopersHistory")
  @Hidden
  @Operation(operationId = "listIDPActiveDevelopersHistory",
      summary = "List Active Developers history for last 30 days in IDP Module",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "List Active Developers history for last 30 days in IDP Module")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<List<ActiveDevelopersTrendCountDTO>>
  listIDPActiveDevelopersHistory(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Valid @RequestBody(
          description = "IDP License Date Usage params") IDPLicenseDateUsageParams idpLicenseDateUsageParams) {
    return ResponseDTO.newResponse(idpModuleLicenseUsage.getHistoryTrend(accountIdentifier, idpLicenseDateUsageParams));
  }
}
