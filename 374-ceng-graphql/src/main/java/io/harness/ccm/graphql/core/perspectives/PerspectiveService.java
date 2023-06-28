/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.graphql.dto.common.StatsInfo;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFilterData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveOverviewStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class PerspectiveService {
  @Inject private ViewsBillingService viewsBillingService;
  @Inject private CEViewService viewService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private PerspectiveOverviewStatsHelper perspectiveOverviewStatsHelper;
  @Inject private PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject private PerspectiveFieldsHelper perspectiveFieldsHelper;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject private ClickHouseViewsBillingServiceImpl clickHouseViewsBillingService;
  private static final int MAX_LIMIT_VALUE = 10_000;

  public PerspectiveTrendStats perspectiveTrendStats(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, Boolean isClusterQuery, String accountId) {
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    groupBy = groupBy != null ? groupBy : Collections.emptyList();

    ViewQueryParams viewQueryParams = viewsQueryHelper.buildQueryParams(accountId, isClusterQuery);

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      viewQueryParams = viewsQueryHelper.buildQueryParamsWithSkipGroupBy(viewQueryParams, true);
    }

    QLCEViewTrendData trendStatsData =
        viewsBillingService.getTrendStatsDataNg(filters, groupBy, aggregateFunction, viewQueryParams);
    return PerspectiveTrendStats.builder()
        .cost(getStats(trendStatsData.getTotalCost()))
        .idleCost(getStats(trendStatsData.getIdleCost()))
        .unallocatedCost(getStats(trendStatsData.getUnallocatedCost()))
        .systemCost(getStats(trendStatsData.getSystemCost()))
        .utilizedCost(getStats(trendStatsData.getUtilizedCost()))
        .efficiencyScoreStats(trendStatsData.getEfficiencyScoreStats())
        .build();
  }

  public PerspectiveTrendStats perspectiveForecastCost(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, Boolean isClusterQuery,
      String accountId) {
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    groupBy = groupBy != null ? groupBy : Collections.emptyList();

    ViewQueryParams viewQueryParams = viewsQueryHelper.buildQueryParams(accountId, isClusterQuery);

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      viewQueryParams = viewsQueryHelper.buildQueryParamsWithSkipGroupBy(viewQueryParams, true);
    }

    QLCEViewTrendInfo forecastCostData =
        viewsBillingService.getForecastCostData(filters, groupBy, aggregateFunction, viewQueryParams);
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

  public PerspectiveEntityStatsData perspectiveGrid(List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sortCriteria,
      Integer limit, Integer offset, Boolean isClusterQuery, Boolean skipRoundOff, String accountId) {
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

  public PerspectiveFilterData perspectiveFilters(List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sortCriteria,
      Integer limit, Integer offset, Boolean isClusterQuery, String accountId) {
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return PerspectiveFilterData.builder()
        .values(viewsBillingService.getFilterValueStatsNg(
            filters, limit, offset, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery)))
        .build();
  }

  public PerspectiveOverviewStatsData perspectiveOverviewStats(String accountId) {
    return perspectiveOverviewStatsHelper.fetch(accountId);
  }

  public PerspectiveTimeSeriesData perspectiveTimeSeriesStats(List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sortCriteria,
      Integer limit, Integer offset, QLCEViewPreferences preferences, Boolean isClusterQuery, String accountId) {
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

  public PerspectiveFieldsData perspectiveFields(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters, String accountId) {
    return perspectiveFieldsHelper.fetch(accountId, filters);
  }

  public List<QLCEView> perspectives(QLCEViewSortCriteria sortCriteria, String accountId) {
    return viewService.getAllViews(accountId, true, sortCriteria);
  }

  public List<QLCEView> perspectives(String folderId, QLCEViewSortCriteria sortCriteria, String accountId) {
    return viewService.getAllViews(accountId, folderId, true, sortCriteria);
  }

  public Integer perspectiveTotalCount(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, Boolean isClusterQuery, String accountId) {
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return viewsBillingService.getTotalCountForQuery(
        filters, groupBy, viewsQueryHelper.buildQueryParams(accountId, false, false, isClusterQuery, true));
  }

  public Map<String, Map<String, String>> workloadLabels(
      Set<String> workloads, List<QLCEViewFilterWrapper> filters, String accountId) {
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
