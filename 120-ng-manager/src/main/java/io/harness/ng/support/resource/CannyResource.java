/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.support.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
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
import io.harness.ng.support.client.CannyClient;
import io.harness.ng.support.dto.CannyBoardsResponseDTO;
import io.harness.ng.support.dto.CannyPostResponseDTO;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.glassfish.jersey.media.multipart.FormDataParam;

@Api("/canny")
@Path("canny")
@Tag(name = "Canny", description = "This contains APIs required for the creation of Canny tickets")
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
public class CannyResource {
  @Inject CannyClient cannyClient;

  @GET
  @Path("/boards")
  @Produces({"application/json", "application/yaml"})
  @Consumes({"application/json", "application/yaml"})
  @ApiOperation(value = "Get a list of boards available on Canny", nickname = "getCannyBoards")
  @Operation(operationId = "getCannyBoards", summary = "Get a list of boards available on Canny",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get a list of boards available on Canny")
      })
  public ResponseDTO<CannyBoardsResponseDTO>
  getBoardsList(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
      "accountIdentifier") String accountIdentifier) {
    return ResponseDTO.newResponse(cannyClient.getBoards());
  }

  @POST
  @Path("/post")
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "create Canny Post for given user", nickname = "createCannyPost")
  @Operation(operationId = "createCannyPost", summary = "create Canny Post for given user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "create Canny Post for given user")
      })
  public ResponseDTO<CannyPostResponseDTO>
  createCannyPost(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                      "accountIdentifier") String accountIdentifier,
      @Parameter(description = "emailId for user creating post") @NotNull @FormDataParam("email") String email,
      @Parameter(description = "name of user creating post") @NotNull @FormDataParam("name") String name,
      @Parameter(description = "title of the post") @NotNull @FormDataParam("title") String title,
      @Parameter(description = "details of the post") @NotNull @FormDataParam("details") String details,
      @Parameter(description = "boardId where post must be created") @NotNull @FormDataParam(
          "boardId") String boardId) {
    return ResponseDTO.newResponse(cannyClient.createPost(email, name, title, details, boardId));
  }
}
