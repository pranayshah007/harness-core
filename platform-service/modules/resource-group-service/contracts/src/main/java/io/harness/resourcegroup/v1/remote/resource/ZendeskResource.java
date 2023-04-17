/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.v1.remote.resource;

import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;

import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.resourcegroup.v1.remote.dto.TicketType;
;
import io.harness.resourcegroup.v1.remote.dto.ZendeskDescription;
import io.harness.resourcegroup.v1.remote.dto.ZendeskHelper;
import io.harness.resourcegroup.v1.remote.dto.ZendeskPriority;
import io.harness.resourcegroup.v1.remote.dto.ZendeskResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Api("/zendesk")
@Path("zendesk")
@Tag(name = "Zendesk", description = "This contains APIs specific to the creation of zendesk ticket")
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = BAD_REQUEST_CODE, description = BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = INTERNAL_SERVER_ERROR_CODE, description = INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDP)
public class ZendeskResource {
  @Inject ZendeskHelper zendeskHelper;

  @POST
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "create zendesk ticket for given user", nickname = "createZendeskTicket")
  @Operation(operationId = "createZendeskTicket", summary = "create zendesk ticket for given user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "create zendesk ticket for given user")
      })
  public ResponseDTO<ZendeskResponseDTO>
  createZendeskTicket(
      @Parameter(description = "emailId for user creating ticket") @NotNull @QueryParam("emailId") String emailId,
      @Parameter(description = "type of the ticket ") @NotNull @QueryParam("ticketType") TicketType ticketType,
      @Parameter(description = "priority of the ticket") @NotNull @QueryParam("priority") ZendeskPriority priority,
      @Parameter(description = "subject of the ticket") @NotNull @QueryParam("subject") String subject,
      @FormDataParam("message") String message, @FormDataParam("url") String url,
      @FormDataParam("userBrowser") String userBrowser, @FormDataParam("userOS") String userOS,
      @FormDataParam("website") String website, @FormDataParam("userName") String userName,
      @FormDataParam("accountId") String accountId, @FormDataParam("module") String module,
      @FormDataParam("browserResolution") String browserResolution,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    ZendeskDescription zendeskDescription = ZendeskDescription.builder()
                                                .message(message)
                                                .accountId(accountId)
                                                .browserResolution(browserResolution)
                                                .userBrowser(userBrowser)
                                                .module(module)
                                                .url(url)
                                                .userOS(userOS)
                                                .website(website)
                                                .userOS(userOS)
                                                .build();
    return ResponseDTO.newResponse(zendeskHelper.create(
        emailId, ticketType, priority, subject, zendeskDescription, uploadedInputStream, fileDetail));
  }

  @GET
  @Path("/token")
  @Produces({"application/json", "application/yaml"})
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "get short live token for zendesk", nickname = "getZendeskToken")
  @Operation(operationId = "getZendeskToken", summary = "get short live token for zendesk",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "get short live token for zendesk")
      })
  public ResponseDTO<ZendeskResponseDTO>
  getShortLiveToken() {
    return ResponseDTO.newResponse(zendeskHelper.getToken());
  }
}
