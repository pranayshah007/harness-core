/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.CVConstants;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.SLOConsumptionBreakdown;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.UnavailabilityInstancesResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventDetailsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventsResponse;
import io.harness.cvng.servicelevelobjective.beans.secondaryEvents.SecondaryEventsType;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("slo-dashboard")
@Path("slo-dashboard")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@Tag(name = "SLOs dashboard", description = "This contains APIs related to SLOs dashboard")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
public class SLODashboardResource {
  @Inject private SLODashboardService sloDashboardService;

  public static final String SLO = "SLO";
  public static final String VIEW_PERMISSION = "chi_slo_view";

  @GET
  @Path("widgets/list")
  @ExceptionMetered
  @ApiOperation(value = "get slo list view", nickname = "getSLOHealthListView")
  @Operation(operationId = "getSLOHealthListView", summary = "Get SLO list view",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLOs for list view")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLOHealthListView>>
  getSloHealthListView(@Valid @BeanParam ProjectParams projectParams, @BeanParam SLODashboardApiFilter filter,
      @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(sloDashboardService.getSloHealthListView(projectParams, filter, pageParams));
  }

  @POST
  @Path("widgets/list")
  @ExceptionMetered
  @ApiOperation(value = "get slo list view", nickname = "getSLOHealthListViewV2")
  @Operation(operationId = "getSLOHealthListViewV2", summary = "Get SLO list view",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLOs for list view")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLOHealthListView>>
  getSloHealthListViewV2(@Valid @BeanParam ProjectParams projectParams, @BeanParam PageParams pageParams,
      @Valid @Body SLODashboardApiFilter filter) {
    return ResponseDTO.newResponse(sloDashboardService.getSloHealthListView(projectParams, filter, pageParams));
  }

  @GET
  @Path("widget/{identifier}")
  @ExceptionMetered
  @ApiOperation(value = "get SLO Dashboard Detail", nickname = "getSLODetails")
  @Operation(operationId = "getSLODetails", summary = "Get SLO dashboard details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLO's details")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<SLODashboardDetail>
  getSloDashboardWidget(@Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(
                            required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @Valid @QueryParam("startTime") Long startTime, @Valid @QueryParam("endTime") Long endTime,
      @Valid @BeanParam ProjectParams projectParams) {
    return ResponseDTO.newResponse(
        sloDashboardService.getSloDashboardDetail(projectParams, identifier, startTime, endTime));
  }

  @GET
  @Path("widget/{identifier}/consumption")
  @ExceptionMetered
  @ApiOperation(value = "get SLO consumption breakdown", nickname = "getSloConsumptionBreakdownView")
  @Operation(operationId = "getSloConsumptionBreakdownView", summary = "Get SLO consumption breakdown",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLO's details")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<SLOConsumptionBreakdown>>
  getSloConsumptionBreakdownView(@Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true)
                                 @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @QueryParam("startTime") Long startTime, @NotNull @Valid @QueryParam("endTime") Long endTime,
      @Valid @BeanParam ProjectParams projectParams) {
    return ResponseDTO.newResponse(
        sloDashboardService.getSLOConsumptionBreakdownView(projectParams, identifier, startTime, endTime));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("risk-count")
  @ApiOperation(
      value = "get all service level objectives count by risk", nickname = "getServiceLevelObjectivesRiskCount")
  @Operation(operationId = "getServiceLevelObjectivesRiskCount", summary = "Get all SLOs count by risk",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get the risk count for all SLOs")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<SLORiskCountResponse>
  getServiceLevelObjectivesRiskCount(
      @Valid @BeanParam ProjectParams projectParams, @BeanParam SLODashboardApiFilter serviceLevelObjectiveFilter) {
    return ResponseDTO.newResponse(sloDashboardService.getRiskCount(projectParams, serviceLevelObjectiveFilter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("monitored-services")
  @ApiOperation(
      value = "get all monitored services associated with the slos", nickname = "getSLOAssociatedMonitoredServices")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<MSDropdownResponse>>
  getSLOAssociatedMonitoredServices(@BeanParam ProjectParams projectParams, @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(sloDashboardService.getSLOAssociatedMonitoredServices(projectParams, pageParams));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/unavailable-instances/{identifier}")
  @ApiOperation(value = "Get Unavailability Instances for SLO", nickname = "getUnavailabilityInstances")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  /*  @Operation(operationId = "get Unavailability Instances", summary = "Get Unavailability Instances for SLO",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Get Unavailability Instances
     for SLO")
     })*/
  public ResponseDTO<List<UnavailabilityInstancesResponse>> getUnavailabilityInstances(
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @QueryParam("startTime") Long startTime, @NotNull @Valid @QueryParam("endTime") Long endTime,
      @Valid @BeanParam ProjectParams projectParams) {
    return ResponseDTO.newResponse(
        sloDashboardService.getUnavailabilityInstances(projectParams, startTime, endTime, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/secondary-events/{identifier}")
  @ApiOperation(value = "Get Secondary events data points for SLO", nickname = "getSecondaryEvents")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<List<SecondaryEventsResponse>> getSecondaryEvents(
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @QueryParam("startTime") Long startTime, @NotNull @Valid @QueryParam("endTime") Long endTime,
      @Valid @BeanParam ProjectParams projectParams) {
    return ResponseDTO.newResponse(
        sloDashboardService.getSecondaryEvents(projectParams, startTime, endTime, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/secondary-events-details")
  @ApiOperation(value = "Get Secondary events details for SLO", nickname = "getSecondaryEventDetails")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<SecondaryEventDetailsResponse> getSecondaryEventDetails(
      @NotNull @Valid @QueryParam("secondaryEventType") SecondaryEventsType type,
      @NotNull @Size(min = 1) @Valid @QueryParam("identifiers") List<String> uuids) {
    return ResponseDTO.newResponse(sloDashboardService.getSecondaryEventDetails(type, uuids));
  }
}
