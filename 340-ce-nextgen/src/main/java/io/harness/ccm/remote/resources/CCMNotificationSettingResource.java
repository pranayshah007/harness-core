/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting;
import io.harness.ccm.commons.entities.notifications.CCMPerspectiveNotificationChannelsDTO;
import io.harness.ccm.service.intf.CCMNotificationService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("notificationSetting")
@Path("notificationSetting")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Hidden
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Notification Settings",
    description = "Set Notification channels to get Cloud Cost Anomaly alerts")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class CCMNotificationSettingResource {
  @Inject CCMNotificationService notificationService;

  @POST
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create Notification Setting", nickname = "createNotificationSetting")
  @Operation(operationId = "createNotificationSetting",
      description = "Create a Notification Setting for a perspective to receive Cloud Cost Anomaly alerts.",
      summary = "Create a Notification Setting",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Notification Setting object created",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CCMNotificationSetting>
  save(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") @NotNull String perspectiveId,
      @RequestBody(required = true,
          description = "Notification Setting definition") CCMNotificationSetting notificationSetting) {
    notificationSetting.setAccountId(accountId);
    notificationSetting.setPerspectiveId(perspectiveId);
    return ResponseDTO.newResponse(notificationService.upsert(notificationSetting));
  }

  @GET
  @Path("perspective/{perspectiveId}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get Notification Settings for a Perspective", nickname = "getNotificationSettings")
  @Operation(operationId = "getNotificationSettings", description = "Get Notification Settings for the Perspective ID.",
      summary = "Get Notification Settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the Notification Setting object for given Perspective",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CCMNotificationSetting>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @PathParam(
          "perspectiveId") @NotNull String perspectiveId) {
    return ResponseDTO.newResponse(notificationService.get(perspectiveId, accountId));
  }

  @GET
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Notification Settings for an account", nickname = "listNotificationSettings")
  @Operation(operationId = "listNotificationSettings",
      description = "List Notification Settings for the given Account ID.", summary = "List Notification Settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of Notification Setting object for given account",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<CCMPerspectiveNotificationChannelsDTO>>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    return ResponseDTO.newResponse(notificationService.list(accountId));
  }

  @PUT
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update Notification Setting", nickname = "updateNotificationSetting")
  @Operation(operationId = "updateNotificationSetting",
      description = "Update an existing Notification Setting for the given Perspective ID.",
      summary = "Update an existing Notification Setting",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Notification Setting object",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CCMNotificationSetting>
  update(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") @NotNull String perspectiveId,
      @RequestBody(required = true,
          description = "Notification Setting definition") CCMNotificationSetting notificationSetting) {
    notificationSetting.setAccountId(accountId);
    notificationSetting.setPerspectiveId(perspectiveId);
    return ResponseDTO.newResponse(notificationService.upsert(notificationSetting));
  }

  @DELETE
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Delete Notification Settings for a Perspective", nickname = "deleteNotificationSettings")
  @Operation(operationId = "deleteNotificationSettings",
      description = "Delete Notification Settings for the given Perspective ID.",
      summary = "Delete Notification Settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns boolean indicating whether deletion of Notification Settings for a perspective was successful",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") @NotNull String perspectiveId) {
    return ResponseDTO.newResponse(notificationService.delete(perspectiveId, accountId));
  }
}
