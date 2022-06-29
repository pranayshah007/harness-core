/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.resources;

import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.utils.NGAccessControlClientCheck;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("slo")
@Path("slo")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelObjectiveResource {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject AccessControlClient accessControlClient;

  public static final String SLO = "SLO";
  public static final String EDIT_PERMISSION = "chi_slo_edit";
  public static final String VIEW_PERMISSION = "chi_slo_view";
  public static final String DELETE_PERMISSION = "chi_slo_delete";

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves slo data", nickname = "saveSLOData")
  @NGAccessControlClientCheck
  public RestResponse<ServiceLevelObjectiveResponse> saveSLOData(
      @ApiParam(required = true) @NotNull @QueryParam("accountId") String accountId,
      @NotNull @Valid @Body ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, serviceLevelObjectiveDTO.getOrgIdentifier(),
                                                  serviceLevelObjectiveDTO.getProjectIdentifier()),
        Resource.of(SLO, null), EDIT_PERMISSION);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(serviceLevelObjectiveDTO.getOrgIdentifier())
                                      .projectIdentifier(serviceLevelObjectiveDTO.getProjectIdentifier())
                                      .build();
    // TODO: change this api signature
    return new RestResponse<>(serviceLevelObjectiveService.create(projectParams, serviceLevelObjectiveDTO));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "update slo data", nickname = "updateSLOData")
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<ServiceLevelObjectiveResponse> updateSLOData(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @NotNull @Valid @Body ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    return new RestResponse<>(serviceLevelObjectiveService.update(projectParams, identifier, serviceLevelObjectiveDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "delete slo data", nickname = "deleteSLOData")
  @NGAccessControlCheck(resourceType = SLO, permission = DELETE_PERMISSION)
  public RestResponse<Boolean> deleteSLOData(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(serviceLevelObjectiveService.delete(projectParams, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all service level objectives ", nickname = "getServiceLevelObjectives")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<ServiceLevelObjectiveResponse>> getServiceLevelObjectives(
      @NotNull @BeanParam ProjectParams projectParams, @QueryParam("offset") @NotNull Integer offset,
      @QueryParam("pageSize") @NotNull Integer pageSize,
      @BeanParam ServiceLevelObjectiveFilter serviceLevelObjectiveFilter) {
    return ResponseDTO.newResponse(
        serviceLevelObjectiveService.get(projectParams, offset, pageSize, serviceLevelObjectiveFilter));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "get service level objective data", nickname = "getServiceLevelObjective")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<ServiceLevelObjectiveResponse> getServiceLevelObjective(
      @NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier) {
    return new RestResponse<>(serviceLevelObjectiveService.get(projectParams, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/logs")
  @ApiOperation(value = "get service level objective logs", nickname = "getServiceLevelObjectiveLogs")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<PageResponse<CVNGLogDTO>> getServiceLevelObjectiveLogs(
      @NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @BeanParam SLILogsFilter sliLogsFilter, @BeanParam PageParams pageParams) {
    return new RestResponse<>(
        serviceLevelObjectiveService.getCVNGLogs(projectParams, identifier, sliLogsFilter, pageParams));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Path("{identifier}/resetErrorBudget")
  @ApiOperation(value = "reset Error budget history", nickname = "resetErrorBudget")
  @NGAccessControlCheck(resourceType = SLO, permission = EDIT_PERMISSION)
  public RestResponse<SLOErrorBudgetResetDTO> resetErrorBudget(@NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String sloIdentifier,
      @NotNull @Valid @Body SLOErrorBudgetResetDTO sloErrorBudgetResetDTO) {
    sloErrorBudgetResetDTO.setServiceLevelObjectiveIdentifier(sloIdentifier);
    return new RestResponse<>(serviceLevelObjectiveService.resetErrorBudget(projectParams, sloErrorBudgetResetDTO));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/errorBudgetResetHistory")
  @ApiOperation(value = "get error budget reset History", nickname = "getErrorBudgetResetHistory")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public RestResponse<List<SLOErrorBudgetResetDTO>> getErrorBudgetResetHistory(
      @NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String sloIdentifier) {
    return new RestResponse<>(serviceLevelObjectiveService.getErrorBudgetResetHistory(projectParams, sloIdentifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}/notification-rules")
  @ApiOperation(value = "get notification rules for SLO", nickname = "getNotificationRulesForSLO")
  @NGAccessControlCheck(resourceType = SLO, permission = VIEW_PERMISSION)
  public ResponseDTO<PageResponse<NotificationRuleResponse>> getNotificationRulesForSLO(
      @NotNull @BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String sloIdentifier,
      @BeanParam PageParams pageParams) {
    return ResponseDTO.newResponse(
        serviceLevelObjectiveService.getNotificationRules(projectParams, sloIdentifier, pageParams));
  }
}
