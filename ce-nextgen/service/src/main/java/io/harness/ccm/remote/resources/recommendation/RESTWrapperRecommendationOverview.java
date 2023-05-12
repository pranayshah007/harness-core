/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationsDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsOverviewQueryV2;
import io.harness.ccm.helper.RecommendationQueryHelper;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterPropertiesDTO;
import io.harness.ccm.remote.beans.recommendation.FilterValuesDTO;
import io.harness.ccm.remote.utils.GraphQLToRESTHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("recommendation/overview")
@Path("recommendation/overview")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Recommendations", description = "Recommendations for workloads and node pools.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class RESTWrapperRecommendationOverview {
  @Inject private RecommendationsOverviewQueryV2 overviewQueryV2;

  private static final String FILTER_DESCRIPTION = "CCM Recommendations filter body.";

  @POST
  @Path("list")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "List Recommendations", nickname = "listRecommendations")
  @Operation(operationId = "listRecommendations",
      description = "Returns the list of Cloud Cost Recommendations for the specified filters.",
      summary = "Return the list of Recommendations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Recommendations available.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RecommendationsDTO>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = FILTER_DESCRIPTION) CCMRecommendationFilterPropertiesDTO ccmFilter) {
    K8sRecommendationFilterDTO filter = RecommendationQueryHelper.buildK8sRecommendationFilterDTO(ccmFilter);
    GraphQLToRESTHelper.setDefaultPaginatedFilterValues(filter);
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    return ResponseDTO.newResponse(overviewQueryV2.recommendations(filter, env));
  }

  @POST
  @Path("stats")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Recommendations Statistics", nickname = "recommendationStats")
  @Operation(operationId = "recommendationStats",
      description = "Returns the Cloud Cost Recommendations statistics for the specified filters.",
      summary = "Return Recommendations statistics",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the statistics of all Recommendations available.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RecommendationOverviewStats>
  stats(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = FILTER_DESCRIPTION) CCMRecommendationFilterPropertiesDTO ccmFilter) {
    K8sRecommendationFilterDTO filter = RecommendationQueryHelper.buildK8sRecommendationFilterDTO(ccmFilter);
    GraphQLToRESTHelper.setDefaultFilterValues(filter);
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    return ResponseDTO.newResponse(overviewQueryV2.recommendationStats(filter, env));
  }

  @POST
  @Path("count")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Recommendations count", nickname = "recommendationsCount")
  @Operation(operationId = "recommendationsCount",
      description = "Returns the total number of Cloud Cost Recommendations based on the specified filters.",
      summary = "Return the number of Recommendations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the count of all Recommendations available.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Integer>
  count(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = FILTER_DESCRIPTION) CCMRecommendationFilterPropertiesDTO ccmFilter) {
    K8sRecommendationFilterDTO filter = RecommendationQueryHelper.buildK8sRecommendationFilterDTO(ccmFilter);
    GraphQLToRESTHelper.setDefaultFilterValues(filter);
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId, filter);

    return ResponseDTO.newResponse(overviewQueryV2.count((RecommendationOverviewStats) null, env));
  }

  @POST
  @Path("filter-values")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Filter values available for Recommendations", nickname = "recommendationFilterValues")
  @Operation(operationId = "recommendationFilterValues",
      description = "Returns the list of filter values for all the specified filters.",
      summary = "Return the list of filter values for the Recommendations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the values available for a filter.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<FilterStatsDTO>>
  filterStats(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @RequestBody(
          required = true, description = "Recommendation Filter Values Body.") FilterValuesDTO filterValues) {
    if (filterValues.getColumns() == null) {
      filterValues.setColumns(emptyList());
    }

    K8sRecommendationFilterDTO filter =
        firstNonNull(RecommendationQueryHelper.buildK8sRecommendationFilterDTO(filterValues.getFilter()),
            K8sRecommendationFilterDTO.builder().build());
    GraphQLToRESTHelper.setDefaultFilterValues(filter);
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    return ResponseDTO.newResponse(overviewQueryV2.recommendationFilterStats(filterValues.getColumns(), filter, env));
  }

  @POST
  @Path("mark-applied")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Mark recommendation as applied", nickname = "markApplied")
  @Operation(operationId = "markRecommendationApplied", description = "Mark recommendation as applied",
      summary = "Return void",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default", description = "Returns void.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Void>
  markApplied(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("recommendationId") @NotNull @Valid String recommendationId) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);
    overviewQueryV2.markRecommendationAsApplied(recommendationId, env);
    return ResponseDTO.newResponse();
  }
}
