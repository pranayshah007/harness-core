/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.TRIGGER_KEY;
import static io.harness.ngtriggers.Constants.WEBHOOK_TOKEN;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.NGProcessWebhookResponseDTO;
import io.harness.ngtriggers.beans.dto.WebhookEventProcessingDetails;
import io.harness.ngtriggers.beans.dto.WebhookExecutionDetails;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.WebhookAction;
import io.harness.ngtriggers.beans.source.webhook.WebhookEvent;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.pms.annotations.PipelineServiceAuthIfHasApiKey;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.security.annotations.PublicApi;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

@Api("webhook")
@Path("webhook")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@Tag(name = "Webhook Triggers", description = "This contains APIs related to Webhook Triggers.")
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
@OwnedBy(PIPELINE)
public interface NGTriggerWebhookConfigResource {
  @GET
  @Operation(hidden = true, operationId = "getSourceRepos",
      summary = "Gets source repo types with all supported events.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns source repo types with all supported events.")
      })
  @Path("/sourceRepos")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getSourceRepoToEvent")
  ResponseDTO<Map<WebhookSourceRepo, List<WebhookEvent>>>
  getSourceRepoToEvent();

  @GET
  @Operation(hidden = true, operationId = "getGitTriggerEventDetails",
      summary = "Gets trigger git actions for each supported event.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns trigger git actions for each supported event.")
      })
  @Path("/gitTriggerEventDetails")
  @ApiOperation(value = "Get trigger git actions with Events", nickname = "getGitTriggerEventDetails")
  ResponseDTO<Map<String, Map<String, List<String>>>>
  getGitTriggerEventDetails();

  @GET
  @Operation(hidden = true, operationId = "getWebhookTriggerTypes", summary = "Gets all supported scm webhook type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported scm webhook type.")
      })
  @Path("/webhookTriggerTypes")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getWebhookTriggerTypes")
  ResponseDTO<List<WebhookTriggerType>>
  getWebhookTriggerTypes();

  @GET
  @Operation(hidden = true, operationId = "getGithubTriggerEvents",
      summary = "Gets all supported Github trigger events.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Github trigger events.")
      })
  @Path("/githubTriggerEvents")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getGithubTriggerEvents")
  ResponseDTO<List<GithubTriggerEvent>>
  getGithubTriggerEvents();

  @GET
  @Operation(hidden = true, operationId = "getGithubPRActions", summary = "Gets all supported Github PR event actions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Github PR event actions.")
      })
  @Path("/githubPRActions")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getGithubPRActions")
  ResponseDTO<List<GithubPRAction>>
  getGithubPRActions();

  @GET
  @Operation(hidden = true, operationId = "getGithubIssueCommentActions",
      summary = "Gets all supported Github Issue comment event actions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Github Issue comment event actions.")
      })
  @Path("/githubIssueCommentActions")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getGithubIssueCommentActions")
  ResponseDTO<List<GithubIssueCommentAction>>
  getGithubIssueCommentActions();

  @GET
  @Operation(hidden = true, operationId = "getGitlabTriggerEvents",
      summary = "Gets all supported Gitlab trigger events.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Gitlab trigger events.")
      })
  @Path("/gitlabTriggerEvents")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getGitlabTriggerEvents")
  ResponseDTO<List<GitlabTriggerEvent>>
  getGitlabTriggerEvents();

  @GET
  @Operation(hidden = true, operationId = "getGitlabPRActions", summary = "Gets all supported GitLab PR event actions.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported GitLab PR event actions.")
      })
  @Path("/gitlabPRActions")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getGitlabPRActions")
  ResponseDTO<List<GitlabPRAction>>
  getGitlabTriggerActions();

  @GET
  @Operation(hidden = true, operationId = "getBitbucketTriggerEvents",
      summary = "Gets all supported Bitbucket trigger events.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Bitbucket trigger events.")
      })
  @Path("/bitbucketTriggerEvents")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getBitbucketTriggerEvents")
  ResponseDTO<List<BitbucketTriggerEvent>>
  getBitbucketTriggerEvents();

  @GET
  @Operation(hidden = true, operationId = "getBitbucketPRActions",
      summary = "Gets all supported Bitbucket PR event actions.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all supported Bitbucket PR event actions.")
      })
  @Path("/bitbucketPRActions")
  @ApiOperation(value = "Get Source Repo types with Events", nickname = "getBitbucketPRActions")
  ResponseDTO<List<BitbucketPRAction>>
  getBitbucketPRActions();

  @GET
  @Operation(hidden = true, operationId = "getActionsList",
      summary = "Get all supported actions for event type and source.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all the supported actions for the specified event type and source.")
      })
  @Path("/actions")
  @ApiOperation(value = "Get Actions for event type and source", nickname = "getActionsList")
  ResponseDTO<List<WebhookAction>>
  getActionsList(
      @NotNull @QueryParam("sourceRepo") WebhookSourceRepo sourceRepo, @NotNull @QueryParam("event") String event);

  @POST
  @Operation(operationId = "processWebhookEvent", summary = "Handles event payload for webhook triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns UUID of newly created webhook processing event.")
      })
  @Path("/trigger")
  @ApiOperation(value = "accept webhook event", nickname = "webhookEndpoint")
  @PublicApi
  ResponseDTO<String>
  processWebhookEvent(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull String eventPayload,
      @Context HttpHeaders httpHeaders);

  @POST
  @Operation(operationId = "processCustomWebhookEvent", summary = "Handles event payload for custom webhook triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns UUID of newly created custom webhook processing event.")
      })
  @Path("/custom")
  @ApiOperation(value = "accept custom webhook event", nickname = "customWebhookEndpoint")
  @PipelineServiceAuthIfHasApiKey
  ResponseDTO<String>
  processWebhookEvent(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(TRIGGER_KEY) String triggerIdentifier, @NotNull String eventPayload,
      @Context HttpHeaders httpHeaders);

  @POST
  @Operation(operationId = "processCustomWebhookEventV2",
      summary = "Handles event payload for custom webhook triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns data about of newly created custom webhook processing event.")
      })
  @Path("/custom/v2")
  @ApiOperation(value = "accept custom webhook event V2", nickname = "customWebhookEndpointV2")
  @PipelineServiceAuthIfHasApiKey
  ResponseDTO<NGProcessWebhookResponseDTO>
  processWebhookEventV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = "Trigger Key") @QueryParam(TRIGGER_KEY) String triggerIdentifier,
      @Parameter(description = "Trigger Payload") @NotNull String eventPayload, @Context HttpHeaders httpHeaders);

  @POST
  @Operation(operationId = "processCustomWebhookEventV3",
      summary = "Handles event payload for custom webhook triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns data about of newly created custom webhook processing event.")
      })
  @Path("/custom/{webhookToken}/v3")
  @ApiOperation(value = "accept custom webhook event V3", nickname = "customWebhookEndpointV3")
  @PipelineServiceAuthIfHasApiKey
  ResponseDTO<NGProcessWebhookResponseDTO>
  processWebhookEventV3(@Parameter(description = "Custom Webhook token for custom webhook triggers") @NotNull
                        @PathParam(WEBHOOK_TOKEN) String webhookToken,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = "Trigger Key") @QueryParam(TRIGGER_KEY) String triggerIdentifier,
      @Parameter(description = "Trigger Payload") @NotNull String eventPayload, @Context HttpHeaders httpHeaders);

  @GET
  @Operation(operationId = "fetchWebhookDetails", summary = "Gets webhook event processing details for input eventId.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns webhook event processing details for input eventId.")
      })
  @Path("/triggerProcessingDetails")
  @ApiOperation(value = "fetch webhook event details", nickname = "triggerProcessingDetails")
  @PublicApi
  ResponseDTO<WebhookEventProcessingDetails>
  fetchWebhookDetails(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam("eventId") String eventId);

  @GET
  @Operation(operationId = "fetchWebhookExecutionDetails",
      summary = "Gets webhook event processing details for input eventId.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns webhook event processing details for input eventId.")
      })
  @Path("/triggerExecutionDetails/{eventId}")
  @ApiOperation(value = "fetch webhook event details with execution summary", nickname = "triggerExecutionDetails")
  @PublicApi
  ResponseDTO<WebhookExecutionDetails>
  fetchWebhookExecutionDetails(@NotNull @PathParam("eventId") String eventId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);
}
