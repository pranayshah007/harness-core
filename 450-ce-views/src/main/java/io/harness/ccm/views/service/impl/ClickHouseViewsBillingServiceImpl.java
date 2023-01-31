/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantUnallocatedCost;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;

import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    String cloudProviderTableName = "";
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
    boolean isClusterPerspective = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    String cloudProviderTableName = "";
    if (isClusterPerspective) {
      cloudProviderTableName = "ccm.clusterData";
    }
    log.info("Cloud provider table name: {}", cloudProviderTableName);
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
    log.info("Fields : {}", fields);
    log.info("Cloud provider table name: {}", cloudProviderTableName);
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
          clickHouseQueryResponseHelper.convertToEntityStatsData(resultSet, fields, costTrendData,
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
    if (isClusterTableQuery) {
      cloudProviderTableName = "ccm.clusterData";
    }
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
          resultSet, fields, isClusterTableQuery, queryParams.isSkipRoundOff(), groupBy);
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
    String cloudProviderTableName = "";
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
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
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
    String cloudProviderTableName = "";
    QLCEViewGridData gridData = null;
    List<QLCEViewGroupBy> groupByExcludingGroupByTime =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    ViewQueryParams queryParamsForGrid =
        viewsQueryHelper.buildQueryParams(queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
    gridData = getEntityStatsDataPointsNg(
        filters, groupByExcludingGroupByTime, aggregateFunction, sort, limit, 0, queryParamsForGrid);

    SelectQuery query = viewBillingServiceHelper.getQuery(
        viewBillingServiceHelper.getModifiedFiltersForTimeSeriesStats(filters, null, groupByExcludingGroupByTime),
        groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
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
    String cloudProviderTableName = "";
    List<ViewRule> viewRuleList = new ArrayList<>();
    List<QLCEViewFilter> idFilters = AwsAccountFieldHelper.removeAccountNameFromAWSAccountIdFilter(
        viewParametersHelper.getModifiedIdFilters(viewParametersHelper.getIdFilters(filters), false));
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);
    SelectQuery query = viewBillingServiceHelper.getTrendStatsQuery(
        filters, idFilters, timeFilters, groupBy, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    List<QLCEViewTimeFilter> trendTimeFilters = viewsQueryHelper.getTrendFilters(timeFilters);
    SelectQuery prevTrendStatsQuery = viewBillingServiceHelper.getTrendStatsQuery(filters, idFilters, trendTimeFilters,
        groupBy, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    Instant trendStartInstant =
        Instant.ofEpochMilli(viewsQueryHelper.getTimeFilter(trendTimeFilters, AFTER).getValue().longValue());

    ViewCostData costData = getViewTrendStatsCostData(query);
    ViewCostData prevCostData = getViewTrendStatsCostData(prevTrendStatsQuery);

    return QLCEViewTrendData.builder()
        .totalCost(
            viewBillingServiceHelper.getCostBillingStats(costData, prevCostData, timeFilters, trendStartInstant, false))
        .build();
  }

  private ViewCostData getViewTrendStatsCostData(SelectQuery query) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();

    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryConfig.getQuery());
      log.info("Resultset: {}", resultSet);
      return clickHouseQueryResponseHelper.convertToTrendStatsData(resultSet);
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
    String cloudProviderTableName = "";
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
    String cloudProviderTableName = "";
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
    String cloudProviderTableName = "";
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
    return null;
  }

  @Override
  public ViewCostData getCostData(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    return null;
  }

  @Override
  public ViewCostData getCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    return null;
  }

  @Override
  public List<ValueDataPoint> getActualCostGroupedByPeriod(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    return null;
  }

  @Override
  public boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    return viewParametersHelper.isDataGroupedByAwsAccount(filters, groupBy);
  }

  @Override
  public Map<String, Map<String, String>> getLabelsForWorkloads(
      String accountId, Set<String> workloads, List<QLCEViewFilterWrapper> filters) {
    return null;
  }

  @Override
  public Map<String, Map<Timestamp, Double>> getSharedCostPerTimestampFromFilters(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      ViewQueryParams queryParams, boolean skipRoundOff) {
    return null;
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
