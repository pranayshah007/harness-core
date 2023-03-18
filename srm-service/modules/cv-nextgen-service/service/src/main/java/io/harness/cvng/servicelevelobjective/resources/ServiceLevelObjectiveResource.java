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
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.CVConstants;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
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
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api("slo")
@Path("slo")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@Tag(name = "SLOs", description = "This contains APIs related to CRUD operations of SLOs")
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
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelObjectiveResource {
  @Inject ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject AccessControlClient accessControlClient;

  public static final String SLO = "SLO";
  public static final String EDIT_PERMISSION = "chi_slo_edit";
  public static final String VIEW_PERMISSION = "chi_slo_view";
  public static final String DELETE_PERMISSION = "chi_slo_delete";

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/logs")
  @ApiOperation(value = "get service level objective logs", nickname = "getServiceLevelObjectiveLogs")
  @Operation(operationId = "getServiceLevelObjectiveLogs", summary = "Get SLO logs",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the SLO's logs")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<PageResponse<CVNGLogDTO>>
  getServiceLevelObjectiveLogs(@NotNull @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String identifier,
      @BeanParam SLILogsFilter sliLogsFilter, @BeanParam PageParams pageParams) {
    return new RestResponse<>(
        serviceLevelObjectiveV2Service.getCVNGLogs(projectParams, identifier, sliLogsFilter, pageParams));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("{identifier}/resetErrorBudget")
  @ApiOperation(value = "reset Error budget history", nickname = "resetErrorBudget")
  @Operation(operationId = "resetErrorBudget", summary = "Reset Error budget history",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Reset Error budget history")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<SLOErrorBudgetResetDTO>
  resetErrorBudget(@NotNull @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String sloIdentifier,
      @Parameter(description = "Details needed to reset error budget") @NotNull @Valid
      @Body SLOErrorBudgetResetDTO sloErrorBudgetResetDTO) {
    sloErrorBudgetResetDTO.setServiceLevelObjectiveIdentifier(sloIdentifier);
    return new RestResponse<>(serviceLevelObjectiveV2Service.resetErrorBudget(projectParams, sloErrorBudgetResetDTO));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/errorBudgetResetHistory")
  @ApiOperation(value = "get error budget reset History", nickname = "getErrorBudgetResetHistory")
  @Operation(operationId = "getErrorBudgetResetHistory", summary = "Get Error budget reset history",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the error budget reset history")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<List<SLOErrorBudgetResetDTO>>
  getErrorBudgetResetHistory(@NotNull @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull @PathParam(
          "identifier") @ResourceIdentifier String sloIdentifier) {
    return new RestResponse<>(serviceLevelObjectiveV2Service.getErrorBudgetResetHistory(projectParams, sloIdentifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/notification-rules")
  @ApiOperation(value = "get notification rules for SLO", nickname = "getNotificationRulesForSLO")
  @Operation(operationId = "getNotificationRulesForSLO", summary = "Get notification rules for SLO",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Gets the notification rules for SLO")
      })
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<NotificationRuleResponse>>
  getNotificationRulesForSLO(@NotNull @BeanParam ProjectParams projectParams,
      @Parameter(description = CVConstants.SLO_PARAM_MESSAGE) @ApiParam(required = true) @NotNull
      @PathParam("identifier") @ResourceIdentifier String sloIdentifier, @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(
        serviceLevelObjectiveV2Service.getNotificationRules(projectParams, sloIdentifier, pageParams));
  }
}
