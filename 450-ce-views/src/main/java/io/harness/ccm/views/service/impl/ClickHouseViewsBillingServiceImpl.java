/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.DataTypeConstants.DATE;
import static io.harness.ccm.commons.constants.DataTypeConstants.DATETIME;
import static io.harness.ccm.commons.constants.DataTypeConstants.FLOAT64;
import static io.harness.ccm.commons.constants.DataTypeConstants.STRING;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.graphql.QLCEViewFilterOperator.IN;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantUnallocatedCost;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.LABEL_KEY_ALIAS;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.LABEL_VALUE_ALIAS;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_GRANULARITY;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.EfficiencyScoreStats;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewDataPoint;
import io.harness.ccm.views.graphql.QLCEViewDataPoint.QLCEViewDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsMetaDataFields;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.ccm.views.helper.BusinessMappingSharedCostHelper;
import io.harness.ccm.views.helper.ClickHouseQueryResponseHelper;
import io.harness.ccm.views.helper.InstanceDetailsHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewBusinessMappingResponseHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.utils.ClickHouseConstants;
import io.harness.timescaledb.DBUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import io.fabric8.utils.Lists;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ClickHouseViewsBillingServiceImpl implements ViewsBillingService {
  private static final String IDLE_COST_LABEL = "Idle Cost";
  private static final String UNALLOCATED_COST_LABEL = "Unallocated Cost";
  private static final String UTILIZED_COST_LABEL = "Utilized Cost";
  private static final String SYSTEM_COST_LABEL = "System Cost";
  private static final int MAX_LIMIT_VALUE = 10_000;

  @Inject @Nullable @Named("clickHouseConfig") private ClickHouseConfig clickHouseConfig;
  @Inject private ClickHouseService clickHouseService;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;
  @Inject private CEViewService viewService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private InstanceDetailsHelper instanceDetailsHelper;
  @Inject private EntityMetadataService entityMetadataService;
  @Inject private BusinessMappingService businessMappingService;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Inject private CEMetadataRecordDao ceMetadataRecordDao;
  @Inject private ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject private ViewBusinessMappingResponseHelper viewBusinessMappingResponseHelper;
  @Inject private ClickHouseQueryResponseHelper clickHouseQueryResponseHelper;
  @Inject private BusinessMappingSharedCostHelper businessMappingSharedCostHelper;

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to get data for filter panel
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public List<String> getFilterValueStats(List<QLCEViewFilterWrapper> filters, Integer limit, Integer offset) {
    return getFilterValueStatsNg(filters, limit, offset, viewsQueryHelper.buildQueryParams(null, false));
  }

  @Override
  public List<String> getFilterValueStatsNg(
      List<QLCEViewFilterWrapper> filters, Integer limit, Integer offset, ViewQueryParams queryParams) {
    // Check if cost category filter values are requested
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromFilters(filters);
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    if (businessMappingId != null) {
      return businessMappingService.getCostTargetNames(businessMappingId, queryParams.getAccountId(),
          viewsQueryHelper.getSearchValueFromBusinessMappingFilter(filters, businessMappingId));
    }

    List<QLCEViewFilter> idFilters = awsAccountFieldHelper.addAccountIdsByAwsAccountNameFilter(
        viewParametersHelper.getIdFilters(filters), queryParams.getAccountId());

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, null);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    // Get the query
    ViewsQueryMetadata viewsQueryMetadata;
    if (!sharedCostBusinessMappings.isEmpty()) {
      viewsQueryMetadata = businessMappingSharedCostHelper.getFilterValueStatsSharedCostQuery(
          filters, cloudProviderTableName, limit, offset, queryParams, sharedCostBusinessMappings);
    } else {
      viewsQueryMetadata = viewBillingServiceHelper.getFilterValueStatsQuery(
          filters, cloudProviderTableName, limit, offset, queryParams, null);
    }

    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(viewsQueryMetadata.getQuery().toString()).build();
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryConfig.getQuery());
      boolean isClusterPerspective =
          viewParametersHelper.isClusterPerspective(filters, Collections.emptyList()) || queryParams.isClusterQuery();
      return clickHouseQueryResponseHelper.getFilterValuesData(
          queryParams.getAccountId(), viewsQueryMetadata, resultSet, idFilters, isClusterPerspective);
    } catch (SQLException e) {
      log.error("Failed to getViewFilterValueStats for query {}", queryConfig.getQuery(), e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to get data for Grid
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public List<QLCEViewEntityStatsDataPoint> getEntityStatsDataPoints(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      Integer limit, Integer offset) {
    ViewQueryParams queryParams = viewsQueryHelper.buildQueryParams(null, false, false, false, false);
    // account id is not required for query builder of current-gen, therefore is passed null
    return getEntityStatsDataPointsNg(filters, groupBy, aggregateFunction, sort, limit, offset, queryParams).getData();
  }

  @Override
  public QLCEViewGridData getEntityStatsDataPointsNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, Integer limit, Integer offset,
      ViewQueryParams queryParams) {
    boolean isClusterPerspective = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    if (isClusterPerspective) {
      cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_CLUSTER_DATA_TABLE;
    }
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    BusinessMapping businessMapping = businessMappingId != null ? businessMappingService.get(businessMappingId) : null;

    List<ViewRule> viewRules = getViewRules(filters);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    // Conversion field is not null in case entity id to name conversion is required for a field
    String conversionField = null;
    if (isDataGroupedByAwsAccount(filters, groupBy) && !queryParams.isUsedByTimeSeriesStats()) {
      conversionField = AWS_ACCOUNT_FIELD;
    }

    boolean isGroupByBusinessMapping = viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy);

    Map<String, ViewCostData> costTrendData = new HashMap<>();
    long startTimeForTrendData = 0L;
    if (!queryParams.isUsedByTimeSeriesStats()) {
      costTrendData = getEntityStatsCostTrendData(filters, groupBy, aggregateFunction, sort, limit, offset, queryParams,
          cloudProviderTableName, isClusterPerspective, viewRules, sharedCostBusinessMappings,
          isGroupByBusinessMapping);
      startTimeForTrendData = viewParametersHelper.getStartTimeForTrendFilters(filters);
    }

    Map<String, Double> sharedCostsFromRulesAndFilters;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      return getEntityStatsSharedCostDataPoints(filters, groupBy, aggregateFunction, sort, cloudProviderTableName,
          limit, offset, queryParams, isClusterPerspective, businessMapping, viewRules, sharedCostBusinessMappings,
          conversionField, costTrendData, startTimeForTrendData);
    } else {
      sharedCostsFromRulesAndFilters =
          getSharedCostFromFilters(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams,
              sharedCostBusinessMappings, limit, offset, queryParams.isSkipRoundOff(), viewRules);
    }

    SelectQuery query = viewBillingServiceHelper.getQuery(
        filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    log.info("Query for grid (with limit as {}): {}", limit, query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());

      boolean addSharedCostFromGroupBy = !businessMappingIds.contains(businessMappingId);
      return viewBusinessMappingResponseHelper.costCategoriesPostFetchResponseUpdate(
          clickHouseQueryResponseHelper.convertToEntityStatsData(resultSet, cloudProviderTableName, costTrendData,
              startTimeForTrendData, isClusterPerspective, queryParams.isUsedByTimeSeriesStats(),
              queryParams.isSkipRoundOff(), conversionField, queryParams.getAccountId(), groupBy, businessMapping,
              addSharedCostFromGroupBy),
          businessMappingId, sharedCostBusinessMappings, sharedCostsFromRulesAndFilters);

    } catch (SQLException e) {
      log.error("Failed to getEntityStatsDataPoints for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  @Nullable
  private Map<String, ViewCostData> getEntityStatsCostTrendData(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final Integer limit, final Integer offset,
      final ViewQueryParams queryParams, final String cloudProviderTableName, final boolean isClusterPerspective,
      final List<ViewRule> viewRules, final List<BusinessMapping> sharedCostBusinessMappings,
      final boolean isGroupByBusinessMapping) {
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      SelectQuery query = businessMappingSharedCostHelper.getEntityStatsSharedCostDataQueryForCostTrend(filters,
          groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
      query.addCustomization(new PgLimitClause(limit));
      query.addCustomization(new PgOffsetClause(offset));
      ResultSet resultSet = null;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        costTrendData = clickHouseQueryResponseHelper.convertToEntityStatsCostTrendData(
            resultSet, isClusterPerspective, queryParams.isSkipRoundOff(), groupBy);
      } catch (SQLException e) {
        log.error("Failed to getEntityStatsDataPoints for query {}", query, e);
      } finally {
        DBUtils.close(resultSet);
      }
    } else {
      costTrendData = getEntityStatsDataForCostTrend(
          filters, groupBy, aggregateFunction, sort, cloudProviderTableName, limit, offset, queryParams);
    }
    return costTrendData;
  }

  @Nullable
  private QLCEViewGridData getEntityStatsSharedCostDataPoints(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final String cloudProviderTableName, final Integer limit,
      final Integer offset, final ViewQueryParams queryParams, final boolean isClusterPerspective,
      final BusinessMapping businessMapping, final List<ViewRule> viewRules,
      final List<BusinessMapping> sharedCostBusinessMappings, final String conversionField,
      final Map<String, ViewCostData> costTrendData, final long startTimeForTrendData) {
    // Group by other than cost category and shared bucket is present in the rules.
    final SelectQuery query = businessMappingSharedCostHelper.getEntityStatsSharedCostDataQuery(filters, groupBy,
        aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
    if (Objects.isNull(query)) {
      return null;
    }
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToEntityStatsData(resultSet, cloudProviderTableName, costTrendData,
          startTimeForTrendData, isClusterPerspective, queryParams.isUsedByTimeSeriesStats(),
          queryParams.isSkipRoundOff(), conversionField, queryParams.getAccountId(), groupBy, businessMapping, false);
    } catch (SQLException e) {
      log.error("Failed to getEntityStatsDataPoints for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  private Map<String, ViewCostData> getEntityStatsDataForCostTrend(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, Integer limit, Integer offset, ViewQueryParams queryParams) {
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    cloudProviderTableName = viewBillingServiceHelper.getUpdatedCloudProviderTableName(
        filters, groupBy, aggregateFunction, queryParams.getAccountId(), cloudProviderTableName, isClusterTableQuery);
    List<QLCEViewAggregation> aggregationsForCostTrend =
        viewParametersHelper.getAggregationsForEntityStatsCostTrend(aggregateFunction);
    List<QLCEViewFilterWrapper> filtersForCostTrend = viewParametersHelper.getFiltersForEntityStatsCostTrend(filters);
    SelectQuery query = viewBillingServiceHelper.getQuery(filtersForCostTrend, groupBy, aggregationsForCostTrend, sort,
        cloudProviderTableName, queryParams, Collections.emptyList());
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToEntityStatsCostTrendData(
          resultSet, isClusterTableQuery, queryParams.isSkipRoundOff(), groupBy);
    } catch (SQLException e) {
      log.error("Failed to getEntityStatsDataForCostTrend for account {}", queryParams.getAccountId(), e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  private Map<String, Double> getSharedCostFromFilters(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, ViewQueryParams queryParams, List<BusinessMapping> sharedCostBusinessMappings,
      Integer limit, Integer offset, boolean skipRoundOff, List<ViewRule> viewRules) {
    Map<String, Double> sharedCostsFromFilters = new HashMap<>();
    String groupByBusinessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    for (BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      List<QLCEViewGroupBy> businessMappingGroupBy =
          viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping);
      ViewQueryParams modifiedQueryParams = viewsQueryHelper.buildQueryParamsWithSkipGroupBy(queryParams, false);
      SelectQuery query = viewBillingServiceHelper.getQuery(
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid()), groupBy,
          businessMappingGroupBy, aggregateFunction, sort, cloudProviderTableName, modifiedQueryParams,
          sharedCostBusinessMapping, Collections.emptyList());
      query.addCustomization(new PgLimitClause(limit));
      query.addCustomization(new PgOffsetClause(offset));
      ResultSet resultSet = null;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        sharedCostsFromFilters =
            clickHouseQueryResponseHelper.convertToSharedCostFromFiltersData(resultSet, filters, groupBy,
                sharedCostBusinessMapping, groupByBusinessMappingId, sharedCostsFromFilters, skipRoundOff, viewRules);
      } catch (SQLException e) {
        log.error("Failed to getSharedCostFromFilters for query: {}", query, e);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return sharedCostsFromFilters;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to get data for Chart
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public TableResult getTimeSeriesStats(String accountId, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort) {
    return null;
  }

  @Override
  public TableResult getTimeSeriesStatsNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, boolean includeOthers,
      Integer limit, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    QLCEViewGridData gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    boolean isGroupByBusinessMapping = viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy);
    List<QLCEViewFilterWrapper> modifiedFilters =
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, gridData, groupByExcludingGroupByTime);

    SelectQuery query;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      List<ViewRule> viewRules = getViewRules(modifiedFilters);
      query = businessMappingSharedCostHelper.getTimeSeriesStatsSharedCostDataQuery(modifiedFilters, groupBy,
          aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
      log.info("TimeSeriesStats shared cost query: {}", query);
    } else {
      query = viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, aggregateFunction, sort,
          cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    }
    if (Objects.isNull(query)) {
      return null;
    }
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
    } catch (SQLException e) {
      log.error("Failed to getTimeSeriesStatsNg for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  public PerspectiveTimeSeriesData getClickHouseTimeSeriesStatsNg(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      boolean includeOthers, Integer limit, ViewQueryParams queryParams, long timePeriod, String conversionField,
      String businessMappingId, Map<String, Map<Timestamp, Double>> sharedCostFromFilters,
      boolean addSharedCostFromGroupBy) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    QLCEViewGridData gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    boolean isGroupByBusinessMapping = viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy);
    List<QLCEViewFilterWrapper> modifiedFilters =
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, gridData, groupByExcludingGroupByTime);

    SelectQuery query;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      List<ViewRule> viewRules = getViewRules(modifiedFilters);
      query = businessMappingSharedCostHelper.getTimeSeriesStatsSharedCostDataQuery(modifiedFilters, groupBy,
          aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
      log.info("TimeSeriesStats shared cost query: {}", query);
    } else {
      query = viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, aggregateFunction, sort,
          cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    }
    if (Objects.isNull(query)) {
      return null;
    }
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToTimeSeriesData(resultSet, timePeriod, conversionField,
          businessMappingId, queryParams.getAccountId(), groupBy, sharedCostFromFilters, addSharedCostFromGroupBy);
    } catch (SQLException e) {
      log.error("Failed to getTimeSeriesStatsNg for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  public List<QLCEViewTimeSeriesData> getClickHouseTimeSeriesStatsNgForReport(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      Integer limit, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    QLCEViewGridData gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    boolean isGroupByBusinessMapping = viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy);
    List<QLCEViewFilterWrapper> modifiedFilters =
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, gridData, groupByExcludingGroupByTime);

    SelectQuery query;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      List<ViewRule> viewRules = getViewRules(modifiedFilters);
      query = businessMappingSharedCostHelper.getTimeSeriesStatsSharedCostDataQuery(modifiedFilters, groupBy,
          aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
      log.info("TimeSeriesStats shared cost query: {}", query);
    } else {
      query = viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, aggregateFunction, sort,
          cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    }
    if (Objects.isNull(query)) {
      return null;
    }
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return convertToQLViewTimeSeriesData(resultSet, queryParams.getAccountId(), groupBy);
    } catch (SQLException e) {
      log.error("Failed to getTimeSeriesStatsNg for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  private List<QLCEViewTimeSeriesData> convertToQLViewTimeSeriesData(
      ResultSet resultSet, String accountId, List<QLCEViewGroupBy> groupBy) {
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap = new HashMap<>();
    Set<String> awsAccounts = new HashSet<>();
    int totalColumns = clickHouseQueryResponseHelper.getTotalColumnsCount(resultSet);
    try {
      while (resultSet != null && resultSet.next()) {
        QLCEViewDataPointBuilder billingDataPointBuilder = QLCEViewDataPoint.builder();
        Long startTimeTruncatedTimestamp = null;
        double value = 0.0;
        int columnIndex = 1;
        while (columnIndex <= totalColumns) {
          String columnName = resultSet.getMetaData().getColumnName(columnIndex);
          String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
          if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
            String stringValue = clickHouseQueryResponseHelper.fetchStringValue(resultSet, columnName, fieldName);
            if (AWS_ACCOUNT_FIELD_ID.equalsIgnoreCase(columnName)) {
              awsAccounts.add(stringValue);
            }
            billingDataPointBuilder.name(stringValue).id(stringValue);
          } else if (columnType.toUpperCase(Locale.ROOT).contains(DATETIME)
              || columnType.toUpperCase(Locale.ROOT).contains(DATE)) {
            startTimeTruncatedTimestamp = clickHouseQueryResponseHelper.fetchTimestampValue(resultSet, columnName);
          } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
            value += clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName);
          }
          columnIndex++;
        }

        billingDataPointBuilder.value(value);
        List<QLCEViewDataPoint> dataPoints = new ArrayList<>();
        if (timeSeriesDataPointsMap.containsKey(startTimeTruncatedTimestamp)) {
          dataPoints = timeSeriesDataPointsMap.get(startTimeTruncatedTimestamp);
        }
        dataPoints.add(billingDataPointBuilder.build());
        timeSeriesDataPointsMap.put(startTimeTruncatedTimestamp, dataPoints);
      }
    } catch (SQLException e) {
      log.error("Failed to build chart for scheduled report");
    }

    return viewBillingServiceHelper.convertTimeSeriesPointsMapToList(
        viewBillingServiceHelper.modifyTimeSeriesDataPointsMap(timeSeriesDataPointsMap, awsAccounts, accountId));
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get columns of given table
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public List<String> getColumnsForTable(String informationSchemaView, String table) {
    SelectQuery query = viewsQueryBuilder.getInformationSchemaQueryForColumns(informationSchemaView, table);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToColumnList(resultSet);
    } catch (SQLException e) {
      log.error("Failed to getColumnsData. {}", e.toString());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to check if given perspective is cluster only perspective
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public boolean isClusterPerspective(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    return viewParametersHelper.isClusterPerspective(filters, groupBy);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get data for Total cost summary card
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public QLCEViewTrendInfo getTrendStatsData(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction) {
    return getTrendStatsDataNg(
        filters, Collections.emptyList(), aggregateFunction, viewsQueryHelper.buildQueryParams(null, false))
        .getTotalCost();
  }

  @Override
  public QLCEViewTrendData getTrendStatsDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    cloudProviderTableName = viewBillingServiceHelper.getUpdatedCloudProviderTableName(
        filters, null, aggregateFunction, "", cloudProviderTableName, queryParams.isClusterQuery());
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);
    BusinessMapping businessMapping = businessMappingId != null ? businessMappingService.get(businessMappingId) : null;
    boolean addSharedCostFromGroupBy = !businessMappingIds.contains(businessMappingId);

    List<QLCEViewTimeFilter> trendTimeFilters = viewsQueryHelper.getTrendFilters(timeFilters);
    List<QLCEViewFilterWrapper> filtersForPrevPeriod = viewsQueryHelper.getUpdatedFiltersForPrevPeriod(filters);

    SelectQuery query = viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
        cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    SelectQuery prevTrendStatsQuery = viewBillingServiceHelper.getQuery(filtersForPrevPeriod, groupBy,
        aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);

    Instant trendStartInstant =
        Instant.ofEpochMilli(viewsQueryHelper.getTimeFilter(trendTimeFilters, AFTER).getValue().longValue());

    double sharedCostFromRulesAndFilters = getTotalSharedCostFromFilters(filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    double prevSharedCostFromRulesAndFilters = getTotalSharedCostFromFilters(filtersForPrevPeriod, groupBy,
        aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);

    List<String> fields = getSelectedFields(filters, groupBy, aggregateFunction);
    ViewCostData costData = getViewTrendStatsCostData(
        query, fields, addSharedCostFromGroupBy, businessMapping, sharedCostFromRulesAndFilters);
    ViewCostData prevCostData = getViewTrendStatsCostData(
        prevTrendStatsQuery, fields, addSharedCostFromGroupBy, businessMapping, prevSharedCostFromRulesAndFilters);

    EfficiencyScoreStats efficiencyScoreStats = null;
    if (isClusterTableQuery) {
      efficiencyScoreStats = viewsQueryHelper.getEfficiencyScoreStats(costData, prevCostData);
    }

    return QLCEViewTrendData.builder()
        .totalCost(viewBillingServiceHelper.getCostBillingStats(
            costData, prevCostData, timeFilters, trendStartInstant, isClusterTableQuery, null))
        .idleCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, IDLE_COST_LABEL, null))
        .unallocatedCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, UNALLOCATED_COST_LABEL, null))
        .systemCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, SYSTEM_COST_LABEL, null))
        .utilizedCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, UTILIZED_COST_LABEL, null))
        .efficiencyScoreStats(efficiencyScoreStats)
        .build();
  }

  private ViewCostData getViewTrendStatsCostData(SelectQuery query, List<String> fields,
      boolean addSharedCostFromGroupBy, BusinessMapping businessMappingFromGroupBy,
      double sharedCostFromFiltersAndRules) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();

    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryConfig.getQuery());
      return clickHouseQueryResponseHelper.convertToTrendStatsData(
          resultSet, fields, addSharedCostFromGroupBy, businessMappingFromGroupBy, sharedCostFromFiltersAndRules);
    } catch (SQLException e) {
      log.error("Failed to getTrendStatsData. {}", e.toString());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get total count of rows
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public Integer getTotalCountForQuery(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    SelectQuery query = getTotalCountQuery(filters, groupBy, cloudProviderTableName, queryParams);
    if (Objects.isNull(query)) {
      return null;
    }
    ResultSet resultSet = null;
    Integer totalCount = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      while (resultSet != null && resultSet.next()) {
        totalCount = resultSet.getInt("totalCount");
      }
      return totalCount;
    } catch (SQLException e) {
      log.error("Failed to getTrendStatsData. {}", e.toString());
    } finally {
      DBUtils.close(resultSet);
    }
    return totalCount;
  }

  private SelectQuery getTotalCountQuery(final List<QLCEViewFilterWrapper> filters, final List<QLCEViewGroupBy> groupBy,
      final String cloudProviderTableName, final ViewQueryParams queryParams) {
    SelectQuery query;
    List<ViewRule> viewRules = getViewRules(filters);

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);

    if (!sharedCostBusinessMappings.isEmpty() && !viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      // Group by other than cost category and shared cost bucket is present
      final ViewQueryParams viewQueryParams =
          viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), queryParams.isTimeTruncGroupByRequired(),
              queryParams.isUsedByTimeSeriesStats(), queryParams.isClusterQuery(), false);
      query = businessMappingSharedCostHelper.getTotalCountSharedCostDataQuery(
          filters, groupBy, cloudProviderTableName, viewQueryParams, sharedCostBusinessMappings, viewRules);
    } else {
      query = viewBillingServiceHelper.getQuery(filters, groupBy, Collections.emptyList(), Collections.emptyList(),
          cloudProviderTableName, queryParams, Collections.emptyList());
    }
    return query;
  }

  @Override
  public Map<Long, Double> getUnallocatedCostDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewSortCriteria> sort, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    if (viewBillingServiceHelper.shouldShowUnallocatedCost(groupBy)
        && viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams)) {
      final List<QLCEViewAggregation> aggregateFunction =
          Collections.singletonList(QLCEViewAggregation.builder()
                                        .operationType(QLCEViewAggregateOperation.SUM)
                                        .columnName(entityConstantUnallocatedCost)
                                        .build());
      final SelectQuery query =
          viewBillingServiceHelper.getQuery(viewParametersHelper.getModifiedFilters(filters, groupBy, true),
              viewParametersHelper.getTimeTruncGroupBys(groupBy), aggregateFunction, sort, cloudProviderTableName,
              queryParams, Collections.emptyList());
      ResultSet resultSet = null;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        return clickHouseQueryResponseHelper.convertToCostData(resultSet);
      } catch (SQLException e) {
        log.error("Failed to getUnallocatedCostDataNg for query: {}", query, e);
        Thread.currentThread().interrupt();
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return Collections.emptyMap();
  }

  @Override
  public Map<Long, Double> getOthersTotalCostDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewSortCriteria> sort, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    final List<QLCEViewAggregation> aggregateFunction =
        Collections.singletonList(QLCEViewAggregation.builder()
                                      .operationType(QLCEViewAggregateOperation.SUM)
                                      .columnName(entityConstantCost)
                                      .build());
    final SelectQuery query =
        viewBillingServiceHelper.getQuery(viewParametersHelper.getModifiedFilters(filters, groupBy,
                                              viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams)),
            viewParametersHelper.getTimeTruncGroupBys(groupBy), aggregateFunction, sort, cloudProviderTableName,
            queryParams, Collections.emptyList());
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToCostData(resultSet);
    } catch (SQLException e) {
      log.error("Failed to getOthersTotalCostDataNg for query: {}", query, e);
      Thread.currentThread().interrupt();
    } finally {
      DBUtils.close(resultSet);
    }
    return Collections.emptyMap();
  }

  @Override
  public QLCEViewTrendInfo getForecastCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    Instant endInstantForForecastCost = viewsQueryHelper.getEndInstantForForecastCost(filters);
    ViewCostData currentCostData =
        getCostData(viewsQueryHelper.getFiltersForForecastCost(filters), groupBy, aggregateFunction, queryParams);
    Double forecastCost = viewBillingServiceHelper.getForecastCost(currentCostData, endInstantForForecastCost);
    return viewBillingServiceHelper.getForecastCostBillingStats(forecastCost, currentCostData.getCost(),
        viewParametersHelper.getStartInstantForForecastCost(), endInstantForForecastCost.plus(1, ChronoUnit.SECONDS),
        null);
  }

  @Override
  public ViewCostData getCostData(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    return getCostData(filters, null, aggregateFunction, queryParams);
  }

  @Override
  public ViewCostData getCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;
    if (Lists.isNullOrEmpty(groupBy)) {
      Optional<QLCEViewFilterWrapper> viewMetadataFilter = viewParametersHelper.getViewMetadataFilter(filters);
      if (viewMetadataFilter.isPresent()) {
        QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
        final String viewId = metadataFilter.getViewId();
        if (!metadataFilter.isPreview()) {
          CEView view = viewService.get(viewId);
          groupBy = viewsQueryHelper.getDefaultViewGroupBy(view);
        }
      }
    }

    // Group by is only needed in case of business mapping
    if (!viewsQueryHelper.isGroupByBusinessMappingPresent(groupBy)) {
      queryParams = viewsQueryHelper.buildQueryParamsWithSkipGroupBy(queryParams, true);
    }

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, businessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);
    BusinessMapping businessMapping = businessMappingId != null ? businessMappingService.get(businessMappingId) : null;
    boolean addSharedCostFromGroupBy = !businessMappingIds.contains(businessMappingId);
    SelectQuery query = viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
        cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    List<String> fields = getSelectedFields(filters, groupBy, aggregateFunction);
    double sharedCostFromFiltersAndRules = getTotalSharedCostFromFilters(filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    return getViewTrendStatsCostData(
        query, fields, addSharedCostFromGroupBy, businessMapping, sharedCostFromFiltersAndRules);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get total shared cost value
  // ----------------------------------------------------------------------------------------------------------------
  private double getTotalSharedCostFromFilters(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      ViewQueryParams queryParams, List<BusinessMapping> sharedCostBusinessMappings) {
    double totalSharedCost = 0.0;
    List<ViewRule> viewRules = getViewRules(filters);
    if (!sharedCostBusinessMappings.isEmpty()) {
      Map<String, Double> sharedCostsFromRulesAndFilters = getSharedCostFromFilters(filters, groupBy, aggregateFunction,
          sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, MAX_LIMIT_VALUE, 0, false, viewRules);
      if (sharedCostsFromRulesAndFilters != null) {
        for (String entry : sharedCostsFromRulesAndFilters.keySet()) {
          totalSharedCost += sharedCostsFromRulesAndFilters.get(entry);
        }
      }
    }
    return totalSharedCost;
  }

  @Override
  public List<ValueDataPoint> getActualCostGroupedByPeriod(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);

    SelectQuery query = viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
        ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE, queryParams, Collections.emptyList());
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());

      String colName = isClusterTableQuery ? BILLING_AMOUNT : COST;
      List<ValueDataPoint> costs = new ArrayList<>();
      while (resultSet != null && resultSet.next()) {
        ValueDataPoint costData =
            ValueDataPoint.builder()
                .time(clickHouseQueryResponseHelper.fetchTimestampValue(resultSet, TIME_GRANULARITY) / 1000)
                .value(
                    BudgetUtils.getRoundedValue(clickHouseQueryResponseHelper.fetchTimestampValue(resultSet, colName)))
                .build();
        costs.add(costData);
      }
      return costs;
    } catch (SQLException e) {
      log.error("Failed to getActualCostGroupedByPeriod() while running the bugQuery", e);
      Thread.currentThread().interrupt();
    } finally {
      DBUtils.close(resultSet);
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    return viewParametersHelper.isDataGroupedByAwsAccount(filters, groupBy);
  }

  @Override
  public Map<String, Map<String, String>> getLabelsForWorkloads(
      String accountId, Set<String> workloads, List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewFilter> idFilters = filters != null ? viewParametersHelper.getIdFilters(filters) : new ArrayList<>();
    if (!workloads.isEmpty()) {
      idFilters.add(QLCEViewFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId(WORKLOAD_NAME_FIELD_ID)
                                   .fieldName("Workload")
                                   .identifier(CLUSTER)
                                   .build())
                        .operator(IN)
                        .values(workloads.toArray(new String[0]))
                        .build());
    }
    List<QLCEViewTimeFilter> timeFilters =
        filters != null ? viewsQueryHelper.getTimeFilters(filters) : new ArrayList<>();
    SelectQuery query = viewsQueryBuilder.getLabelsForWorkloadsQuery(
        ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE, idFilters, timeFilters);
    return getLabelsForWorkloadsData(query);
  }

  private Map<String, Map<String, String>> getLabelsForWorkloadsData(SelectQuery query) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());

      Map<String, Map<String, String>> workloadToLabelsMapping = new HashMap<>();
      while (resultSet != null && resultSet.next()) {
        String workload = clickHouseQueryResponseHelper.fetchStringValue(resultSet, WORKLOAD_NAME);
        String labelKey = clickHouseQueryResponseHelper.fetchStringValue(resultSet, LABEL_KEY_ALIAS);
        String labelValue = clickHouseQueryResponseHelper.fetchStringValue(resultSet, LABEL_VALUE_ALIAS);
        Map<String, String> labels = new HashMap<>();
        if (workloadToLabelsMapping.containsKey(workload)) {
          labels = workloadToLabelsMapping.get(workload);
        }
        labels.put(labelKey, labelValue);
        workloadToLabelsMapping.put(workload, labels);
      }
      return workloadToLabelsMapping;
    } catch (SQLException e) {
      log.error("Failed to getActualCostGroupedByPeriod() while running the bugQuery", e);
      Thread.currentThread().interrupt();
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  @Override
  public Map<String, Map<Timestamp, Double>> getSharedCostPerTimestampFromFilters(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      ViewQueryParams queryParams, boolean skipRoundOff) {
    String cloudProviderTableName = ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE;

    // Fetching business mapping Ids from filters
    List<ViewRule> viewRules = getViewRules(filters);
    Map<String, Map<Timestamp, Double>> entitySharedCostsPerTimestamp = new HashMap<>();

    String groupByBusinessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    List<String> businessMappingIds = viewParametersHelper.getBusinessMappingIds(filters, groupByBusinessMappingId);
    List<BusinessMapping> sharedCostBusinessMappings =
        viewParametersHelper.getSharedCostBusinessMappings(businessMappingIds);
    Map<String, Map<Timestamp, Double>> sharedCosts = new HashMap<>();

    for (BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      List<QLCEViewGroupBy> updatedGroupBy =
          new ArrayList<>(viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping));
      updatedGroupBy.add(viewsQueryHelper.getGroupByTime(groupBy));
      final boolean groupByCurrentBusinessMapping =
          groupByBusinessMappingId != null && groupByBusinessMappingId.equals(sharedCostBusinessMapping.getUuid());

      SelectQuery query = viewBillingServiceHelper.getQuery(
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid()), groupBy,
          updatedGroupBy, aggregateFunction, sort, cloudProviderTableName, queryParams,
          sharedCostBusinessMappings.get(0), Collections.emptyList());

      List<String> sharedCostBucketNames =
          sharedCostBusinessMapping.getSharedCosts()
              .stream()
              .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
              .collect(Collectors.toList());
      List<String> costTargetBucketNames =
          sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());

      String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
      Map<String, Double> entityCosts = new HashMap<>();
      costTargetBucketNames.forEach(costTarget -> entityCosts.put(costTarget, 0.0));
      double totalCost = 0.0;
      double numberOfEntities = costTargetBucketNames.size();
      Set<Timestamp> timestamps = new HashSet<>();

      ResultSet resultSet = null;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        int totalColumns = clickHouseQueryResponseHelper.getTotalColumnsCount(resultSet);
        while (resultSet != null && resultSet.next()) {
          Timestamp timestamp = null;
          String name = DEFAULT_GRID_ENTRY_NAME;
          Double cost = 0.0;
          Map<String, Double> sharedCostsPerTimestamp = new HashMap<>();
          sharedCostBucketNames.forEach(sharedCostBucketName -> sharedCostsPerTimestamp.put(sharedCostBucketName, 0.0));
          int columnIndex = 1;
          while (columnIndex <= totalColumns) {
            String columnName = resultSet.getMetaData().getColumnName(columnIndex);
            String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
            if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
              name = clickHouseQueryResponseHelper.fetchStringValue(resultSet, columnName, fieldName);
            } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
              if (columnName.equalsIgnoreCase(COST) || columnName.equals(BILLING_AMOUNT)) {
                cost = clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName, skipRoundOff);
              } else if (sharedCostBucketNames.contains(columnName)) {
                sharedCostsPerTimestamp.put(columnName,
                    sharedCostsPerTimestamp.get(columnName)
                        + clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName, skipRoundOff));
              }
            } else if (columnType.toUpperCase(Locale.ROOT).contains(DATETIME)
                || columnType.toUpperCase(Locale.ROOT).contains(DATE)) {
              timestamp = Timestamp.ofTimeMicroseconds(
                  clickHouseQueryResponseHelper.fetchTimestampValue(resultSet, columnName));
              timestamps.add(timestamp);
            }
            columnIndex++;
          }

          if (costTargetBucketNames.contains(name)) {
            totalCost += cost;
            entityCosts.put(name, entityCosts.get(name) + cost);
          }

          for (String sharedCostEntity : sharedCostsPerTimestamp.keySet()) {
            viewBusinessMappingResponseHelper.updateSharedCostMap(
                sharedCosts, sharedCostsPerTimestamp.get(sharedCostEntity), sharedCostEntity, timestamp);
          }
        }
      } catch (SQLException e) {
        log.error("Exception while fetching shared cost per timestamp for query: {}", query, e);
      } finally {
        DBUtils.close(resultSet);
      }

      List<String> selectedCostTargets =
          viewsQueryHelper.getSelectedCostTargetsFromFilters(filters, viewRules, sharedCostBusinessMapping);

      for (String costTarget : costTargetBucketNames) {
        if (selectedCostTargets.contains(costTarget)
            || (groupByCurrentBusinessMapping && selectedCostTargets.isEmpty())) {
          String sharedCostEntryName = groupByCurrentBusinessMapping ? costTarget : fieldName;
          if (!entitySharedCostsPerTimestamp.containsKey(sharedCostEntryName)) {
            entitySharedCostsPerTimestamp.put(sharedCostEntryName, new HashMap<>());
          }
          Map<Timestamp, Double> currentSharedCostsPerTimestamp =
              entitySharedCostsPerTimestamp.get(sharedCostEntryName);

          for (Timestamp timestamp : timestamps) {
            if (!currentSharedCostsPerTimestamp.containsKey(timestamp)) {
              currentSharedCostsPerTimestamp.put(timestamp, 0.0);
            }
            currentSharedCostsPerTimestamp.put(timestamp,
                currentSharedCostsPerTimestamp.get(timestamp)
                    + viewBusinessMappingResponseHelper.calculateSharedCostForTimestamp(sharedCosts, timestamp,
                        sharedCostBusinessMapping, costTarget, entityCosts.get(costTarget), numberOfEntities,
                        totalCost));
          }
          entitySharedCostsPerTimestamp.put(sharedCostEntryName, currentSharedCostsPerTimestamp);
        }
      }
    }

    return entitySharedCostsPerTimestamp;
  }

  @Override
  public List<ViewRule> getViewRules(List<QLCEViewFilterWrapper> filters) {
    return viewParametersHelper.getViewRules(filters);
  }

  private List<String> getSelectedFields(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations) {
    List<String> fields = new ArrayList<>();
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null) {
        fields.add(getAliasedColumnNameFromGroupBy(groupBy.getEntityGroupBy()));
      }
    });
    aggregations.forEach(aggregation -> fields.add(getAliasedColumnNameFromAggregation(aggregation)));
    return fields;
  }

  private String getAliasedColumnNameFromGroupBy(QLCEViewFieldInput groupBy) {
    if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM && groupBy.getIdentifier() != BUSINESS_MAPPING
        && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
      return groupBy.getFieldId();
    } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
      return ViewsMetaDataFields.LABEL_VALUE.getAlias();
    } else {
      // Will handle both Custom and Business Mapping Cases
      return viewsQueryBuilder.modifyStringToComplyRegex(viewsQueryBuilder.getColumnName(groupBy.getFieldName()));
    }
  }

  private String getAliasedColumnNameFromAggregation(QLCEViewAggregation aggregation) {
    if (aggregation.getColumnName().equals(ViewsMetaDataFields.START_TIME.getFieldName())) {
      return String.format(ViewsQueryBuilder.aliasStartTimeMaxMin, ViewsMetaDataFields.START_TIME.getFieldName(),
          aggregation.getOperationType());
    } else {
      return viewsQueryBuilder.getAliasNameForAggregation(aggregation.getColumnName());
    }
  }
}
