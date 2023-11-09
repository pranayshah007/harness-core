/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.GovernanceAiEngineRequestDTO;
import io.harness.ccm.views.dto.GovernanceAiEngineResponseDTO;
import io.harness.ccm.views.dto.GovernancePromptRule;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.service.GovernanceAiEngineService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("governance")
@Path("governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "AiEngine", description = "This contains APIs related to Generative AI Support for Governance ")
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
public class GovernanceAiEngineResource {
  private final CENextGenConfiguration configuration;
  private final GovernanceAiEngineService governanceAiEngineService;

  @Inject
  public GovernanceAiEngineResource(
      CENextGenConfiguration configuration, GovernanceAiEngineService governanceAiEngineService) {
    this.configuration = configuration;
    this.governanceAiEngineService = governanceAiEngineService;
  }

  //@PublicApi
  @Hidden
  @Path("aiengine")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get generative ai generated yaml for governance", nickname = "aiengine")
  @Operation(operationId = "aiengine", description = "Get ai generated yaml for governance",
      summary = "Get ai generated yaml for governance",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Schema", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<GovernanceAiEngineResponseDTO>
  aiengine(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountIdentifier,
      @RequestBody(required = true, description = "Request body for queuing the governance job")
      @Valid GovernanceAiEngineRequestDTO governanceAiEngineRequestDTO) {
    return ResponseDTO.newResponse(governanceAiEngineService.getAiEngineResponse(
        accountIdentifier, configuration.getAiEngineConfig(), governanceAiEngineRequestDTO));
  }

  @GET
  @Path("promptResources")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get supported prompt resources", nickname = "getPromptResources")
  @Operation(operationId = "getPromptResources",
      description = "Get supported prompt resources for a given cloud provider",
      summary = "Get supported prompt resources",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Get supported prompt resources for a given cloud provider",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Set<String>>
  getPromptResources(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountIdentifier,
      @Parameter(required = true, description = "Cloud Provider") @QueryParam(
          "cloudProvider") RuleCloudProviderType ruleCloudProviderType) {
    return ResponseDTO.newResponse(governanceAiEngineService.getGovernancePromptResources(ruleCloudProviderType));
  }

  @GET
  @Path("promptRules")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get sample prompt governance rules", nickname = "getPromptRules")
  @Operation(operationId = "getPromptRules",
      description = "Get sample prompt rules for given cloud provider and resource type",
      summary = "Get sample prompt governance rules",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Get sample prompt rules for given cloud provider and resource type",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<GovernancePromptRule>>
  getPromptRules(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                     NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Valid String accountIdentifier,
      @Parameter(required = true, description = "Cloud Provider") @QueryParam(
          "cloudProvider") RuleCloudProviderType ruleCloudProviderType,
      @Parameter(description = "Resource Type") @QueryParam("resourceType") String resourceType) {
    return ResponseDTO.newResponse(
        governanceAiEngineService.getGovernancePromptRules(ruleCloudProviderType, resourceType));
  }
}
