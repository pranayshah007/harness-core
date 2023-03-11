/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.LICENSE_TYPE_KEY;
import static io.harness.NGCommonEntityConstants.LICENSE_TYPE_PARAM_MESSAGE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.LICENSE_DATE_USAGE_PARAMS_MESSAGE;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.dto.LicenseDateUsageDTO;
import io.harness.cdng.usage.dto.LicenseDateUsageParams;
import io.harness.cdng.usage.impl.CDLicenseUsageImpl;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.utils.PageUtils;

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
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Api("license-usage-cd")
@Path("license-usage-cd")
@Produces({"application/json"})
@Consumes({"application/json"})
@Tag(name = "Usage", description = "This contains APIs specific to CD license usage")
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
public class CDLicenseUsageResource {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private CDLicenseUsageImpl cdLicenseUsageService;

  @GET
  @Path("services")
  @ApiOperation(value = "Get all services", nickname = "getAllServices")
  @Operation(operationId = "getAllServices",
      summary = "Get all services across organizations and projects within account",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns the list of all Services") },
      hidden = true)
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<PageResponse<ServiceResponse>>
  getAllServices(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                     ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = NGResourceFilterConstants.SEARCH_TERM) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") @Max(1000) int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SORT) List<String> sort) {
    Criteria criteria = ServiceFilterHelper.createCriteriaForListingAllServices(
        accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, false);

    Pageable pageRequest = getPageRequest(page, size, sort);
    Page<ServiceEntity> serviceEntities = serviceEntityService.list(criteria, pageRequest);
    return ResponseDTO.newResponse(getNGPageResponse(serviceEntities.map(ServiceElementMapper::toResponseWrapper)));
  }

  @POST
  @Path("date")
  @ApiOperation(value = "Get license date usage in CD Module", nickname = "getLicenseDateUsage")
  @Hidden
  @Operation(operationId = "getLicenseDateUsage", summary = "Get license usage by dates in requested date range",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns license usage per dates") })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<LicenseDateUsageDTO>
  getLicenseDateUsage(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = LICENSE_TYPE_PARAM_MESSAGE) @NotNull @QueryParam(
          LICENSE_TYPE_KEY) CDLicenseType licenseType,
      @Valid @RequestBody(description = LICENSE_DATE_USAGE_PARAMS_MESSAGE) LicenseDateUsageParams dateUsageParams) {
    return ResponseDTO.newResponse(
        cdLicenseUsageService.getLicenseDateUsage(accountIdentifier, dateUsageParams, licenseType));
  }

  private Pageable getPageRequest(int page, int size, List<String> sort) {
    return isEmpty(sort) ? PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt))
                         : PageUtils.getPageRequest(page, size, sort);
  }
}
