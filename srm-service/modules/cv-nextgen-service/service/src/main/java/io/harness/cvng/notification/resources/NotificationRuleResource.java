/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.services.api.NotificationRuleService;
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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Api("notification-rule")
@Path("notification-rule")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CV)
@Tag(name = "Srm Notification", description = "This contains APIs related to CRUD operations of srm notifications")
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
public class NotificationRuleResource {
  @Inject NotificationRuleService notificationRuleService;

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "saves notificationRule data", nickname = "saveNotificationRuleData")
  public RestResponse<NotificationRuleResponse> saveNotificationRuleData(
      @ApiParam(required = true) @NotNull @QueryParam("accountId") String accountId,
      @Valid @Body NotificationRuleDTO notificationRuleDTO) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(notificationRuleDTO.getOrgIdentifier())
                                      .projectIdentifier(notificationRuleDTO.getProjectIdentifier())
                                      .build();
    return new RestResponse<>(notificationRuleService.create(projectParams, notificationRuleDTO));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "update notificationRule data", nickname = "updateNotificationRuleData")
  public RestResponse<NotificationRuleResponse> updateNotificationRuleData(@BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") String identifier,
      @Valid @Body NotificationRuleDTO notificationRuleDTO) {
    return new RestResponse<>(notificationRuleService.update(projectParams, identifier, notificationRuleDTO));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "delete notificationRule data", nickname = "deleteNotificationRuleData")
  public RestResponse<Boolean> deleteNotificationRuleData(@BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") String identifier) {
    return new RestResponse<>(notificationRuleService.delete(projectParams, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @Path("{identifier}")
  @ApiOperation(value = "get notificationRule data", nickname = "getNotificationRuleData")
  public RestResponse<NotificationRuleResponse> getNotificationRuleData(@BeanParam ProjectParams projectParams,
      @ApiParam(required = true) @NotNull @PathParam("identifier") String identifier) {
    return new RestResponse<>(notificationRuleService.get(projectParams, identifier));
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get notificationRule data", nickname = "getNotificationRuleData")
  public ResponseDTO<PageResponse<NotificationRuleResponse>> getNotificationRuleData(
      @BeanParam ProjectParams projectParams,
      @QueryParam("notificationRuleIdentifiers") List<String> notificationRuleIdentifiers,
      @QueryParam("pageNumber") @NotNull Integer pageNumber, @QueryParam("pageSize") @NotNull Integer pageSize) {
    return ResponseDTO.newResponse(
        notificationRuleService.get(projectParams, notificationRuleIdentifiers, pageNumber, pageSize));
  }
}
