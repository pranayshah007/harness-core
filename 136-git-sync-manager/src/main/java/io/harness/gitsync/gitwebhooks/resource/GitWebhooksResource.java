/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitwebhooks.resource;

import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.common.dtos.CreateGitWebhookResponse;
import io.harness.gitsync.common.dtos.GetGitWebhookResponse;
import io.harness.gitsync.common.dtos.GitWebhookRequestDTO;
import io.harness.gitsync.common.dtos.ListGitWebhookResponse;
import io.harness.gitsync.common.dtos.UpdateGitWebhookResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@Api("/git-webhooks")
@Path("/git-webhooks")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "SCM", description = "Contains APIs related to Scm")
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
@NextGenManagerAuth
@OwnedBy(PIPELINE)
public class GitWebhooksResource {
  // TODO:Need to add rbac

  @POST
  @ApiOperation(value = "Create a webhook", nickname = "createGitWebhook")
  @Operation(operationId = "postGitWebhook", description = "Creates a Git Webhook", summary = "Create a Git Webhook",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Git Webhook")
      },
      deprecated = true)
  @Hidden
  public ResponseDTO<CreateGitWebhookResponse>
  createGitWebhook(@QueryParam("accountIdentifier") String accountIdentifier,
      @QueryParam("webhookIdentifier") String webhookIdentifier, @QueryParam("connectorRef") String connectorRef,
      @QueryParam("repoName") String repoName,
      @RequestBody(required = true,
          description = "Request Body for Creating a git webhook") GitWebhookRequestDTO gitWebhookRequestDTO) {
    return ResponseDTO.newResponse(CreateGitWebhookResponse.builder().build());
  }

  @GET
  @Path("/{webhookIdentifier}")
  @ApiOperation(value = "Gets a git webhook by identifier", nickname = "getGitWebhook")
  @Operation(operationId = "getGitWebhook", description = "Returns a git webhook by Identifier",
      summary = "Fetch a git webhook",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns git webhook")
      })
  @Hidden
  public ResponseDTO<GetGitWebhookResponse>
  getGitWebhookByIdentifier(@QueryParam("accountIdentifier") String accountIdentifier,
      @PathParam("webhookIdentifier") String webhookIdentifier) {
    return ResponseDTO.newResponse(GetGitWebhookResponse.builder().build());
  }

  @PUT
  @Path("/{webhookIdentifier}")
  @ApiOperation(value = "Update a webhook", nickname = "updateGitWebhook")
  @Operation(operationId = "putGitWebhook", description = "Update a git Webhook", summary = "Update a Git Webhook",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Update Git Webhook")
      },
      deprecated = true)
  @Hidden
  public ResponseDTO<UpdateGitWebhookResponse>
  updateGitWebhook(@QueryParam("accountIdentifier") String accountIdentifier,
      @PathParam("webhookIdentifier") String webhookIdentifier, @QueryParam("connectorRef") String connectorRef,
      @QueryParam("repoName") String repoName, @QueryParam("enable") Boolean isEnabled,
      @RequestBody(required = true,
          description = "Request Body for Updating a git webhook") GitWebhookRequestDTO gitWebhookRequestDTO) {
    return ResponseDTO.newResponse(UpdateGitWebhookResponse.builder().build());
  }

  @GET
  @ApiOperation(value = "List Git Webhooks", nickname = "listGitWebhooks")
  @Operation(operationId = "getGitWebhookList", summary = "Lists all the git webhooks",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Lists all the git webhooks") })
  @Hidden
  public ResponseDTO<ListGitWebhookResponse>
  listGitWebhooks(@QueryParam("accountIdentifier") String accountIdentifier,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("25") int size,
      @QueryParam("webhookSearchTerm") String webhookSearchTerm) {
    return ResponseDTO.newResponse(ListGitWebhookResponse.builder().build());
  }

  @DELETE
  @Path("/{webhookIdentifier}")
  @ApiOperation(value = "Delete a Git Webhook", nickname = "deleteGitWebhook")
  @Operation(operationId = "deleteGitWebhook", summary = "Delete a git webhook",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Delete a git webhook") })
  @Hidden
  public ResponseDTO<Boolean>
  deleteGitWebhook(@QueryParam("accountIdentifier") String accountIdentifier,
      @PathParam("webhookIdentifier") String webhookIdentifier) {
    return ResponseDTO.newResponse();
  }
}
