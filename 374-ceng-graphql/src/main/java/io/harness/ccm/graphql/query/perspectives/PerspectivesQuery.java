/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_VIEW;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.graphql.core.perspectives.PerspectiveFieldsHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveOverviewStatsHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.graphql.dto.common.StatsInfo;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFilterData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveOverviewStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewPreferences;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.service.impl.ClickHouseViewsBillingServiceImpl;

import com.google.cloud.Timestamp;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class PerspectivesQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject private ViewsBillingService viewsBillingService;
  @Inject private CEViewService viewService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private PerspectiveOverviewStatsHelper perspectiveOverviewStatsHelper;
  @Inject private PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject private PerspectiveFieldsHelper perspectiveFieldsHelper;
  @Inject private CCMRbacHelper rbacHelper;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject private ClickHouseViewsBillingServiceImpl clickHouseViewsBillingService;
  private static final int MAX_LIMIT_VALUE = 10_000;

  @GraphQLQuery(name = "perspectiveTrendStats", description = "Trend stats for perspective")
  public PerspectiveTrendStats perspectiveTrendStats(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    groupBy = groupBy != null ? groupBy : Collections.emptyList();

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      groupBy = Collections.emptyList();
    }

    QLCEViewTrendData trendStatsData = viewsBillingService.getTrendStatsDataNg(
        filters, groupBy, aggregateFunction, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery));
    return PerspectiveTrendStats.builder()
        .cost(getStats(trendStatsData.getTotalCost()))
        .idleCost(getStats(trendStatsData.getIdleCost()))
        .unallocatedCost(getStats(trendStatsData.getUnallocatedCost()))
        .systemCost(getStats(trendStatsData.getSystemCost()))
        .utilizedCost(getStats(trendStatsData.getUtilizedCost()))
        .efficiencyScoreStats(trendStatsData.getEfficiencyScoreStats())
        .build();
  }

  @GraphQLQuery(name = "perspectiveForecastCost", description = "Forecast cost for perspective")
  public PerspectiveTrendStats perspectiveForecastCost(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    groupBy = groupBy != null ? groupBy : Collections.emptyList();

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      groupBy = Collections.emptyList();
    }

    QLCEViewTrendInfo forecastCostData = viewsBillingService.getForecastCostData(
        filters, groupBy, aggregateFunction, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery));
    return PerspectiveTrendStats.builder()
        .cost(StatsInfo.builder()
                  .statsTrend(forecastCostData.getStatsTrend())
                  .statsLabel(forecastCostData.getStatsLabel())
                  .statsDescription(forecastCostData.getStatsDescription())
                  .statsValue(forecastCostData.getStatsValue())
                  .value(forecastCostData.getValue())
                  .build())
        .build();
  }

  @GraphQLQuery(name = "perspectiveGrid", description = "Table for perspective")
  public PerspectiveEntityStatsData perspectiveGrid(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLArgument(name = "skipRoundOff") Boolean skipRoundOff,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    skipRoundOff = skipRoundOff != null && skipRoundOff;
    final int maxLimit = Objects.isNull(limit) ? MAX_LIMIT_VALUE : Integer.min(limit, MAX_LIMIT_VALUE);

    return PerspectiveEntityStatsData.builder()
        .data(viewsBillingService
                  .getEntityStatsDataPointsNg(filters, groupBy, aggregateFunction, sortCriteria, maxLimit, offset,
                      viewsQueryHelper.buildQueryParams(accountId, isClusterQuery, skipRoundOff))
                  .getData())
        .build();
  }

  @GraphQLQuery(name = "perspectiveFilters", description = "Filter values for perspective")
  public PerspectiveFilterData perspectiveFilters(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return PerspectiveFilterData.builder()
        .values(viewsBillingService.getFilterValueStatsNg(
            filters, limit, offset, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery)))
        .build();
  }

  @GraphQLQuery(name = "perspectiveOverviewStats", description = "Overview stats for perspective")
  public PerspectiveOverviewStatsData perspectiveOverviewStats(@GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveOverviewStatsHelper.fetch(accountId);
  }

  @GraphQLQuery(name = "perspectiveTimeSeriesStats", description = "Table for perspective")
  public PerspectiveTimeSeriesData perspectiveTimeSeriesStats(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "sortCriteria") List<QLCEViewSortCriteria> sortCriteria,
      @GraphQLArgument(name = "limit") Integer limit, @GraphQLArgument(name = "offset") Integer offset,
      @GraphQLArgument(name = "preferences") QLCEViewPreferences preferences,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    final boolean includeOthers = Objects.nonNull(preferences) && Boolean.TRUE.equals(preferences.getIncludeOthers());
    final boolean includeUnallocatedCost =
        Objects.nonNull(preferences) && Boolean.TRUE.equals(preferences.getIncludeUnallocatedCost());
    final int maxLimit = Objects.isNull(limit) ? MAX_LIMIT_VALUE : Integer.min(limit, MAX_LIMIT_VALUE);
    long timePeriod = perspectiveTimeSeriesHelper.getTimePeriod(groupBy);
    String conversionField = null;
    if (viewsBillingService.isDataGroupedByAwsAccount(filters, groupBy)) {
      conversionField = AWS_ACCOUNT_FIELD;
    }
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    // If group by business mapping is present, query unified table
    isClusterQuery = isClusterQuery && businessMappingId == null;

    ViewQueryParams viewQueryParams = viewsQueryHelper.buildQueryParams(accountId, true, false, isClusterQuery, false);
    Map<String, Map<Timestamp, Double>> sharedCostFromFilters =
        getSharedCostFromFilters(aggregateFunction, filters, groupBy, sortCriteria, businessMappingId, viewQueryParams);
    boolean addSharedCostFromGroupBy = false;

    ViewQueryParams viewQueryParamsWithSkipDefaultGroupBy =
        viewsQueryHelper.buildQueryParams(accountId, true, false, isClusterQuery, false, true);

    PerspectiveTimeSeriesData data;
    if (isClickHouseEnabled) {
      data = clickHouseViewsBillingService.getClickHouseTimeSeriesStatsNg(filters, groupBy, aggregateFunction,
          sortCriteria, includeOthers, maxLimit, viewQueryParams, timePeriod, conversionField, businessMappingId,
          sharedCostFromFilters, addSharedCostFromGroupBy);
    } else {
      data = perspectiveTimeSeriesHelper.fetch(
          viewsBillingService.getTimeSeriesStatsNg(
              filters, groupBy, aggregateFunction, sortCriteria, includeOthers, maxLimit, viewQueryParams),
          timePeriod, conversionField, businessMappingId, accountId, groupBy, sharedCostFromFilters,
          addSharedCostFromGroupBy);
    }

    Map<Long, Double> othersTotalCost = Collections.emptyMap();
    if (includeOthers) {
      othersTotalCost = viewsBillingService.getOthersTotalCostDataNg(
          filters, groupBy, Collections.emptyList(), viewQueryParamsWithSkipDefaultGroupBy);
    }

    Map<Long, Double> unallocatedCost = Collections.emptyMap();
    if (includeUnallocatedCost) {
      unallocatedCost = viewsBillingService.getUnallocatedCostDataNg(
          filters, groupBy, Collections.emptyList(), viewQueryParamsWithSkipDefaultGroupBy);
    }

    return perspectiveTimeSeriesHelper.postFetch(data, includeOthers, othersTotalCost, unallocatedCost);
  }

  private Map<String, Map<Timestamp, Double>> getSharedCostFromFilters(List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sortCriteria,
      String businessMappingId, ViewQueryParams viewQueryParams) {
    Map<String, Map<Timestamp, Double>> sharedCostFromFilters = new HashMap<>();
    if (Objects.nonNull(businessMappingId)) {
      sharedCostFromFilters = viewsBillingService.getSharedCostPerTimestampFromFilters(
          filters, groupBy, aggregateFunction, sortCriteria, viewQueryParams, viewQueryParams.isSkipRoundOff());
    }
    return sharedCostFromFilters;
  }

  @GraphQLQuery(name = "perspectiveFields", description = "Fields for perspective explorer")
  public PerspectiveFieldsData perspectiveFields(@GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return perspectiveFieldsHelper.fetch(accountId, filters);
  }

  @GraphQLQuery(name = "perspectives", description = "Fetch perspectives for account")
  public PerspectiveData perspectives(@GraphQLArgument(name = "folderId") String folderId,
      @GraphQLArgument(name = "sortCriteria") QLCEViewSortCriteria sortCriteria,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    if (StringUtils.isEmpty(folderId)) {
      List<QLCEView> allPerspectives = viewService.getAllViews(accountId, true, sortCriteria);
      List<QLCEView> allowedPerspectives = null;
      if (allPerspectives != null) {
        Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
            allPerspectives.stream().map(perspective -> perspective.getFolderId()).collect(Collectors.toSet()),
            PERSPECTIVE_VIEW);
        allowedPerspectives = allPerspectives.stream()
                                  .filter(perspective -> allowedFolderIds.contains(perspective.getFolderId()))
                                  .collect(Collectors.toList());
      }
      return PerspectiveData.builder().customerViews(allowedPerspectives).build();
    }
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null, folderId);
    return PerspectiveData.builder()
        .customerViews(viewService.getAllViews(accountId, folderId, true, sortCriteria))
        .build();
  }

  @GraphQLQuery(name = "perspectiveTotalCount", description = "Get total count of rows for query")
  public Integer perspectiveTotalCount(@GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return viewsBillingService.getTotalCountForQuery(
        filters, groupBy, viewsQueryHelper.buildQueryParams(accountId, false, false, isClusterQuery, true));
  }

  @GraphQLQuery(name = "workloadLabels", description = "Labels for workloads")
  public Map<String, Map<String, String>> workloadLabels(@GraphQLArgument(name = "workloads") Set<String> workloads,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    return viewsBillingService.getLabelsForWorkloads(accountId, workloads, filters);
  }

  private StatsInfo getStats(QLCEViewTrendInfo trendInfo) {
    if (trendInfo == null) {
      return null;
    }
    return StatsInfo.builder()
        .statsTrend(trendInfo.getStatsTrend())
        .statsLabel(trendInfo.getStatsLabel())
        .statsDescription(trendInfo.getStatsDescription())
        .statsValue(trendInfo.getStatsValue())
        .value(trendInfo.getValue())
        .build();
  }
}
