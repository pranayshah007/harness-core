/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.ccm.commons.constants.DataTypeConstants.DATE;
import static io.harness.ccm.commons.constants.DataTypeConstants.DATETIME;
import static io.harness.ccm.commons.constants.DataTypeConstants.FLOAT64;
import static io.harness.ccm.commons.constants.DataTypeConstants.STRING;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
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
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_GRANULARITY;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;

import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.EfficiencyScoreStats;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsMetaDataFields;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.ccm.views.helper.ClickHouseQueryResponseHelper;
import io.harness.ccm.views.helper.InstanceDetailsHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewBusinessMappingResponseHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.timescaledb.DBUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClickHouseViewsBillingServiceImpl implements ViewsBillingService {
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  @Inject ClickHouseService clickHouseService;
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

  private static final String IDLE_COST_LABEL = "Idle Cost";
  private static final String UNALLOCATED_COST_LABEL = "Unallocated Cost";
  private static final String UTILIZED_COST_LABEL = "Utilized Cost";
  private static final String SYSTEM_COST_LABEL = "System Cost";
  private static final String CLICKHOUSE_UNIFIED_TABLE = "ccm.unifiedTable";
  private static final String CLICKHOUSE_CLUSTER_DATA_TABLE = "ccm.clusterData";
  private static final int MAX_LIMIT_VALUE = 10_000;

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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    if (businessMappingId != null) {
      return businessMappingService.getCostTargetNames(businessMappingId, queryParams.getAccountId(),
          viewsQueryHelper.getSearchValueFromBusinessMappingFilter(filters, businessMappingId));
    }

    List<QLCEViewFilter> idFilters = awsAccountFieldHelper.addAccountIdsByAwsAccountNameFilter(
        viewParametersHelper.getIdFilters(filters), queryParams.getAccountId());

    // Get the query
    ViewsQueryMetadata viewsQueryMetadata =
        viewBillingServiceHelper.getFilterValueStatsQuery(filters, cloudProviderTableName, limit, offset, queryParams);

    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(viewsQueryMetadata.getQuery().toString()).build();
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryConfig.getQuery());
      return clickHouseQueryResponseHelper.getFilterValuesData(queryParams.getAccountId(), viewsQueryMetadata,
          resultSet, idFilters, cloudProviderTableName.contains(CLUSTER_TABLE));
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
    log.info("GridQueryLog: Starts");
    boolean isClusterPerspective = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    if (isClusterPerspective) {
      cloudProviderTableName = CLICKHOUSE_CLUSTER_DATA_TABLE;
    }
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    BusinessMapping businessMapping = businessMappingId != null ? businessMappingService.get(businessMappingId) : null;
    boolean addSharedCostFromGroupBy = true;

    // Fetching business mapping Ids from rules and filters
    List<ViewRule> viewRules = getViewRules(filters);
    Set<String> businessMappingIdsFromRules = viewsQueryHelper.getBusinessMappingIdsFromViewRules(viewRules);
    List<String> businessMappingIdsFromRulesAndFilters = viewsQueryHelper.getBusinessMappingIdsFromFilters(filters);
    businessMappingIdsFromRulesAndFilters.addAll(businessMappingIdsFromRules);
    List<BusinessMapping> sharedCostBusinessMappings = new ArrayList<>();
    Map<String, Double> sharedCostsFromRulesAndFilters = new HashMap<>();
    if (!businessMappingIdsFromRulesAndFilters.isEmpty()) {
      businessMappingIdsFromRulesAndFilters.forEach(businessMappingIdFromRulesAndFilters -> {
        BusinessMapping businessMappingFromRulesAndFilters =
            businessMappingService.get(businessMappingIdFromRulesAndFilters);
        if (businessMappingFromRulesAndFilters != null && businessMappingFromRulesAndFilters.getSharedCosts() != null) {
          sharedCostBusinessMappings.add(businessMappingFromRulesAndFilters);
        }
      });
    }

    // Conversion field is not null in case entity id to name conversion is required for a field
    String conversionField = null;
    if (isDataGroupedByAwsAccount(filters, groupBy) && !queryParams.isUsedByTimeSeriesStats()) {
      conversionField = AWS_ACCOUNT_FIELD;
    }

    Map<String, ViewCostData> costTrendData = new HashMap<>();
    long startTimeForTrendData = 0L;
    if (!queryParams.isUsedByTimeSeriesStats()) {
      costTrendData = getEntityStatsDataForCostTrend(
          filters, groupBy, aggregateFunction, sort, cloudProviderTableName, limit, offset, queryParams);
      startTimeForTrendData = viewParametersHelper.getStartTimeForTrendFilters(filters);
    }
    List<String> fields = getSelectedFields(filters, groupBy, aggregateFunction);
    log.info("GridQueryLog: Fields{}", fields);
    SelectQuery query = viewBillingServiceHelper.getQuery(
        filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    log.info("Query for grid (with limit as {}): {}", limit, query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      if (!sharedCostBusinessMappings.isEmpty()) {
        sharedCostsFromRulesAndFilters =
            getSharedCostFromFilters(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams,
                sharedCostBusinessMappings, limit, offset, queryParams.isSkipRoundOff(), viewRules);
      }

      addSharedCostFromGroupBy = !businessMappingIdsFromRulesAndFilters.contains(businessMappingId);
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

  private Map<String, ViewCostData> getEntityStatsDataForCostTrend(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, Integer limit, Integer offset, ViewQueryParams queryParams) {
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    cloudProviderTableName = viewBillingServiceHelper.getUpdatedCloudProviderTableName(
        filters, groupBy, aggregateFunction, queryParams.getAccountId(), cloudProviderTableName, isClusterTableQuery);
    List<QLCEViewAggregation> aggregationsForCostTrend =
        viewParametersHelper.getAggregationsForEntityStatsCostTrend(aggregateFunction);
    List<QLCEViewFilterWrapper> filtersForCostTrend = viewParametersHelper.getFiltersForEntityStatsCostTrend(filters);
    List<String> fields = getSelectedFields(filtersForCostTrend, groupBy, aggregationsForCostTrend);
    SelectQuery query = viewBillingServiceHelper.getQuery(
        filtersForCostTrend, groupBy, aggregationsForCostTrend, sort, cloudProviderTableName, queryParams);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    log.info("Query for cost trend (with limit as {}): {}", limit, query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToEntityStatsCostTrendData(
          resultSet, isClusterTableQuery, queryParams.isSkipRoundOff(), groupBy);
    } catch (SQLException e) {
      log.error("Failed to getEntityStatsDataForCostTrend. {}", e.toString());
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
      SelectQuery query = viewBillingServiceHelper.getQuery(
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid()),
          businessMappingGroupBy, aggregateFunction, sort, cloudProviderTableName, queryParams,
          sharedCostBusinessMapping);
      query.addCustomization(new PgLimitClause(limit));
      query.addCustomization(new PgOffsetClause(offset));
      ResultSet resultSet;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        sharedCostsFromFilters =
            clickHouseQueryResponseHelper.convertToSharedCostFromFiltersData(resultSet, filters, groupBy,
                sharedCostBusinessMapping, groupByBusinessMappingId, sharedCostsFromFilters, skipRoundOff, viewRules);
      } catch (SQLException e) {
        log.error("Failed to getSharedCostFromFilters.", e);
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    QLCEViewGridData gridData = null;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    SelectQuery query = viewBillingServiceHelper.getQuery(
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, gridData, groupByExcludingGroupByTime),
        groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());
    } catch (SQLException e) {
      log.error("Failed to getTimeSeriesStatsNg. {}", e.toString());
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    QLCEViewGridData gridData = null;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    SelectQuery query = viewBillingServiceHelper.getQuery(
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, gridData, groupByExcludingGroupByTime),
        groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      log.info("ChartQueryLog query: {}", query.toString());
      resultSet = statement.executeQuery(query.toString());
      return clickHouseQueryResponseHelper.convertToTimeSeriesData(resultSet, timePeriod, conversionField,
          businessMappingId, queryParams.getAccountId(), groupBy, sharedCostFromFilters, addSharedCostFromGroupBy);
    } catch (SQLException e) {
      log.error("Failed to getTimeSeriesStatsNg. {}", e.toString());
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    cloudProviderTableName = viewBillingServiceHelper.getUpdatedCloudProviderTableName(
        filters, null, aggregateFunction, "", cloudProviderTableName, queryParams.isClusterQuery());
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    List<ViewRule> viewRuleList = new ArrayList<>();
    List<QLCEViewFilter> idFilters = AwsAccountFieldHelper.removeAccountNameFromAWSAccountIdFilter(
        viewParametersHelper.getModifiedIdFilters(viewParametersHelper.getIdFilters(filters), isClusterTableQuery));
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);
    SelectQuery query = viewBillingServiceHelper.getTrendStatsQuery(
        filters, idFilters, timeFilters, groupBy, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    List<QLCEViewTimeFilter> trendTimeFilters = viewsQueryHelper.getTrendFilters(timeFilters);
    log.info("Trend time filters: {}", trendTimeFilters);
    SelectQuery prevTrendStatsQuery = viewBillingServiceHelper.getTrendStatsQuery(filters, idFilters, trendTimeFilters,
        groupBy, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    Instant trendStartInstant =
        Instant.ofEpochMilli(viewsQueryHelper.getTimeFilter(trendTimeFilters, AFTER).getValue().longValue());

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    List<String> businessMappingIdsFromRulesAndFilters =
        viewParametersHelper.getBusinessMappingIdsFromRulesAndFilters(filters);
    BusinessMapping businessMapping =
        businessMappingId != null && !businessMappingIdsFromRulesAndFilters.contains(businessMappingId)
        ? businessMappingService.get(businessMappingId)
        : null;

    double sharedCostFromRulesAndFilters = getTotalSharedCostFromFilters(filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, MAX_LIMIT_VALUE, 0, false);
    double prevSharedCostFromRulesAndFilters =
        getTotalSharedCostFromFilters(viewsQueryHelper.getUpdatedFiltersForPrevPeriod(filters), groupBy,
            aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, MAX_LIMIT_VALUE, 0, false);

    List<String> fields = getSelectedFields(filters, groupBy, aggregateFunction);
    ViewCostData costData =
        getViewTrendStatsCostData(query, fields, isClusterTableQuery, businessMapping, sharedCostFromRulesAndFilters);
    ViewCostData prevCostData = getViewTrendStatsCostData(
        prevTrendStatsQuery, fields, isClusterTableQuery, businessMapping, prevSharedCostFromRulesAndFilters);

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

  private ViewCostData getViewTrendStatsCostData(SelectQuery query, List<String> fields, boolean isClusterTableQuery,
      BusinessMapping businessMappingFromGroupBy, double sharedCostFromFiltersAndRules) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();

    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryConfig.getQuery());
      return clickHouseQueryResponseHelper.convertToTrendStatsData(
          resultSet, fields, isClusterTableQuery, businessMappingFromGroupBy, sharedCostFromFiltersAndRules);
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    SelectQuery query = viewBillingServiceHelper.getQuery(
        filters, groupBy, Collections.EMPTY_LIST, Collections.emptyList(), cloudProviderTableName, queryParams);
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

  @Override
  public Map<Long, Double> getUnallocatedCostDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewSortCriteria> sort, ViewQueryParams queryParams) {
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
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
              queryParams);
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    final List<QLCEViewAggregation> aggregateFunction =
        Collections.singletonList(QLCEViewAggregation.builder()
                                      .operationType(QLCEViewAggregateOperation.SUM)
                                      .columnName(entityConstantCost)
                                      .build());
    final SelectQuery query =
        viewBillingServiceHelper.getQuery(viewParametersHelper.getModifiedFilters(filters, groupBy,
                                              viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams)),
            viewParametersHelper.getTimeTruncGroupBys(groupBy), aggregateFunction, sort, cloudProviderTableName,
            queryParams);
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    List<ViewRule> viewRuleList = new ArrayList<>();
    List<QLCEViewFilter> idFilters =
        viewParametersHelper.getModifiedIdFilters(viewParametersHelper.getIdFilters(filters), isClusterTableQuery);
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);
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
      groupBy = Collections.emptyList();
    }

    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);
    List<String> businessMappingIdsFromRulesAndFilters =
        viewParametersHelper.getBusinessMappingIdsFromRulesAndFilters(filters);
    BusinessMapping businessMapping =
        businessMappingId != null && !businessMappingIdsFromRulesAndFilters.contains(businessMappingId)
        ? businessMappingService.get(businessMappingId)
        : null;
    SelectQuery query = viewBillingServiceHelper.getTrendStatsQuery(
        filters, idFilters, timeFilters, groupBy, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);
    List<String> fields = getSelectedFields(filters, groupBy, aggregateFunction);
    double sharedCostFromFiltersAndRules = getTotalSharedCostFromFilters(filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, MAX_LIMIT_VALUE, 0, false);
    return getViewTrendStatsCostData(
        query, fields, isClusterTableQuery, businessMapping, sharedCostFromFiltersAndRules);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get total shared cost value
  // ----------------------------------------------------------------------------------------------------------------
  private double getTotalSharedCostFromFilters(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      ViewQueryParams queryParams, Integer limit, Integer offset, boolean skipRoundOff) {
    double totalSharedCost = 0.0;
    List<ViewRule> viewRules = getViewRules(filters);
    Set<String> businessMappingIdsFromRules = viewsQueryHelper.getBusinessMappingIdsFromViewRules(viewRules);
    List<String> businessMappingIdsFromRulesAndFilters = viewsQueryHelper.getBusinessMappingIdsFromFilters(filters);
    businessMappingIdsFromRulesAndFilters.addAll(businessMappingIdsFromRules);
    List<BusinessMapping> sharedCostBusinessMappings = new ArrayList<>();
    Map<String, Double> sharedCostsFromRulesAndFilters = new HashMap<>();
    if (!businessMappingIdsFromRulesAndFilters.isEmpty()) {
      businessMappingIdsFromRulesAndFilters.forEach(businessMappingIdFromRulesAndFilters -> {
        BusinessMapping businessMappingFromRulesAndFilters =
            businessMappingService.get(businessMappingIdFromRulesAndFilters);
        if (businessMappingFromRulesAndFilters != null && businessMappingFromRulesAndFilters.getSharedCosts() != null) {
          sharedCostBusinessMappings.add(businessMappingFromRulesAndFilters);
        }
      });
    }
    if (!sharedCostBusinessMappings.isEmpty()) {
      sharedCostsFromRulesAndFilters = getSharedCostFromFilters(filters, groupBy, aggregateFunction, sort,
          cloudProviderTableName, queryParams, sharedCostBusinessMappings, limit, offset, skipRoundOff, viewRules);
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
    List<QLCEViewFilter> idFilters =
        viewParametersHelper.getModifiedIdFilters(viewParametersHelper.getIdFilters(filters), isClusterTableQuery);
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);

    SelectQuery query = viewBillingServiceHelper.getTrendStatsQuery(filters, idFilters, timeFilters, groupBy,
        aggregateFunction, new ArrayList<>(), CLICKHOUSE_UNIFIED_TABLE, queryParams);
    log.info("getActualCostGroupedByPeriod() query formed: " + query.toString());
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
    SelectQuery query = viewsQueryBuilder.getLabelsForWorkloadsQuery(CLICKHOUSE_UNIFIED_TABLE, idFilters, timeFilters);
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
    String cloudProviderTableName = CLICKHOUSE_UNIFIED_TABLE;

    // Fetching business mapping Ids from filters
    List<ViewRule> viewRules = getViewRules(filters);
    Map<String, Map<Timestamp, Double>> entitySharedCostsPerTimestamp = new HashMap<>();

    Set<String> businessMappingIdsFromRules = viewsQueryHelper.getBusinessMappingIdsFromViewRules(viewRules);
    List<String> businessMappingIdsFromRulesAndFilters = viewsQueryHelper.getBusinessMappingIdsFromFilters(filters);
    businessMappingIdsFromRulesAndFilters.addAll(businessMappingIdsFromRules);
    List<BusinessMapping> sharedCostBusinessMappings = new ArrayList<>();
    Map<String, Map<Timestamp, Double>> sharedCosts = new HashMap<>();
    if (!businessMappingIdsFromRulesAndFilters.isEmpty()) {
      businessMappingIdsFromRulesAndFilters.forEach(businessMappingIdFromRulesAndFilters -> {
        BusinessMapping businessMappingFromRulesAndFilters =
            businessMappingService.get(businessMappingIdFromRulesAndFilters);
        if (businessMappingFromRulesAndFilters != null && businessMappingFromRulesAndFilters.getSharedCosts() != null) {
          sharedCostBusinessMappings.add(businessMappingFromRulesAndFilters);
        }
      });
    }

    String groupByBusinessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    for (BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      List<QLCEViewGroupBy> updatedGroupBy =
          new ArrayList<>(viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping));
      updatedGroupBy.add(viewsQueryHelper.getGroupByTime(groupBy));
      final boolean groupByCurrentBusinessMapping =
          groupByBusinessMappingId != null && groupByBusinessMappingId.equals(sharedCostBusinessMapping.getUuid());

      SelectQuery query =
          viewBillingServiceHelper.getQuery(viewsQueryHelper.removeBusinessMappingFilters(filters), updatedGroupBy,
              aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings.get(0));
      ResultSet result;
      ResultSet resultSet = null;
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
      } catch (SQLException e) {
        log.error("Failed to getOthersTotalCostDataNg for query: {}", query, e);
        Thread.currentThread().interrupt();
        return null;
      } finally {
        DBUtils.close(resultSet);
      }

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
      int totalColumns = clickHouseQueryResponseHelper.getTotalColumnsCount(resultSet);
      try {
        while (resultSet != null && resultSet.next()) {
          Timestamp timestamp = null;
          String name = DEFAULT_GRID_ENTRY_NAME;
          String id = DEFAULT_GRID_ENTRY_NAME;
          Double cost = 0.0;
          Map<String, Double> sharedCostsPerTimestamp = new HashMap<>();
          sharedCostBucketNames.forEach(sharedCostBucketName -> sharedCostsPerTimestamp.put(sharedCostBucketName, 0.0));
          int columnIndex = 1;
          while (columnIndex <= totalColumns) {
            String columnName = resultSet.getMetaData().getColumnName(columnIndex);
            String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
            log.info("GridQueryLog: Column name {} , columnType {}", columnName, columnType);
            if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
              name = clickHouseQueryResponseHelper.fetchStringValue(resultSet, columnName, fieldName);
            } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
              if (columnName.equalsIgnoreCase(COST)) {
                cost = clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName, skipRoundOff);
                log.info("GridQueryLog: cost {}", cost);
              } else if (sharedCostBucketNames.contains(columnName)) {
                if (sharedCostBucketNames.contains(columnName)) {
                  sharedCostsPerTimestamp.put(columnName,
                      sharedCostsPerTimestamp.get(columnName)
                          + clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName, skipRoundOff));
                }
              }
            } else if (columnType.toUpperCase(Locale.ROOT).contains(DATETIME)
                || columnType.toUpperCase(Locale.ROOT).contains(DATE)) {
              timestamp = Timestamp.ofTimeMicroseconds(
                  clickHouseQueryResponseHelper.fetchTimestampValue(resultSet, columnName));
              timestamps.add(timestamp);
              break;
            }
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
        log.info("Exception while fetching shared cost per timestamp: {}", e.toString());
      }

      List<QLCEViewFilterWrapper> businessMappingFilters =
          viewsQueryHelper.getBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid());
      List<String> selectedCostTargets = new ArrayList<>();
      for (QLCEViewFilterWrapper businessMappingFilter : businessMappingFilters) {
        if (!selectedCostTargets.isEmpty()) {
          selectedCostTargets = viewsQueryHelper.intersection(
              selectedCostTargets, Arrays.asList(businessMappingFilter.getIdFilter().getValues()));
        } else {
          selectedCostTargets.addAll(Arrays.asList(businessMappingFilter.getIdFilter().getValues()));
        }
      }
      selectedCostTargets = viewsQueryHelper.intersection(selectedCostTargets,
          viewsQueryHelper.getSelectedCostTargetsFromViewRules(viewRules, sharedCostBusinessMapping.getUuid()));

      for (String costTarget : costTargetBucketNames) {
        if (selectedCostTargets.contains(costTarget)) {
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
                        sharedCostBusinessMapping, entityCosts.get(costTarget), numberOfEntities, totalCost));
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
