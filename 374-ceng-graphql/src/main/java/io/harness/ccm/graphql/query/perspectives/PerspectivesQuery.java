/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveFieldsHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveOverviewStatsHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.graphql.dto.common.StatsInfo;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFieldsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveFilterData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveOverviewStatsData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTimeSeriesData;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.views.entities.ViewQueryParams;
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

import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class PerspectivesQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject ViewsBillingService viewsBillingService;
  @Inject private CEViewService viewService;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject BigQueryService bigQueryService;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject PerspectiveOverviewStatsHelper perspectiveOverviewStatsHelper;
  @Inject PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject PerspectiveFieldsHelper perspectiveFieldsHelper;

  @GraphQLQuery(name = "perspectiveTrendStats", description = "Trend stats for perspective")
  public PerspectiveTrendStats perspectiveTrendStats(
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    QLCEViewTrendData trendStatsData = viewsBillingService.getTrendStatsDataNg(bigQuery, filters, aggregateFunction,
        cloudProviderTableName, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery));
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
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "isClusterQuery") Boolean isClusterQuery,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    QLCEViewTrendInfo forecastCostData = viewsBillingService.getForecastCostData(bigQuery, filters, aggregateFunction,
        cloudProviderTableName, viewsQueryHelper.buildQueryParams(accountId, isClusterQuery));
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
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
    isClusterQuery = isClusterQuery != null && isClusterQuery;
    skipRoundOff = skipRoundOff != null && skipRoundOff;

    return PerspectiveEntityStatsData.builder()
        .data(viewsBillingService
                  .getEntityStatsDataPointsNg(bigQuery, filters, groupBy, aggregateFunction, sortCriteria,
                      cloudProviderTableName, limit, offset,
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
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return PerspectiveFilterData.builder()
        .values(viewsBillingService.getFilterValueStatsNg(bigQuery, filters, cloudProviderTableName, limit, offset,
            viewsQueryHelper.buildQueryParams(accountId, isClusterQuery)))
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
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
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

    PerspectiveTimeSeriesData data = perspectiveTimeSeriesHelper.fetch(
        viewsBillingService.getTimeSeriesStatsNg(bigQuery, filters, groupBy, aggregateFunction, sortCriteria,
            cloudProviderTableName, includeOthers, limit, viewQueryParams),
        timePeriod, conversionField, businessMappingId, accountId);

    Map<Long, Double> unallocatedCost = null;
    if (includeUnallocatedCost) {
      unallocatedCost = viewsBillingService.getUnallocatedCostDataNg(
          bigQuery, filters, groupBy, Collections.emptyList(), cloudProviderTableName, viewQueryParams);
    }

    return perspectiveTimeSeriesHelper.postFetch(data, limit, includeOthers, unallocatedCost);
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
      return PerspectiveData.builder().customerViews(viewService.getAllViews(accountId, true, sortCriteria)).build();
    }
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
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    BigQuery bigQuery = bigQueryService.get();
    isClusterQuery = isClusterQuery != null && isClusterQuery;

    return viewsBillingService.getTotalCountForQuery(bigQuery, filters, groupBy, cloudProviderTableName,
        viewsQueryHelper.buildQueryParams(accountId, false, false, isClusterQuery, true));
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
