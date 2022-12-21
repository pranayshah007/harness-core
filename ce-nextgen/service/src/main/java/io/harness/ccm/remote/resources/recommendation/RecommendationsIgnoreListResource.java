/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList;
import io.harness.ccm.graphql.core.recommendation.RecommendationsIgnoreListService;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsIgnoreResourcesDTO;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("recommendation/ignore-list")
@Path("recommendation/ignore-list")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Recommendation Ignore List",
    description = "Cloud Cost recommendation CRUD apis for recommendations ignore list.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class RecommendationsIgnoreListResource {
  @Inject private RecommendationsIgnoreListService ignoreListResource;

  @GET
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Get resources in ignoreList", nickname = "getRecommendationsIgnoreList")
  @LogAccountIdentifier
  @Operation(operationId = "getRecommendationsIgnoreList", description = "Get resources in recommendations ignore list",
      summary = "Get resources in recommendations ignore list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns ignore list", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RecommendationsIgnoreList>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    return ResponseDTO.newResponse(ignoreListResource.getIgnoreList(accountId));
  }

  @POST
  @Path("add")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add resources to ignoreList", nickname = "addRecommendationsIgnoreList")
  @LogAccountIdentifier
  @Operation(operationId = "addRecommendationsIgnoreList", description = "Add resources to recommendations ignore list",
      summary = "Add resources to recommendations ignore list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns ignore list", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RecommendationsIgnoreList>
  add(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing IgnoreList")
      @Valid RecommendationsIgnoreResourcesDTO ignoreResourcesDTO) {
    return ResponseDTO.newResponse(ignoreListResource.addResources(accountId, ignoreResourcesDTO));
  }

  @POST
  @Path("remove")
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Remove resources from ignoreList", nickname = "removeRecommendationsIgnoreList")
  @LogAccountIdentifier
  @Operation(operationId = "removeRecommendationsIgnoreList",
      description = "Remove resources from recommendations ignore list",
      summary = "Remove resources from recommendations ignore list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns ignore list", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RecommendationsIgnoreList>
  remove(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing IgnoreList")
      @Valid RecommendationsIgnoreResourcesDTO ignoreResourcesDTO) {
    return ResponseDTO.newResponse(ignoreListResource.removeResources(accountId, ignoreResourcesDTO));
  }
}
