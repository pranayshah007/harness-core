/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_NOTIFICATION_SETTINGS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_NOTIFICATION_SETTINGS_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.NOTIFICATION;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.service.api.NotificationManagementService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("notification-channel")
@Path("notification-channel")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Notification-Channel", description = "This contains APIs related to notification channel configured ")
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
@Slf4j
@OwnedBy(PL)
public class NotificationChannelResource {
  private final NotificationManagementService notificationManagementService;

  @POST
  @ApiOperation(value = "Create notification channel", nickname = "postNotifictaionChannel")
  @NGAccessControlCheck(resourceType = NOTIFICATION, permission = MANAGE_NOTIFICATION_SETTINGS_PERMISSION)
  @Operation(operationId = "postNotificationChannel", summary = "Create Notification Channel",
      description = "Create Notification Channel",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Notification Channel")
      })
  public ResponseDTO<NotificationChannel>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = "Organization identifier for the Project.") @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @DefaultValue(DEFAULT_ORG_IDENTIFIER) @OrgIdentifier String orgIdentifier,
      @RequestBody(required = true,
          description = "Notification channel details") @NotNull @Valid NotificationChannel notificationChannel) {
    return ResponseDTO.newResponse(NotificationChannel.builder().build());
  }

  @PUT
  @ApiOperation(value = "Update notification channel", nickname = "putNotificationChannel")
  @NGAccessControlCheck(resourceType = NOTIFICATION, permission = MANAGE_NOTIFICATION_SETTINGS_PERMISSION)
  @Operation(operationId = "putNotificationChannel", description = "Update notification channel",
      summary = "Update notification channel",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully updated notification channel")
      })
  public ResponseDTO<NotificationChannel>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "notification channel with update",
          required = true) @NotNull @Valid NotificationChannel notificationChannel) {
    return ResponseDTO.newResponse(NotificationChannel.builder().build());
  }

  @GET
  @Path("{name}")
  @ApiOperation(value = "Get a Notification Channel", nickname = "getNotificationChannel")
  @NGAccessControlCheck(resourceType = NOTIFICATION, permission = VIEW_NOTIFICATION_SETTINGS_PERMISSION)
  @Operation(operationId = "getNotificationChannel", summary = "Get Notification Channel",
      description = "Get a Notification Channel in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully fetched Notification Channel")
      })
  public ResponseDTO<NotificationChannel>
  notificationChannel(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "notification rule Identifier", required = true) @NotEmpty @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier) {
    return ResponseDTO.newResponse(NotificationChannel.builder().build());
  }

  @DELETE
  @Path("{channel-identifier}")
  @ApiOperation(value = "Delete notification channel", nickname = "deleteNotificationChannel")
  @NGAccessControlCheck(resourceType = NOTIFICATION, permission = MANAGE_NOTIFICATION_SETTINGS_PERMISSION)
  @Operation(operationId = "deleteNotificationChannel", description = "Delete Notification Channel",
      summary = "Delete notification channel in an account/org/project",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns boolean if successfully deleted") })
  public ResponseDTO<Boolean>
  deleteNotificationChannel(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
                                NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the notification channel", required = true) @NotEmpty @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String channelIdentifier) {
    return ResponseDTO.newResponse(true);
  }

  @GET
  @ApiOperation(value = "Get Notification channel List", nickname = "getNotificationChannelList")
  @NGAccessControlCheck(resourceType = NOTIFICATION, permission = MANAGE_NOTIFICATION_SETTINGS_PERMISSION)
  @Operation(operationId = "getNotificationChannelList", description = "List Notification Channel",
      summary = "List the Notification Channel in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the paginated list of the Notification rule.")
      })
  public ResponseDTO<List<NotificationChannel>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Search filter which matches by rule name/identifier")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm, @BeanParam PageRequest pageRequest) {
    List<NotificationChannel> page =
        notificationManagementService.getNotificationChannelList(accountIdentifier, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(page);
  }
}
