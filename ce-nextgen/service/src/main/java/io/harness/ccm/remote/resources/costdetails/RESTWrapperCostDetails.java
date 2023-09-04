/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.costdetails;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_LIMIT;
import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_LIMIT_CLUSTER_DATA;
import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_OFFSET;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.ClusterCostDetails;
import io.harness.ccm.graphql.dto.common.StatsInfo;
import io.harness.ccm.graphql.dto.perspectives.ClusterCostDetailsQueryParamsDTO;
import io.harness.ccm.graphql.dto.perspectives.CostDetailsQueryParamsDTO;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.query.perspectives.PerspectivesQuery;
import io.harness.ccm.graphql.utils.GraphQLToRESTHelper;
import io.harness.ccm.graphql.utils.RESTToGraphQLHelper;
import io.harness.ccm.helper.CostDetailsQueryHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.exception.InvalidArgumentsException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Api("costdetails")
@Path("costdetails")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Details", description = "Fetch cloud cost data for cost analysis")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class RESTWrapperCostDetails {
  @Inject private PerspectivesQuery perspectivesQuery;
  @Inject private CostDetailsQueryHelper costDetailsQueryHelper;
  private final String COST_DETAILS_BODY_DESCRIPTION = "Cost details query parameters.";
  private final String CLUSTER_COST_DETAILS_BODY_DESCRIPTION = "Cluster cost details query parameters.";
  private static final String DATETIME_DESCRIPTION =
      "Should use org.joda.time.DateTime parsable format. Example, '2022-01-31', '2022-01-31T07:54Z' or '2022-01-31T07:54:51.264Z'. ";

  @POST
  @Path("tabularformat")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return cost detail in tabular format", nickname = "costdetailtabular")
  @Operation(operationId = "costdetailtabular",
      description = "Returns cost details in a tabular format based on the specified query parameters.",
      summary = "Returns cost details in a tabular format",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns cost details in a tabular format based on the specified query parameters.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<PerspectiveEntityStatsData>
  tabularCostDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Perspective identifier of the cost details") @QueryParam(
          "perspectiveId") @NotNull @Valid String perspectiveId,
      @Parameter(description = "Start time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today - 7days") @QueryParam("startTime") @Valid String from,
      @Parameter(description = "End time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today") @QueryParam("endTime") @Valid String to,
      @NotNull @Valid @RequestBody(
          required = true, description = COST_DETAILS_BODY_DESCRIPTION) CostDetailsQueryParamsDTO queryParams) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }

    Boolean skipRoundOff = queryParams.getSkipRoundOff();
    Integer limit = queryParams.getLimit();
    Integer offset = queryParams.getOffset();
    List<QLCEViewAggregation> aggregationList = RESTToGraphQLHelper.getCostAggregation();
    List<QLCEViewSortCriteria> sortList = RESTToGraphQLHelper.getCostSortingCriteria(queryParams.getSortOrder());
    List<QLCEViewFilterWrapper> filters;
    try {
      filters = RESTToGraphQLHelper.convertFilters(queryParams.getFilters(), perspectiveId, startTime, endTime);
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    List<QLCEViewGroupBy> groupBy;
    try {
      groupBy = RESTToGraphQLHelper.convertGroupBy(queryParams.getGroupBy());
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    PerspectiveEntityStatsData perspectiveGridData = perspectivesQuery.perspectiveGrid(aggregationList, filters,
        groupBy, sortList, firstNonNull(limit, (int) DEFAULT_LIMIT), firstNonNull(offset, (int) DEFAULT_OFFSET), null,
        false, skipRoundOff != null && skipRoundOff, env);

    return ResponseDTO.newResponse(perspectiveGridData);
  }

  @POST
  @Path("timeseriesformat")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return cost detail in time series format", nickname = "costdetailttimeseries")
  @Operation(operationId = "costdetailttimeseries",
      description = "Returns cost details in a time series format based on the specified query parameters.",
      summary = "Returns cost details in a time series format",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns cost details in a time series format based on the specified query parameters.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<PerspectiveTimeSeriesData>
  timeSeriesCostDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Perspective identifier of the cost details") @QueryParam(
          "perspectiveId") @NotNull @Valid String perspectiveId,
      @Parameter(description = "Start time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today - 7days") @QueryParam("startTime") @Valid String from,
      @Parameter(description = "End time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today") @QueryParam("endTime") @Valid String to,
      @NotNull @Valid @RequestBody(
          required = true, description = COST_DETAILS_BODY_DESCRIPTION) CostDetailsQueryParamsDTO queryParams) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }

    Integer limit = queryParams.getLimit();
    Integer offset = queryParams.getOffset();
    List<QLCEViewAggregation> aggregationList = RESTToGraphQLHelper.getCostAggregation();
    List<QLCEViewSortCriteria> sortList = RESTToGraphQLHelper.getCostSortingCriteria(queryParams.getSortOrder());
    List<QLCEViewFilterWrapper> filters;
    try {
      filters = RESTToGraphQLHelper.convertFilters(queryParams.getFilters(), perspectiveId, startTime, endTime);
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(RESTToGraphQLHelper.convertTimeSeriesGroupBy(queryParams.getTimeResolution()));
    try {
      groupBy = RESTToGraphQLHelper.convertGroupBy(queryParams.getGroupBy());
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    PerspectiveTimeSeriesData perspectiveTimeSeriesData =
        perspectivesQuery.perspectiveTimeSeriesStats(aggregationList, filters, groupBy, sortList,
            firstNonNull(limit, (int) DEFAULT_LIMIT), firstNonNull(offset, (int) DEFAULT_OFFSET), null, false, env);
    return ResponseDTO.newResponse(perspectiveTimeSeriesData);
  }

  @POST
  @Path("overview")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Returns an overview of the cost", nickname = "costdetailoverview")
  @Operation(operationId = "costdetailoverview",
      description = "Returns total cost, cost trend, and the time period based on the specified query parameters.",
      summary = "Returns an overview of the cost",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns total cost, cost trend, and the time period based on the specified query parameters.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<StatsInfo>
  overviewCostDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Perspective identifier of the cost details") @QueryParam(
          "perspectiveId") @NotNull @Valid String perspectiveId,
      @Parameter(description = "Start time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today - 7days") @QueryParam("startTime") @Valid String from,
      @Parameter(description = "End time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today") @QueryParam("endTime") @Valid String to,
      @Valid @RequestBody(description = COST_DETAILS_BODY_DESCRIPTION) CostDetailsQueryParamsDTO queryParams) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }
    List<QLCEViewAggregation> aggregationList = RESTToGraphQLHelper.getCostAggregation();
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    try {
      if (queryParams != null) {
        filters = RESTToGraphQLHelper.convertFilters(queryParams.getFilters(), perspectiveId, startTime, endTime);
      }
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    PerspectiveTrendStats perspectiveTrendStats =
        perspectivesQuery.perspectiveTrendStats(filters, Collections.emptyList(), aggregationList, null, false, env);
    return ResponseDTO.newResponse(perspectiveTrendStats.getCost());
  }

  @POST
  @Path("clusterData")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return cluster data", nickname = "clusterData")
  @Operation(operationId = "clusterData", description = "Returns cluster data based on the specified query parameters.",
      summary = "Returns cluster data in a tabular format",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns cluster data in a tabular format based on the specified query parameters.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<ClusterCostDetails>>
  tabularCostDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(description = "Start time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today - 7days") @QueryParam("startTime") @Valid String from,
      @Parameter(description = "End time of the cost details. " + DATETIME_DESCRIPTION
              + " Defaults to Today") @QueryParam("endTime") @Valid String to,
      @NotNull @Valid @RequestBody(
          required = true, description = COST_DETAILS_BODY_DESCRIPTION) ClusterCostDetailsQueryParamsDTO queryParams) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }

    Boolean skipRoundOff = queryParams.getSkipRoundOff();
    Integer limit = queryParams.getLimit();
    Integer offset = queryParams.getOffset();
    List<QLCEViewAggregation> aggregationList = RESTToGraphQLHelper.getAggregations(queryParams.getAggregations());
    List<QLCEViewSortCriteria> sortList = RESTToGraphQLHelper.getClusterCostSortingCriteria(queryParams.getSortOrder());
    List<QLCEViewFilterWrapper> filters;
    try {
      filters = RESTToGraphQLHelper.convertFilters(queryParams.getFilters(), startTime, endTime);
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    List<QLCEViewGroupBy> groupBy;
    try {
      groupBy = RESTToGraphQLHelper.convertGroupBy(queryParams.getGroupBy());
    } catch (Exception e) {
      throw new InvalidArgumentsException(e.getMessage());
    }

    PerspectiveEntityStatsData perspectiveGridData = perspectivesQuery.perspectiveGrid(aggregationList, filters,
        groupBy, sortList, firstNonNull(limit, (int) DEFAULT_LIMIT_CLUSTER_DATA),
        firstNonNull(offset, (int) DEFAULT_OFFSET), null, true, skipRoundOff != null && skipRoundOff, env);

    // List of workloads in case data is grouped by workload
    Set<String> workloads = costDetailsQueryHelper.getWorkloadsFromCostDetailsResponse(perspectiveGridData, groupBy);
    Map<String, Map<String, String>> workloadLabels = perspectivesQuery.workloadLabels(workloads, filters, env);

    List<String> selectedLabels = queryParams.getSelectedLabels();
    List<ClusterCostDetails> clusterCostDetails =
        costDetailsQueryHelper.getClusterCostDataFromGridResponse(perspectiveGridData);

    if (selectedLabels != null && !workloads.isEmpty()) {
      clusterCostDetails = costDetailsQueryHelper.updateGridResponseWithSelectedLabels(
          clusterCostDetails, selectedLabels, workloadLabels);
    }

    return ResponseDTO.newResponse(clusterCostDetails);
  }
}
