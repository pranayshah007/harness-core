/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.resources;

import static io.harness.cvng.core.beans.params.ProjectParams.fromProjectPathParams;
import static io.harness.cvng.core.beans.params.ProjectParams.fromResourcePathParams;
import static io.harness.cvng.core.resources.MonitoredServiceResource.TOGGLE_PERMISSION;
import static io.harness.cvng.core.services.CVNextGenConstants.DOWNTIME_PROJECT_PATH;
import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_PATH;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.beans.params.ResourcePathParams;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeDashboardFilter;
import io.harness.cvng.downtime.beans.DowntimeHistoryView;
import io.harness.cvng.downtime.beans.DowntimeListView;
import io.harness.cvng.downtime.beans.DowntimeResponse;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(value = DOWNTIME_PROJECT_PATH, tags = "Downtime")
@Path(DOWNTIME_PROJECT_PATH)
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
/*@Tag(name = "Downtime", description = "This contains APIs related to CRUD operations of Downtime")
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
    })*/
@OwnedBy(HarnessTeam.CV)
public class DowntimeProjectLevelResource {
  public static final String DOWNTIME = "DOWNTIME";
  public static final String EDIT_PERMISSION = "chi_downtime_edit";
  public static final String VIEW_PERMISSION = "chi_downtime_view";
  public static final String DELETE_PERMISSION = "chi_downtime_delete";

  @Inject DowntimeService downtimeService;

  @POST
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves downtime", nickname = "saveDowntime")
  /*  @Operation(operationId = "saveDowntime", summary = "Saves Downtime",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the saved Downtime") })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = VIEW_PERMISSION)
  public RestResponse<DowntimeResponse> saveDowntime(@Valid @BeanParam ProjectPathParams projectPathParams,
      @Parameter(description = "Details of the Downtime to be saved") @NotNull @Valid @Body DowntimeDTO downtimeDTO) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return new RestResponse<>(downtimeService.create(projectParams, downtimeDTO));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "get downtime data", nickname = "getDowntime")
  /*  @Operation(operationId = "getDowntime", summary = "Get Downtime data",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the Downtime's data")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = VIEW_PERMISSION)
  public RestResponse<DowntimeResponse> getDowntime(@Valid @BeanParam ResourcePathParams resourcePathParams) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(downtimeService.get(projectParams, resourcePathParams.getIdentifier()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/monitored-services/{identifier}")
  @ApiOperation(value = "get associated Monitored Services", nickname = "getDowntimeAssociatedMonitoredServices")
  /*  @Operation(operationId = "getDowntimeAssociatedMonitoredServices", summary = "Get Downtime Associated Monitored
     Services", responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Get Downtime
     Associated Monitored Services")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = VIEW_PERMISSION)
  public RestResponse<List<MonitoredServiceDetail>> getAssociatedMonitoredServices(
      @Valid @BeanParam ResourcePathParams resourcePathParams) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(
        downtimeService.getAssociatedMonitoredServices(projectParams, resourcePathParams.getIdentifier()));
  }

  @PUT
  @Consumes("application/json")
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "update downtime data", nickname = "updateDowntimeData")
  /*  @Operation(operationId = "updateDowntimeData", summary = "Update Downtime data",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the updated Downtime")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = EDIT_PERMISSION)
  public RestResponse<DowntimeResponse> updateDowntimeData(@Valid @BeanParam ResourcePathParams resourcePathParams,
      @Parameter(description = "Details of the Downtime to be updated") @NotNull @Valid @Body DowntimeDTO downtimeDTO) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(downtimeService.update(projectParams, resourcePathParams.getIdentifier(), downtimeDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path(RESOURCE_IDENTIFIER_PATH)
  @ApiOperation(value = "delete downtime data", nickname = "deleteDowntimeData")
  /*  @Operation(operationId = "deleteDowntimeData", summary = "Delete Downtime data",
        responses =
        { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns true if the Downtime is deleted")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = DELETE_PERMISSION)
  public RestResponse<Boolean> deleteDowntimeData(@Valid @BeanParam ResourcePathParams resourcePathParams) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(downtimeService.delete(projectParams, resourcePathParams.getIdentifier()));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/list")
  @ApiOperation(value = "list downtime data", nickname = "listDowntimes")
  /*  @Operation(operationId = "listDowntimes", summary = "List Downtime data",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Gets the list of downtimes")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<DowntimeListView>> listDowntimes(
      @Valid @BeanParam ProjectPathParams projectPathParams, @BeanParam PageParams pageParams,
      @BeanParam DowntimeDashboardFilter filter) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return ResponseDTO.newResponse(downtimeService.list(projectParams, pageParams, filter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("/history")
  @ApiOperation(value = "Get downtime history data", nickname = "getHistory")
  /*  @Operation(operationId = "getHistory", summary = "Get downtime history data",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Get downtime history data")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<DowntimeHistoryView>> getHistory(@BeanParam ProjectPathParams projectPathParams,
      @BeanParam PageParams pageParams, @BeanParam DowntimeDashboardFilter filter) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    return ResponseDTO.newResponse(downtimeService.history(projectParams, pageParams, filter));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path("/flag/{identifier}")
  @ApiOperation(value = "Enables disables downtime", nickname = "enablesDisablesDowntime")
  /*  @Operation(operationId = "enableDisableDowntime", summary = "Enables or Disables Downtime",
        responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Enables or Disables Downtime")
     })*/
  @NGAccessControlCheck(resourceType = DOWNTIME, permission = TOGGLE_PERMISSION)
  public RestResponse<DowntimeResponse> updateDowntimeEnabled(
      @Valid @BeanParam ResourcePathParams resourcePathParams, @NotNull @QueryParam("enable") Boolean enable) {
    ProjectParams projectParams = fromResourcePathParams(resourcePathParams);
    return new RestResponse<>(
        downtimeService.enableOrDisable(projectParams, resourcePathParams.getIdentifier(), enable));
  }
}
