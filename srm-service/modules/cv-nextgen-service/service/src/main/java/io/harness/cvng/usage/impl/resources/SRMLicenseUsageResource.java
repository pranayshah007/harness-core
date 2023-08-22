/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PAGE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SIZE_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.SORT;
import static io.harness.NGCommonEntityConstants.SORT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.TIMESTAMP;
import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.licensing.usage.utils.PageableUtils.validateSort;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

import io.harness.ModuleType;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.cvng.usage.impl.ActiveServiceMonitoredDTO;
import io.harness.cvng.usage.impl.ActiveServiceMonitoredFilterParams;
import io.harness.cvng.usage.impl.SRMLicenseUsageDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
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
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Api("/usage")
@Path("/usage")
@Produces("application/json")
@NextGenManagerAuth
public class SRMLicenseUsageResource {
  @Inject LicenseUsageInterface licenseUsageInterface;

  private static final String SERVICE_INSTANCE_ID = "serviceInstances";

  private static final List<String> ACTIVE_SERVICES_MONITORED_SORT_QUERY_PROPERTIES = List.of(SERVICE_INSTANCE_ID);

  private static final String ACTIVE_SERVICES_MONITORED_FILTER_PARAM_MESSAGE =
      "Details of the Active Services Monitored Filter";

  @GET
  @Path("/CV")
  @ApiOperation(value = "Gets License Usage for CV", nickname = "getLicenseUsage")
  public ResponseDTO<SRMLicenseUsageDTO> getLicenseUsage(
      @QueryParam("accountIdentifier") @AccountIdentifier @NotNull String accountIdentifier,
      @QueryParam("timestamp") long timestamp) {
    try {
      ModuleType moduleType = ModuleType.fromString("CV");

      return ResponseDTO.newResponse((SRMLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(
          accountIdentifier, moduleType, timestamp, UsageRequestParams.builder().build()));
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Module is invalid", e);
    }
  }

  @GET
  @Path("/SRM")
  @ApiOperation(value = "Gets License Usage for SRM", nickname = "getSRMLicenseUsage")
  public ResponseDTO<SRMLicenseUsageDTO> getSRMLicenseUsage(
      @QueryParam("accountIdentifier") @AccountIdentifier @NotNull String accountIdentifier,
      @QueryParam("timestamp") long timestamp) {
    try {
      ModuleType moduleType = ModuleType.fromString("SRM");

      return ResponseDTO.newResponse((SRMLicenseUsageDTO) licenseUsageInterface.getLicenseUsage(
          accountIdentifier, moduleType, timestamp, UsageRequestParams.builder().build()));
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("Module is invalid", e);
    }
  }

  @POST
  @Path("/SRM/active-services-monitored")
  @ApiOperation(value = "List Active Services Monitored in SRM Module", nickname = "listSRMActiveServicesMonitored")
  @Hidden
  @Operation(operationId = "listSRMActiveServicesMonitored",
      summary = "List Active Services Monitored on Account, Organization and Project level",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a list of active services Monitored")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  @Deprecated
  public ResponseDTO<Page<ActiveServiceMonitoredDTO>>
  listSRMActiveServicesMonitored(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int page,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("30")
      @Max(50) int size, @Parameter(description = SORT_PARAM_MESSAGE) @QueryParam(SORT) List<String> sort,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs,
      @Valid @RequestBody(description = ACTIVE_SERVICES_MONITORED_FILTER_PARAM_MESSAGE)
      ActiveServiceMonitoredFilterParams filterParams) {
    currentTsInMs = fixOptionalCurrentTs(currentTsInMs);
    Pageable pageRequest =
        PageableUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, SERVICE_INSTANCE_ID));
    validateSort(pageRequest.getSort(), ACTIVE_SERVICES_MONITORED_SORT_QUERY_PROPERTIES);
    DefaultPageableUsageRequestParams requestParams =
        DefaultPageableUsageRequestParams.builder().filterParams(filterParams).pageRequest(pageRequest).build();

    return ResponseDTO.newResponse((Page<ActiveServiceMonitoredDTO>) licenseUsageInterface.listLicenseUsage(
        accountIdentifier, ModuleType.SRM, currentTsInMs, requestParams));
  }

  @POST
  @Path("/SRM/active-monitored-services")
  @ApiOperation(value = "List Active Services in SRM Module", nickname = "listSRMActiveMonitoredServices")
  @Hidden
  @Operation(operationId = "listSRMActiveMonitoredServices",
      summary = "Returns a List of active monitored services along with identifier,lastUpdatedBy and other details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a list of active monitored services")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public ResponseDTO<Page<ActiveMonitoredServiceDTO>>
  listSRMActiveMonitoredServices(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PAGE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PAGE) @DefaultValue(
          "0") int page,
      @Parameter(description = SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("20")
      @Max(50) int size, @Parameter(description = SORT_PARAM_MESSAGE) @QueryParam(SORT) List<String> sort,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs,
      @Valid @RequestBody(description = ACTIVE_SERVICES_MONITORED_FILTER_PARAM_MESSAGE)
      ActiveServiceMonitoredFilterParams filterParams) {
    Pageable pageRequest =
        PageableUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, SERVICE_INSTANCE_ID));
    validateSort(pageRequest.getSort(), ACTIVE_SERVICES_MONITORED_SORT_QUERY_PROPERTIES);
    DefaultPageableUsageRequestParams requestParams =
        DefaultPageableUsageRequestParams.builder().filterParams(filterParams).pageRequest(pageRequest).build();

    return ResponseDTO.newResponse((Page<ActiveMonitoredServiceDTO>) licenseUsageInterface.listLicenseUsage(
        accountIdentifier, ModuleType.SRM, currentTsInMs, requestParams));
  }

  @GET
  @Path("/SRM/active-services-monitored/csv/download")
  @ApiOperation(
      value = "Download CSV Active Services Monitored report", nickname = "downloadActiveServiceMonitoredCSVReport")
  @Operation(operationId = "downloadActiveServiceMonitoredCSVReport",
      summary = "Download CSV Active Services Monitored report",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Download CSV Active Services Monitored report")
      })
  @NGAccessControlCheck(resourceType = "LICENSE", permission = "core_license_view")
  public Response
  downloadActiveServiceMonitoredCSVReport(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                              ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @QueryParam(TIMESTAMP) @DefaultValue("0") long currentTsInMs) {
    currentTsInMs = fixOptionalCurrentTs(currentTsInMs);
    File file = licenseUsageInterface.getLicenseUsageCSVReport(accountIdentifier, ModuleType.SRM, currentTsInMs);

    return Response
        .ok(
            (StreamingOutput) output
            -> {
              Files.copy(file.toPath(), output);
              deleteFileIfExists(file.getPath());
            },
            APPLICATION_OCTET_STREAM)
        .header(
            "Content-Disposition", "attachment; filename=" + prepareCSVReportFileName(accountIdentifier, currentTsInMs))
        .build();
  }

  @NotNull
  private String prepareCSVReportFileName(String accountIdentifier, long currentTsInMs) {
    return format("%s-%s.csv", accountIdentifier, currentTsInMs);
  }

  private static long fixOptionalCurrentTs(long currentTsMs) {
    return currentTsMs == 0 ? System.currentTimeMillis() : currentTsMs;
  }
}
