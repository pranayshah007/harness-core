/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.beans.FeatureName.CE_BILLING_DATA_PRE_AGGREGATION;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLOUD_SERVICE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.LAUNCH_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NAMESPACE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.TASK_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.LABEL;
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.MAX;
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.MIN;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantClusterCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantIdleCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMaxStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMinStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantSystemCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantUnallocatedCost;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_NODE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_POD;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_POD_FARGATE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_PV;
import static io.harness.ccm.views.utils.ClusterTableKeys.ACTUAL_IDLE_COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_PROVIDER;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_SERVICE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_AGGREGRATED;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY_AGGREGRATED;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.COUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_STRING_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLOUD_PROVIDER;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLUSTER_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_CLUSTER_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_LAUNCH_TYPE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_SERVICE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_SERVICE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_TASK;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_TASK_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NAMESPACE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NAMESPACE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NODE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_POD;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_PRODUCT;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_STORAGE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_WORKLOAD_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.ID_SEPARATOR;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.NAMESPACE;
import static io.harness.ccm.views.utils.ClusterTableKeys.PARENT_INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.PRICING_SOURCE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TASK_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_TYPE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ClusterData;
import io.harness.ccm.views.entities.ClusterData.ClusterDataBuilder;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.EfficiencyScoreStats;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewDataPoint;
import io.harness.ccm.views.graphql.QLCEViewDataPoint.QLCEViewDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewSortType;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewCostData.ViewCostDataBuilder;
import io.harness.ccm.views.graphql.ViewsMetaDataFields;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.InstanceDetailsHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import com.sun.istack.internal.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ViewsBillingServiceImpl implements ViewsBillingService {
  @Inject ViewsQueryBuilder viewsQueryBuilder;
  @Inject CEViewService viewService;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject FeatureFlagService featureFlagService;
  @Inject InstanceDetailsHelper instanceDetailsHelper;
  @Inject EntityMetadataService entityMetadataService;
  @Inject BusinessMappingService businessMappingService;
  @Inject AwsAccountFieldHelper awsAccountFieldHelper;

  private static final String OTHERS = "Others";
  private static final String COST_DESCRIPTION = "of %s - %s";
  private static final String OTHER_COST_DESCRIPTION = "%s of total";
  private static final String COST_VALUE = "$%s";
  private static final String TOTAL_COST_LABEL = "Total Cost";
  private static final String FORECAST_COST_LABEL = "Forecasted Cost";
  private static final String IDLE_COST_LABEL = "Idle Cost";
  private static final String UNALLOCATED_COST_LABEL = "Unallocated Cost";
  private static final String UTILIZED_COST_LABEL = "Utilized Cost";
  private static final String SYSTEM_COST_LABEL = "System Cost";
  private static final String EMPTY_VALUE = "-";
  private static final String DATE_PATTERN_FOR_CHART = "MMM dd";
  private static final String STANDARD_TIME_ZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000L;
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;
  private static final List<String> UNALLOCATED_COST_CLUSTER_FIELDS = ImmutableList.of(
      NAMESPACE_FIELD_ID, WORKLOAD_NAME_FIELD_ID, CLOUD_SERVICE_NAME_FIELD_ID, TASK_FIELD_ID, LAUNCH_TYPE_FIELD_ID);

  @Override
  public List<String> getFilterValueStats(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      String cloudProviderTableName, Integer limit, Integer offset) {
    return getFilterValueStatsNg(
        bigQuery, filters, cloudProviderTableName, limit, offset, viewsQueryHelper.buildQueryParams(null, false));
  }

  @Override
  public List<String> getFilterValueStatsNg(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      String cloudProviderTableName, Integer limit, Integer offset, ViewQueryParams queryParams) {
    boolean isClusterQuery = queryParams.isClusterQuery();
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromFilters(filters);

    // If filter values of business mapping are requested, query unified table
    isClusterQuery = isClusterQuery && businessMappingId == null;

    List<ViewRule> viewRuleList = new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    List<QLCEViewFilter> idFilters = getIdFilters(filters);

    List<QLCEViewRule> rules = getRuleFilters(filters);
    if (!rules.isEmpty()) {
      for (QLCEViewRule rule : rules) {
        viewRuleList.add(convertQLCEViewRuleToViewRule(rule));
      }
    }

    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
      }
    }

    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      cloudProviderTableName = getUpdatedCloudProviderTableName(
          filters, null, null, queryParams.getAccountId(), cloudProviderTableName, isClusterQuery);
    }

    ViewsQueryMetadata viewsQueryMetadata = viewsQueryBuilder.getFilterValuesQuery(
        viewRuleList, idFilters, getTimeFilters(filters), cloudProviderTableName, limit, offset);
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(viewsQueryMetadata.getQuery().toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getViewFilterValueStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return costCategoriesPostFetchResponseUpdate(
        getFilterValuesData(queryParams.getAccountId(), viewsQueryMetadata, result, idFilters), businessMappingId);
  }

  private List<String> getFilterValuesData(final String harnessAccountId, final ViewsQueryMetadata viewsQueryMetadata,
      final TableResult result, final List<QLCEViewFilter> idFilters) {
    List<String> filterValuesData = convertToFilterValuesData(result, viewsQueryMetadata.getFields());
    if (isDataFilteredByAwsAccount(idFilters)) {
      filterValuesData = awsAccountFieldHelper.mergeAwsAccountNameWithValues(filterValuesData, harnessAccountId);
    }
    return filterValuesData;
  }

  private boolean isDataFilteredByAwsAccount(final List<QLCEViewFilter> idFilters) {
    return idFilters.stream()
        .filter(idFilter -> Objects.nonNull(idFilter) && Objects.nonNull(idFilter.getField()))
        .anyMatch(idFilter -> AWS_ACCOUNT_FIELD.equals(idFilter.getField().getFieldName()));
  }

  @Override
  public List<QLCEViewEntityStatsDataPoint> getEntityStatsDataPoints(BigQuery bigQuery,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewSortCriteria> sort, String cloudProviderTableName, Integer limit, Integer offset) {
    ViewQueryParams queryParams = viewsQueryHelper.buildQueryParams(null, false, false, false, false);
    // account id is not required for query builder of current-gen, therefore is passed null
    return getEntityStatsDataPointsNg(
        bigQuery, filters, groupBy, aggregateFunction, sort, cloudProviderTableName, limit, offset, queryParams)
        .getData();
  }

  @Override
  public QLCEViewGridData getEntityStatsDataPointsNg(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, Integer limit, Integer offset, ViewQueryParams queryParams) {
    boolean isClusterPerspective = isClusterTableQuery(filters, queryParams);
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    // If group by business mapping is present, query unified table
    isClusterPerspective = isClusterPerspective && businessMappingId == null;

    // Conversion field is not null in case entity id to name conversion is required for a field
    String conversionField = null;
    if (isDataGroupedByAwsAccount(filters, groupBy) && !queryParams.isUsedByTimeSeriesStats()) {
      conversionField = AWS_ACCOUNT_FIELD;
    }
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    long startTimeForTrendData = 0L;
    if (!queryParams.isUsedByTimeSeriesStats()) {
      costTrendData = getEntityStatsDataForCostTrend(
          bigQuery, filters, groupBy, aggregateFunction, sort, cloudProviderTableName, limit, offset, queryParams);
      startTimeForTrendData = getStartTimeForTrendFilters(filters);
    }
    SelectQuery query = getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getEntityStatsDataPoints. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return costCategoriesPostFetchResponseUpdate(
        convertToEntityStatsData(result, costTrendData, startTimeForTrendData, isClusterPerspective,
            queryParams.isUsedByTimeSeriesStats(), queryParams.isSkipRoundOff(), conversionField,
            queryParams.getAccountId()),
        businessMappingId);
  }

  @Override
  public TableResult getTimeSeriesStats(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName) {
    SelectQuery query = getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, true);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      return bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTimeSeriesStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  @Override
  public TableResult getTimeSeriesStatsNg(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, boolean includeOthers, Integer limit, ViewQueryParams queryParams) {
    if (!includeOthers && !isMetricsQuery(aggregateFunction)) {
      ViewQueryParams queryParamsForGrid = viewsQueryHelper.buildQueryParams(
          queryParams.getAccountId(), false, true, queryParams.isClusterQuery(), false);
      List<QLCEViewGroupBy> groupByExcludingGroupByTime =
          groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());
      QLCEViewGridData gridData = getEntityStatsDataPointsNg(bigQuery, filters, groupByExcludingGroupByTime,
          aggregateFunction, sort, cloudProviderTableName, limit, 0, queryParamsForGrid);
      log.info("GRID DATA: {}", gridData);
      filters = getModifiedFilters(filters, gridData);
    }

    SelectQuery query = getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      return bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTimeSeriesStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  @Override
  public Map<Long, Double> getUnallocatedCostDataNg(final BigQuery bigQuery, final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewSortCriteria> sort, final String cloudProviderTableName,
      final ViewQueryParams queryParams) {
    if (shouldShowUnallocatedCost(groupBy) && isClusterTableQuery(filters, queryParams)) {
      final List<QLCEViewAggregation> aggregateFunction =
          Collections.singletonList(QLCEViewAggregation.builder()
                                        .operationType(QLCEViewAggregateOperation.SUM)
                                        .columnName(entityConstantUnallocatedCost)
                                        .build());
      final SelectQuery query = getQuery(filters, modifyGroupByForUnallocatedCostData(groupBy), aggregateFunction, sort,
          cloudProviderTableName, queryParams);
      final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
      try {
        return convertToUnallocatedCostData(bigQuery.query(queryConfig));
      } catch (final InterruptedException e) {
        log.error("Failed to getUnallocatedCostDataNg.", e);
        Thread.currentThread().interrupt();
      }
    }
    return null;
  }

  @Override
  public QLCEViewTrendInfo getTrendStatsData(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewAggregation> aggregateFunction, String cloudProviderTableName) {
    return getTrendStatsDataNg(
        bigQuery, filters, aggregateFunction, cloudProviderTableName, viewsQueryHelper.buildQueryParams(null, false))
        .getTotalCost();
  }

  @Override
  public QLCEViewTrendData getTrendStatsDataNg(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewAggregation> aggregateFunction, String cloudProviderTableName, ViewQueryParams queryParams) {
    boolean isClusterTableQuery = isClusterTableQuery(filters, queryParams);
    List<ViewRule> viewRuleList = new ArrayList<>();
    List<QLCEViewFilter> idFilters = removeAccountNameFromAWSAccountIdFilter(getIdFilters(filters));
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);
    SelectQuery query = getTrendStatsQuery(
        filters, idFilters, timeFilters, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    List<QLCEViewTimeFilter> trendTimeFilters = getTrendFilters(timeFilters);
    SelectQuery prevTrendStatsQuery = getTrendStatsQuery(
        filters, idFilters, trendTimeFilters, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);

    Instant trendStartInstant = Instant.ofEpochMilli(getTimeFilter(trendTimeFilters, AFTER).getValue().longValue());

    ViewCostData costData = getViewTrendStatsCostData(bigQuery, query, isClusterTableQuery);
    ViewCostData prevCostData = getViewTrendStatsCostData(bigQuery, prevTrendStatsQuery, isClusterTableQuery);

    EfficiencyScoreStats efficiencyScoreStats = null;
    if (isClusterTableQuery) {
      efficiencyScoreStats = viewsQueryHelper.getEfficiencyScoreStats(costData, prevCostData);
    }

    return QLCEViewTrendData.builder()
        .totalCost(getCostBillingStats(costData, prevCostData, timeFilters, trendStartInstant))
        .idleCost(getOtherCostBillingStats(costData, IDLE_COST_LABEL))
        .unallocatedCost(getOtherCostBillingStats(costData, UNALLOCATED_COST_LABEL))
        .systemCost(getOtherCostBillingStats(costData, SYSTEM_COST_LABEL))
        .utilizedCost(getOtherCostBillingStats(costData, UTILIZED_COST_LABEL))
        .efficiencyScoreStats(efficiencyScoreStats)
        .build();
  }

  @Override
  public QLCEViewTrendInfo getForecastCostData(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewAggregation> aggregateFunction, String cloudProviderTableName, ViewQueryParams queryParams) {
    Instant endInstantForForecastCost = viewsQueryHelper.getEndInstantForForecastCost(filters);
    ViewCostData currentCostData = getCostData(bigQuery, viewsQueryHelper.getFiltersForForecastCost(filters),
        aggregateFunction, cloudProviderTableName, queryParams);
    Double forecastCost = getForecastCost(currentCostData, endInstantForForecastCost);
    return getForecastCostBillingStats(forecastCost, currentCostData.getCost(), getStartInstantForForecastCost(),
        endInstantForForecastCost.plus(1, ChronoUnit.SECONDS));
  }

  @Override
  public List<String> getColumnsForTable(BigQuery bigQuery, String informationSchemaView, String table) {
    SelectQuery query = viewsQueryBuilder.getInformationSchemaQueryForColumns(informationSchemaView, table);
    return getColumnsData(bigQuery, query);
  }

  @Override
  public boolean isClusterPerspective(List<QLCEViewFilterWrapper> filters) {
    boolean dataSourceCondition = false;
    boolean ruleCondition = true;
    List<ViewRule> viewRuleList = new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
        List<ViewFieldIdentifier> dataSources;
        try {
          dataSources = ceView.getDataSources();
        } catch (Exception e) {
          dataSources = null;
        }
        if (dataSources != null) {
          dataSourceCondition = true;
          for (ViewFieldIdentifier identifier : dataSources) {
            if (!identifier.equals(CLUSTER) && !identifier.equals(LABEL)) {
              dataSourceCondition = false;
              break;
            }
          }
        }
      }
    }
    for (ViewRule rule : viewRuleList) {
      for (ViewCondition condition : rule.getViewConditions()) {
        ViewIdCondition viewIdCondition = (ViewIdCondition) condition;
        ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
        if (!(viewFieldIdentifier.equals(CLUSTER) || viewFieldIdentifier.equals(LABEL))) {
          ruleCondition = false;
          break;
        }
      }
    }

    return dataSourceCondition && ruleCondition;
  }

  @Override
  public ViewCostData getCostData(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewAggregation> aggregateFunction, String cloudProviderTableName, ViewQueryParams queryParams) {
    boolean isClusterTableQuery = isClusterTableQuery(filters, queryParams);
    List<ViewRule> viewRuleList = new ArrayList<>();
    List<QLCEViewFilter> idFilters = getIdFilters(filters);
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);
    SelectQuery query = getTrendStatsQuery(
        filters, idFilters, timeFilters, aggregateFunction, viewRuleList, cloudProviderTableName, queryParams);
    return getViewTrendStatsCostData(bigQuery, query, isClusterTableQuery);
  }

  @Override
  public Integer getTotalCountForQuery(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, String cloudProviderTableName, ViewQueryParams queryParams) {
    SelectQuery query = getQuery(
        filters, groupBy, Collections.EMPTY_LIST, Collections.emptyList(), cloudProviderTableName, queryParams);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTotalCountForQuery. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    Integer totalCount = null;
    for (FieldValueList row : result.iterateAll()) {
      totalCount = row.get(COUNT).getNumericValue().intValue();
    }
    return totalCount;
  }

  @Override
  public boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    boolean defaultFieldCheck = false;
    boolean isGroupByEntityEmpty =
        groupBy.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList()).isEmpty();
    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        if (ceView.getViewVisualization() != null) {
          ViewVisualization viewVisualization = ceView.getViewVisualization();
          ViewField defaultGroupByField = viewVisualization.getGroupBy();
          defaultFieldCheck = defaultGroupByField.getFieldName().equals(AWS_ACCOUNT_FIELD);
        }
      }
    }
    return (isGroupByEntityEmpty && defaultFieldCheck)
        || viewsQueryHelper.isGroupByFieldPresent(groupBy, AWS_ACCOUNT_FIELD);
  }

  private boolean isClusterTableQuery(List<QLCEViewFilterWrapper> filters, ViewQueryParams queryParams) {
    return (queryParams.isClusterQuery() || isClusterPerspective(filters)) && queryParams.getAccountId() != null;
  }

  private List<String> getColumnsData(BigQuery bigQuery, SelectQuery query) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTrendStatsData. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return convertToColumnList(result);
  }

  private List<String> convertToColumnList(TableResult result) {
    List<String> columns = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      columns.add(row.get("column_name").getValue().toString());
    }
    return columns;
  }

  private ViewCostData getViewTrendStatsCostData(BigQuery bigQuery, SelectQuery query, boolean isClusterTableQuery) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTrendStatsData. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return convertToTrendStatsData(result, isClusterTableQuery);
  }

  private ViewCostData convertToTrendStatsData(TableResult result, boolean isClusterTableQuery) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();
    Double totalCost = null;
    Double idleCost = null;
    Double unallocatedCost = null;
    for (FieldValueList row : result.iterateAll()) {
      for (Field field : fields) {
        switch (field.getName()) {
          case entityConstantMinStartTime:
            viewCostDataBuilder.minStartTime(getTimeStampValue(row, field, isClusterTableQuery));
            break;
          case entityConstantMaxStartTime:
            viewCostDataBuilder.maxStartTime(getTimeStampValue(row, field, isClusterTableQuery));
            break;
          case entityConstantCost:
          case entityConstantClusterCost:
            totalCost = getNumericValue(row, field);
            viewCostDataBuilder.cost(totalCost);
            break;
          case entityConstantIdleCost:
            idleCost = getNumericValue(row, field);
            viewCostDataBuilder.idleCost(idleCost);
            break;
          case entityConstantUnallocatedCost:
            unallocatedCost = getNumericValue(row, field);
            viewCostDataBuilder.unallocatedCost(unallocatedCost);
            break;
          case entityConstantSystemCost:
            viewCostDataBuilder.systemCost(getNumericValue(row, field));
            break;
          default:
            break;
        }
      }
    }
    if (totalCost != null && idleCost != null) {
      Double utilizedCost = totalCost - idleCost;
      if (unallocatedCost != null) {
        utilizedCost -= unallocatedCost;
      }
      viewCostDataBuilder.utilizedCost(viewsQueryHelper.getRoundedDoubleValue(utilizedCost));
    }
    return viewCostDataBuilder.build();
  }

  private boolean shouldShowUnallocatedCost(final List<QLCEViewGroupBy> groupByList) {
    boolean shouldShowUnallocatedCost = false;
    for (final String unallocatedCostClusterField : UNALLOCATED_COST_CLUSTER_FIELDS) {
      shouldShowUnallocatedCost = viewsQueryHelper.isGroupByFieldIdPresent(groupByList, unallocatedCostClusterField);
      if (shouldShowUnallocatedCost) {
        break;
      }
    }
    return shouldShowUnallocatedCost;
  }

  private Map<Long, Double> convertToUnallocatedCostData(final TableResult result) {
    Map<Long, Double> unallocatedCostMapping = new HashMap<>();
    final Schema schema = result.getSchema();
    final FieldList fields = schema.getFields();
    for (final FieldValueList row : result.iterateAll()) {
      long timestamp = 0L;
      double unallocatedCost = 0.0D;
      for (final Field field : fields) {
        switch (field.getType().getStandardType()) {
          case TIMESTAMP:
            timestamp = row.get(field.getName()).getTimestampValue() / 1000;
            break;
          case FLOAT64:
            unallocatedCost = getNumericValue(row, field, true);
            break;
          default:
            break;
        }
      }
      if (unallocatedCost != 0L) {
        unallocatedCostMapping.put(timestamp, unallocatedCostMapping.getOrDefault(timestamp, 0.0D) + unallocatedCost);
      }
    }
    return unallocatedCostMapping;
  }

  protected QLCEViewTrendInfo getCostBillingStats(ViewCostData costData, ViewCostData prevCostData,
      List<QLCEViewTimeFilter> filters, Instant trendFilterStartTime) {
    Instant startInstant = Instant.ofEpochMilli(getTimeFilter(filters, AFTER).getValue().longValue());
    Instant endInstant = Instant.ofEpochMilli(costData.getMaxStartTime() / 1000);
    if (costData.getMaxStartTime() == 0) {
      endInstant =
          Instant.ofEpochMilli(getTimeFilter(filters, QLCEViewTimeFilterOperator.BEFORE).getValue().longValue());
    }

    boolean isYearRequired = viewsQueryHelper.isYearRequired(startInstant, endInstant);
    String startInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(startInstant, isYearRequired);
    String endInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(endInstant, isYearRequired);
    String totalCostDescription = format(COST_DESCRIPTION, startInstantFormat, endInstantFormat);
    String totalCostValue =
        format(COST_VALUE, viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(costData.getCost())));

    double forecastCost = viewsQueryHelper.getForecastCost(ViewCostData.builder()
                                                               .cost(costData.getCost())
                                                               .minStartTime(costData.getMinStartTime() / 1000)
                                                               .maxStartTime(costData.getMaxStartTime() / 1000)
                                                               .build(),
        Instant.ofEpochMilli(getTimeFilter(filters, QLCEViewTimeFilterOperator.BEFORE).getValue().longValue()));

    return QLCEViewTrendInfo.builder()
        .statsLabel(TOTAL_COST_LABEL)
        .statsDescription(totalCostDescription)
        .statsValue(totalCostValue)
        .statsTrend(
            viewsQueryHelper.getBillingTrend(costData.getCost(), forecastCost, prevCostData, trendFilterStartTime))
        .value(costData.getCost())
        .build();
  }

  protected QLCEViewTrendInfo getOtherCostBillingStats(ViewCostData costData, String costLabel) {
    if (costData == null) {
      return null;
    }
    Double otherCost;
    double totalCost = costData.getCost();
    String otherCostDescription = EMPTY_VALUE;
    String otherCostValue = EMPTY_VALUE;
    switch (costLabel) {
      case IDLE_COST_LABEL:
        otherCost = costData.getIdleCost();
        break;
      case UNALLOCATED_COST_LABEL:
        otherCost = costData.getUnallocatedCost();
        break;
      case UTILIZED_COST_LABEL:
        otherCost = costData.getUtilizedCost();
        break;
      case SYSTEM_COST_LABEL:
        otherCost = costData.getSystemCost();
        break;
      default:
        return null;
    }
    if (otherCost != null) {
      otherCostValue =
          String.format(COST_VALUE, viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(otherCost)));
      if (totalCost != 0) {
        double percentageOfTotalCost = viewsQueryHelper.getRoundedDoublePercentageValue(otherCost / totalCost);
        otherCostDescription = String.format(OTHER_COST_DESCRIPTION, percentageOfTotalCost + "%");
      }
    }

    return QLCEViewTrendInfo.builder()
        .statsLabel(costLabel)
        .statsDescription(otherCostDescription)
        .statsValue(otherCostValue)
        .value(otherCost)
        .build();
  }

  protected QLCEViewTrendInfo getForecastCostBillingStats(
      Double forecastCost, Double totalCost, Instant startInstant, Instant endInstant) {
    String forecastCostDescription = "";
    String forecastCostValue = "";
    Double statsTrend = 0.0;

    if (forecastCost != null) {
      boolean isYearRequired = viewsQueryHelper.isYearRequired(startInstant, endInstant);
      String startInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(startInstant, isYearRequired);
      String endInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(endInstant, isYearRequired);
      forecastCostDescription = format(COST_DESCRIPTION, startInstantFormat, endInstantFormat);
      forecastCostValue =
          format(COST_VALUE, viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(forecastCost)));
      statsTrend = viewsQueryHelper.getRoundedDoubleValue(((forecastCost - totalCost) / totalCost) * 100);
    } else {
      forecastCost = 0.0;
    }

    return QLCEViewTrendInfo.builder()
        .statsLabel(FORECAST_COST_LABEL)
        .statsTrend(statsTrend)
        .statsDescription(forecastCostDescription)
        .statsValue(forecastCostValue)
        .value(forecastCost)
        .build();
  }

  private static List<QLCEViewGroupBy> modifyGroupByForUnallocatedCostData(final List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream()
        .filter(groupBy -> Objects.nonNull(groupBy.getTimeTruncGroupBy()))
        .collect(Collectors.toList());
  }

  protected List<QLCEViewTimeFilter> getTrendFilters(List<QLCEViewTimeFilter> timeFilters) {
    Instant startInstant = Instant.ofEpochMilli(getTimeFilter(timeFilters, AFTER).getValue().longValue());
    Instant endInstant =
        Instant.ofEpochMilli(getTimeFilter(timeFilters, QLCEViewTimeFilterOperator.BEFORE).getValue().longValue());
    long diffMillis = Duration.between(startInstant, endInstant).toMillis();
    long trendEndTime = startInstant.toEpochMilli() - 1000;
    long trendStartTime = trendEndTime - diffMillis;

    List<QLCEViewTimeFilter> trendFilters = new ArrayList<>();
    trendFilters.add(getTrendBillingFilter(trendStartTime, AFTER));
    trendFilters.add(getTrendBillingFilter(trendEndTime, QLCEViewTimeFilterOperator.BEFORE));
    return trendFilters;
  }

  protected QLCEViewTimeFilter getTrendBillingFilter(Long filterTime, QLCEViewTimeFilterOperator operator) {
    return QLCEViewTimeFilter.builder()
        .field(QLCEViewFieldInput.builder()
                   .fieldId(ViewsMetaDataFields.START_TIME.getFieldName())
                   .fieldName(ViewsMetaDataFields.START_TIME.getFieldName())
                   .identifier(ViewFieldIdentifier.COMMON)
                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                   .build())
        .operator(operator)
        .value(filterTime)
        .build();
  }

  protected QLCEViewTimeFilter getTimeFilter(
      List<QLCEViewTimeFilter> filters, QLCEViewTimeFilterOperator timeFilterOperator) {
    Optional<QLCEViewTimeFilter> timeFilter =
        filters.stream().filter(filter -> filter.getOperator() == timeFilterOperator).findFirst();
    if (timeFilter.isPresent()) {
      return timeFilter.get();
    } else {
      throw new InvalidRequestException("Time cannot be null");
    }
  }

  private SelectQuery getTrendStatsQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewFilter> idFilters,
      List<QLCEViewTimeFilter> timeFilters, List<QLCEViewAggregation> aggregateFunction, List<ViewRule> viewRuleList,
      String cloudProviderTableName, ViewQueryParams queryParams) {
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);
    if (viewMetadataFilter.isPresent()) {
      final String viewId = viewMetadataFilter.get().getViewMetadataFilter().getViewId();
      CEView ceView = viewService.get(viewId);
      viewRuleList = ceView.getViewRules();
    }
    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      if (isClusterPerspective(filters)) {
        // Changes column name for cost to billingamount
        aggregateFunction = getModifiedAggregations(aggregateFunction);
      }
      cloudProviderTableName = getUpdatedCloudProviderTableName(
          filters, null, aggregateFunction, "", cloudProviderTableName, queryParams.isClusterQuery());
    }
    return viewsQueryBuilder.getQuery(viewRuleList, idFilters, timeFilters, Collections.EMPTY_LIST, aggregateFunction,
        Collections.EMPTY_LIST, cloudProviderTableName);
  }

  // Current gen
  private SelectQuery getQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      boolean isTimeTruncGroupByRequired) {
    return getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName,
        viewsQueryHelper.buildQueryParams(null, isTimeTruncGroupByRequired, false, false, false));
  }

  // Next-gen
  private SelectQuery getQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      ViewQueryParams queryParams) {
    List<ViewRule> viewRuleList = new ArrayList<>();

    // Removing group by none if present
    boolean skipDefaultGroupBy = false;
    if (viewsQueryHelper.isGroupByNonePresent(groupBy)) {
      skipDefaultGroupBy = true;
      groupBy = viewsQueryHelper.removeGroupByNone(groupBy);
    }

    List<QLCEViewGroupBy> modifiedGroupBy = groupBy != null ? new ArrayList<>(groupBy) : new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = getViewMetadataFilter(filters);

    List<QLCEViewRule> rules = removeAccountNameFromAWSAccountRuleFilter(getRuleFilters(filters));
    if (!rules.isEmpty()) {
      for (QLCEViewRule rule : rules) {
        viewRuleList.add(convertQLCEViewRuleToViewRule(rule));
      }
    }

    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
        if (ceView.getViewVisualization() != null) {
          ViewVisualization viewVisualization = ceView.getViewVisualization();
          ViewField defaultGroupByField = viewVisualization.getGroupBy();
          ViewTimeGranularity defaultTimeGranularity = viewVisualization.getGranularity();
          if (defaultTimeGranularity == null) {
            defaultTimeGranularity = ViewTimeGranularity.DAY;
          }
          modifiedGroupBy = getModifiedGroupBy(groupBy, defaultGroupByField, defaultTimeGranularity,
              queryParams.isTimeTruncGroupByRequired(), skipDefaultGroupBy);
        }
      }
    }
    List<QLCEViewFilter> idFilters = removeAccountNameFromAWSAccountIdFilter(getIdFilters(filters));
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);

    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      boolean isPodQuery = false;
      if (isClusterPerspective(filters) || queryParams.isClusterQuery()) {
        isPodQuery = isPodQuery(modifiedGroupBy);
        if (isInstanceDetailsQuery(modifiedGroupBy)) {
          idFilters.add(getFilterForInstanceDetails(modifiedGroupBy));
        }
        modifiedGroupBy = addAdditionalRequiredGroupBy(modifiedGroupBy);
        idFilters = addNotNullFilters(idFilters, modifiedGroupBy);
        // Changes column name for cost to billingamount
        aggregateFunction = getModifiedAggregations(aggregateFunction);
        sort = getModifiedSort(sort);
      }
      cloudProviderTableName = getUpdatedCloudProviderTableName(filters, modifiedGroupBy, aggregateFunction,
          queryParams.getAccountId(), cloudProviderTableName, queryParams.isClusterQuery(), isPodQuery);
    }

    if (queryParams.isTotalCountQuery()) {
      return viewsQueryBuilder.getTotalCountQuery(
          viewRuleList, idFilters, timeFilters, modifiedGroupBy, cloudProviderTableName);
    }

    return viewsQueryBuilder.getQuery(viewRuleList, idFilters, timeFilters, modifiedGroupBy, aggregateFunction, sort,
        cloudProviderTableName, queryParams.getTimeOffsetInDays());
  }

  private static List<QLCEViewRule> removeAccountNameFromAWSAccountRuleFilter(final List<QLCEViewRule> ruleFilters) {
    final List<QLCEViewRule> updatedRuleFilters = new ArrayList<>();
    ruleFilters.forEach(ruleFilter -> {
      if (Objects.nonNull(ruleFilter.getConditions())) {
        final List<QLCEViewFilter> updatedConditions = new ArrayList<>();
        ruleFilter.getConditions().forEach(condition -> {
          if (Objects.nonNull(condition.getField()) && AWS_ACCOUNT_FIELD.equals(condition.getField().getFieldName())
              && Objects.nonNull(condition.getValues())) {
            final String[] updatedValues = Arrays.stream(condition.getValues())
                                               .map(AwsAccountFieldHelper::removeAwsAccountNameFromValue)
                                               .toArray(String[] ::new);
            updatedConditions.add(QLCEViewFilter.builder()
                                      .field(condition.getField())
                                      .operator(condition.getOperator())
                                      .values(updatedValues)
                                      .build());
          } else {
            updatedConditions.add(condition);
          }
        });
        updatedRuleFilters.add(QLCEViewRule.builder().conditions(updatedConditions).build());
      } else {
        updatedRuleFilters.add(ruleFilter);
      }
    });
    return updatedRuleFilters;
  }

  private static List<QLCEViewFilter> removeAccountNameFromAWSAccountIdFilter(final List<QLCEViewFilter> idFilters) {
    final List<QLCEViewFilter> updatedIdFilters = new ArrayList<>();
    idFilters.forEach(idFilter -> {
      if (Objects.nonNull(idFilter.getField()) && AWS_ACCOUNT_FIELD.equals(idFilter.getField().getFieldName())
          && Objects.nonNull(idFilter.getValues())) {
        final String[] updatedValues = Arrays.stream(idFilter.getValues())
                                           .map(AwsAccountFieldHelper::removeAwsAccountNameFromValue)
                                           .toArray(String[] ::new);
        updatedIdFilters.add(QLCEViewFilter.builder()
                                 .field(idFilter.getField())
                                 .operator(idFilter.getOperator())
                                 .values(updatedValues)
                                 .build());
      } else {
        updatedIdFilters.add(idFilter);
      }
    });
    return updatedIdFilters;
  }

  public static List<ViewRule> convertQLCEViewRuleToViewRule(@NotNull List<QLCEViewRule> ruleList) {
    return ruleList.stream().map(ViewsBillingServiceImpl::convertQLCEViewRuleToViewRule).collect(Collectors.toList());
  }

  private static ViewRule convertQLCEViewRuleToViewRule(QLCEViewRule rule) {
    List<ViewCondition> conditionsList = convertIdFilterToViewCondition(rule.getConditions());
    return ViewRule.builder().viewConditions(conditionsList).build();
  }

  public static List<ViewCondition> convertIdFilterToViewCondition(@NotNull List<QLCEViewFilter> qlceViewFilters) {
    return qlceViewFilters.stream()
        .map(ViewsBillingServiceImpl::constructViewIdConditionFromQLCEViewFilter)
        .collect(Collectors.toList());
  }

  private static ViewIdCondition constructViewIdConditionFromQLCEViewFilter(QLCEViewFilter filter) {
    return ViewIdCondition.builder()
        .values(Arrays.asList(filter.getValues()))
        .viewField(getViewField(filter.getField()))
        .viewOperator(mapQLCEViewFilterOperatorToViewIdOperator(filter.getOperator()))
        .build();
  }

  private static ViewIdOperator mapQLCEViewFilterOperatorToViewIdOperator(QLCEViewFilterOperator operator) {
    try {
      return ViewIdOperator.valueOf(operator.name());
    } catch (IllegalArgumentException ex) {
      log.warn("ViewIdOperator equivalent of QLCEViewFilterOperator=[{}] is not present.", operator.name(), ex);
      return null;
    }
  }

  public static ViewField getViewField(QLCEViewFieldInput field) {
    return ViewField.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  private static Optional<QLCEViewFilterWrapper> getViewMetadataFilter(List<QLCEViewFilterWrapper> filters) {
    return filters.stream().filter(f -> f.getViewMetadataFilter() != null).findFirst();
  }

  private static List<QLCEViewTimeFilter> getTimeFilters(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getTimeFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public static List<QLCEViewFilter> getIdFilters(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getIdFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public static List<QLCEViewRule> getRuleFilters(@NotNull List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .map(QLCEViewFilterWrapper::getRuleFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /*
   * This method is overriding the Group By passed by the UI with the defaults selected by user while creating the View
   * */
  private List<QLCEViewGroupBy> getModifiedGroupBy(List<QLCEViewGroupBy> groupByList, ViewField defaultGroupByField,
      ViewTimeGranularity defaultTimeGranularity, boolean isTimeTruncGroupByRequired, boolean skipDefaultGroupBy) {
    if (groupByList == null) {
      return new ArrayList<>();
    }
    List<QLCEViewGroupBy> modifiedGroupBy = new ArrayList<>();
    Optional<QLCEViewGroupBy> timeTruncGroupBy =
        groupByList.stream().filter(g -> g.getTimeTruncGroupBy() != null).findFirst();

    List<QLCEViewGroupBy> entityGroupBy =
        groupByList.stream().filter(g -> g.getEntityGroupBy() != null).collect(Collectors.toList());

    if (timeTruncGroupBy.isPresent()) {
      modifiedGroupBy.add(timeTruncGroupBy.get());
    } else if (isTimeTruncGroupByRequired) {
      modifiedGroupBy.add(
          QLCEViewGroupBy.builder()
              .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder()
                                    .resolution(viewsQueryBuilder.mapViewTimeGranularityToQLCEViewTimeGroupType(
                                        defaultTimeGranularity))
                                    .build())
              .build());
    }

    if (!entityGroupBy.isEmpty()) {
      modifiedGroupBy.addAll(entityGroupBy);
    } else {
      if (!skipDefaultGroupBy) {
        modifiedGroupBy.add(
            QLCEViewGroupBy.builder().entityGroupBy(viewsQueryBuilder.getViewFieldInput(defaultGroupByField)).build());
      }
    }
    return modifiedGroupBy;
  }

  // Here conversion field is not null if id to name conversion is required for the main group by field
  private QLCEViewGridData convertToEntityStatsData(TableResult result, Map<String, ViewCostData> costTrendData,
      long startTimeForTrend, boolean isClusterPerspective, boolean isUsedByTimeSeriesStats, boolean skipRoundOff,
      String conversionField, String accountId) {
    if (isClusterPerspective) {
      return convertToEntityStatsDataForCluster(
          result, costTrendData, startTimeForTrend, isUsedByTimeSeriesStats, skipRoundOff);
    }
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    List<String> fieldNames = getFieldNames(fields);
    List<String> entityNames = new ArrayList<>();

    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      Double cost = null;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String id = DEFAULT_STRING_VALUE;
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case STRING:
            name = fetchStringValue(row, field);
            entityNames.add(name);
            id = getUpdatedId(id, name);
            break;
          case FLOAT64:
            cost = getNumericValue(row, field, skipRoundOff);
            dataPointBuilder.cost(cost);
            break;
          default:
            break;
        }
      }
      dataPointBuilder.id(id);
      dataPointBuilder.name(name);
      if (!isUsedByTimeSeriesStats) {
        dataPointBuilder.costTrend(getCostTrendForEntity(cost, costTrendData.get(id), startTimeForTrend));
      }
      entityStatsDataPoints.add(dataPointBuilder.build());
    }

    if (conversionField != null) {
      entityStatsDataPoints = getUpdatedDataPoints(entityStatsDataPoints, entityNames, accountId, conversionField);
    }

    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fieldNames).build();
  }

  private QLCEViewGridData convertToEntityStatsDataForCluster(TableResult result,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isUsedByTimeSeriesStats,
      boolean skipRoundOff) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();

    List<String> fieldNames = getFieldNames(fields);
    boolean isInstanceDetailsData = fieldNames.contains(INSTANCE_ID);
    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    Set<String> instanceTypes = new HashSet<>();
    for (FieldValueList row : result.iterateAll()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      ClusterDataBuilder clusterDataBuilder = ClusterData.builder();
      Double cost = null;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String entityId = DEFAULT_STRING_VALUE;
      String pricingSource = DEFAULT_STRING_VALUE;

      Map<String, java.lang.reflect.Field> builderFields = new HashMap<>();
      for (java.lang.reflect.Field builderField : clusterDataBuilder.getClass().getDeclaredFields()) {
        builderFields.put(builderField.getName().toLowerCase(), builderField);
      }

      for (Field field : fields) {
        java.lang.reflect.Field builderField = builderFields.get(field.getName().toLowerCase());
        try {
          if (builderField != null) {
            builderField.setAccessible(true);
            if (builderField.getType().equals(String.class)) {
              builderField.set(clusterDataBuilder, fetchStringValue(row, field));
            } else {
              builderField.set(clusterDataBuilder, getNumericValue(row, field, skipRoundOff));
            }
          } else {
            switch (field.getName().toLowerCase()) {
              case BILLING_AMOUNT:
                cost = getNumericValue(row, field, skipRoundOff);
                clusterDataBuilder.totalCost(cost);
                break;
              case ACTUAL_IDLE_COST:
                clusterDataBuilder.idleCost(getNumericValue(row, field, skipRoundOff));
                break;
              case PRICING_SOURCE:
                pricingSource = fetchStringValue(row, field);
                break;
              default:
                break;
            }
          }
        } catch (Exception e) {
          log.error("Exception in convertToEntityStatsDataForCluster: {}", e.toString());
        }

        if (field.getType().getStandardType() == StandardSQLTypeName.STRING) {
          name = fetchStringValue(row, field);
          entityId = getUpdatedId(entityId, name);
        }
      }
      clusterDataBuilder.id(entityId);
      clusterDataBuilder.name(name);
      ClusterData clusterData = clusterDataBuilder.build();
      // Calculating efficiency score
      if (cost != null && cost > 0 && clusterData.getIdleCost() != null && clusterData.getUnallocatedCost() != null) {
        clusterDataBuilder.efficiencyScore(viewsQueryHelper.calculateEfficiencyScore(
            cost, clusterData.getIdleCost(), clusterData.getUnallocatedCost()));
      }
      // Collect instance type
      if (clusterData.getInstanceType() != null) {
        instanceTypes.add(clusterData.getInstanceType());
      }
      dataPointBuilder.cost(cost);
      if (!isUsedByTimeSeriesStats) {
        dataPointBuilder.costTrend(getCostTrendForEntity(cost, costTrendData.get(entityId), startTimeForTrend));
      }
      dataPointBuilder.clusterData(clusterDataBuilder.build());
      dataPointBuilder.isClusterPerspective(true);
      dataPointBuilder.id(entityId);
      dataPointBuilder.name(name);
      dataPointBuilder.pricingSource(pricingSource);
      entityStatsDataPoints.add(dataPointBuilder.build());
    }
    if (isInstanceDetailsData && !isUsedByTimeSeriesStats) {
      return QLCEViewGridData.builder()
          .data(instanceDetailsHelper.getInstanceDetails(entityStatsDataPoints, getInstanceType(instanceTypes)))
          .fields(fieldNames)
          .build();
    }
    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fieldNames).build();
  }

  private QLCEViewGridData costCategoriesPostFetchResponseUpdate(QLCEViewGridData response, String businessMappingId) {
    if (businessMappingId != null) {
      BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
      if (businessMapping.getUnallocatedCost() != null) {
        UnallocatedCostStrategy strategy = businessMapping.getUnallocatedCost().getStrategy();
        switch (strategy) {
          case DISPLAY_NAME:
            response.getData().forEach(dataPoint -> {
              if (dataPoint.getName().equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedDataPoints.add(QLCEViewEntityStatsDataPoint.builder()
                                          .name(businessMapping.getUnallocatedCost().getLabel())
                                          .id(businessMapping.getUnallocatedCost().getLabel())
                                          .pricingSource(dataPoint.getPricingSource())
                                          .cost(dataPoint.getCost())
                                          .costTrend(dataPoint.getCostTrend())
                                          .isClusterPerspective(dataPoint.isClusterPerspective())
                                          .clusterData(dataPoint.getClusterData())
                                          .instanceDetails(dataPoint.getInstanceDetails())
                                          .storageDetails(dataPoint.getStorageDetails())
                                          .build());
              } else {
                updatedDataPoints.add(dataPoint);
              }
            });
            break;
          case HIDE:
            response.getData().forEach(dataPoint -> {
              if (!dataPoint.getName().equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedDataPoints.add(dataPoint);
              }
            });
            break;
          case SHARE:
          default:
            throw new InvalidRequestException(
                "Invalid Unallocated Cost Strategy / Unallocated Cost Strategy not supported");
        }
        return QLCEViewGridData.builder().data(updatedDataPoints).fields(response.getFields()).build();
      }
    }
    return response;
  }

  private List<String> getFieldNames(FieldList fields) {
    List<String> fieldNames = new ArrayList<>();
    for (Field field : fields) {
      if (field.getType().getStandardType() == StandardSQLTypeName.STRING) {
        fieldNames.add(field.getName());
      }
    }
    return fieldNames;
  }

  private String getUpdatedId(String id, String newField) {
    return id.equals(DEFAULT_STRING_VALUE) ? newField : id + ID_SEPARATOR + newField;
  }

  public List<String> convertToFilterValuesData(TableResult result, List<QLCEViewFieldInput> viewFieldList) {
    List<String> filterValues = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      for (QLCEViewFieldInput field : viewFieldList) {
        final String filterStringValue = fetchStringValue(row, field);
        if (Objects.nonNull(filterStringValue)) {
          filterValues.add(filterStringValue);
        }
      }
    }
    return filterValues;
  }

  public List<String> costCategoriesPostFetchResponseUpdate(List<String> response, String businessMappingId) {
    if (businessMappingId != null) {
      BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      if (businessMapping.getUnallocatedCost() != null) {
        List<String> updatedResponse = new ArrayList<>();
        UnallocatedCostStrategy strategy = businessMapping.getUnallocatedCost().getStrategy();
        switch (strategy) {
          case DISPLAY_NAME:
            response.forEach(value -> {
              if (value.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedResponse.add(businessMapping.getUnallocatedCost().getLabel());
              } else {
                updatedResponse.add(value);
              }
            });
            break;
          case HIDE:
            response.forEach(value -> {
              if (!value.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
                updatedResponse.add(value);
              }
            });
            break;
          case SHARE:
          default:
            throw new InvalidRequestException(
                "Invalid Unallocated Cost Strategy / Unallocated Cost Strategy not supported");
        }
        return updatedResponse;
      }
    }
    return response;
  }

  private long getTimeStampValue(FieldValueList row, Field field) {
    return getTimeStampValue(row, field, false);
  }

  private long getTimeStampValue(FieldValueList row, Field field, boolean isClusterPerspective) {
    FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return isClusterPerspective ? value.getLongValue() * 1000 : value.getTimestampValue();
    }
    return 0;
  }

  private double getNumericValue(FieldValueList row, Field field) {
    return getNumericValue(row, field, false);
  }

  private double getNumericValue(FieldValueList row, Field field, boolean skipRoundOff) {
    FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return skipRoundOff ? value.getNumericValue().doubleValue()
                          : Math.round(value.getNumericValue().doubleValue() * 100D) / 100D;
    }
    return 0;
  }

  private String fetchStringValue(FieldValueList row, Field field) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return OTHERS;
  }

  private String fetchStringValue(FieldValueList row, QLCEViewFieldInput field) {
    Object value = row.get(viewsQueryBuilder.getAliasFromField(field)).getValue();
    if (value != null) {
      return value.toString();
    }
    return null;
  }

  public List<QLCEViewTimeSeriesData> convertToQLViewTimeSeriesData(TableResult result, String accountId) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();

    Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap = new HashMap<>();
    Set<String> awsAccounts = new HashSet<>();
    for (FieldValueList row : result.iterateAll()) {
      QLCEViewDataPointBuilder billingDataPointBuilder = QLCEViewDataPoint.builder();
      Long startTimeTruncatedTimestamp = null;
      Double value = Double.valueOf(0);
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case TIMESTAMP:
            startTimeTruncatedTimestamp = row.get(field.getName()).getTimestampValue() / 1000;
            break;
          case STRING:
            String stringValue = fetchStringValue(row, field);
            if (AWS_ACCOUNT_FIELD_ID.equals(field.getName())) {
              awsAccounts.add(stringValue);
            }
            billingDataPointBuilder.name(stringValue).id(stringValue);
            break;
          case FLOAT64:
            value += getNumericValue(row, field);
            break;
          default:
            break;
        }
      }

      billingDataPointBuilder.value(value);
      List<QLCEViewDataPoint> dataPoints = new ArrayList<>();
      if (timeSeriesDataPointsMap.containsKey(startTimeTruncatedTimestamp)) {
        dataPoints = timeSeriesDataPointsMap.get(startTimeTruncatedTimestamp);
      }
      dataPoints.add(billingDataPointBuilder.build());
      timeSeriesDataPointsMap.put(startTimeTruncatedTimestamp, dataPoints);
    }

    return convertTimeSeriesPointsMapToList(
        modifyTimeSeriesDataPointsMap(timeSeriesDataPointsMap, awsAccounts, accountId));
  }

  private Map<Long, List<QLCEViewDataPoint>> modifyTimeSeriesDataPointsMap(
      final Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap, final Set<String> awsAccounts,
      final String accountId) {
    Map<Long, List<QLCEViewDataPoint>> updatedTimeSeriesDataPointsMap = timeSeriesDataPointsMap;
    if (!awsAccounts.isEmpty()) {
      final Map<String, String> entityIdToName =
          entityMetadataService.getEntityIdToNameMapping(new ArrayList<>(awsAccounts), accountId, AWS_ACCOUNT_FIELD);
      updatedTimeSeriesDataPointsMap =
          timeSeriesDataPointsMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
              timeSeriesDataPoint -> getUpdatedTimeSeriesDataPoints(entityIdToName, timeSeriesDataPoint.getValue())));
    }
    return updatedTimeSeriesDataPointsMap;
  }

  private List<QLCEViewDataPoint> getUpdatedTimeSeriesDataPoints(
      final Map<String, String> entityIdToName, final List<QLCEViewDataPoint> timeSeriesDataPoints) {
    final List<QLCEViewDataPoint> updatedTimeSeriesDataPoints = new ArrayList<>();
    timeSeriesDataPoints.forEach(timeSeriesDataPoint
        -> updatedTimeSeriesDataPoints.add(
            QLCEViewDataPoint.builder()
                .id(timeSeriesDataPoint.getId())
                .value(timeSeriesDataPoint.getValue())
                .name(AwsAccountFieldHelper.mergeAwsAccountIdAndName(
                    timeSeriesDataPoint.getId(), entityIdToName.get(timeSeriesDataPoint.getId())))
                .build()));
    return updatedTimeSeriesDataPoints;
  }

  public List<QLCEViewTimeSeriesData> convertTimeSeriesPointsMapToList(
      Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap) {
    return timeSeriesDataPointsMap.entrySet()
        .stream()
        .map(e
            -> QLCEViewTimeSeriesData.builder()
                   .time(e.getKey())
                   .date(getFormattedDate(Instant.ofEpochMilli(e.getKey()), DATE_PATTERN_FOR_CHART))
                   .values(e.getValue())
                   .build())
        .sorted(Comparator.comparing(QLCEViewTimeSeriesData::getTime))
        .collect(Collectors.toList());
  }

  protected String getFormattedDate(Instant instant, String datePattern) {
    return instant.atZone(ZoneId.of("GMT")).format(DateTimeFormatter.ofPattern(datePattern));
  }

  private Map<String, ViewCostData> getEntityStatsDataForCostTrend(BigQuery bigQuery,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewSortCriteria> sort, String cloudProviderTableName, Integer limit, Integer offset,
      ViewQueryParams queryParams) {
    boolean isClusterTableQuery = isClusterTableQuery(filters, queryParams);
    SelectQuery query = getQuery(getFiltersForEntityStatsCostTrend(filters), groupBy,
        getAggregationsForEntityStatsCostTrend(aggregateFunction), sort, cloudProviderTableName, queryParams);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getEntityStatsDataForCostTrend. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return convertToEntityStatsCostTrendData(result, isClusterTableQuery, queryParams.isSkipRoundOff());
  }

  private Map<String, ViewCostData> convertToEntityStatsCostTrendData(
      TableResult result, boolean isClusterTableQuery, boolean skipRoundOff) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    for (FieldValueList row : result.iterateAll()) {
      String name = "";
      String id = DEFAULT_STRING_VALUE;
      ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case STRING:
            name = fetchStringValue(row, field);
            id = getUpdatedId(id, name);
            break;
          case FLOAT64:
            if (field.getName().equalsIgnoreCase(BILLING_AMOUNT) || field.getName().equalsIgnoreCase(COST)) {
              viewCostDataBuilder.cost(getNumericValue(row, field, skipRoundOff));
            }
            break;
          default:
            switch (field.getName()) {
              case entityConstantMinStartTime:
                viewCostDataBuilder.minStartTime(getTimeStampValue(row, field, isClusterTableQuery));
                break;
              case entityConstantMaxStartTime:
                viewCostDataBuilder.maxStartTime(getTimeStampValue(row, field, isClusterTableQuery));
                break;
              default:
                break;
            }
            break;
        }
      }
      costTrendData.put(id, viewCostDataBuilder.build());
    }
    return costTrendData;
  }

  private List<QLCEViewFilterWrapper> getFiltersForEntityStatsCostTrend(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewFilterWrapper> trendFilters =
        filters.stream().filter(f -> f.getTimeFilter() == null).collect(Collectors.toList());
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);
    List<QLCEViewTimeFilter> trendTimeFilters = getTrendFilters(timeFilters);
    trendTimeFilters.forEach(
        timeFilter -> trendFilters.add(QLCEViewFilterWrapper.builder().timeFilter(timeFilter).build()));

    return trendFilters;
  }

  private List<QLCEViewAggregation> getAggregationsForEntityStatsCostTrend(List<QLCEViewAggregation> aggregations) {
    List<QLCEViewAggregation> trendAggregations = new ArrayList<>(aggregations);
    trendAggregations.add(QLCEViewAggregation.builder().operationType(MAX).columnName("startTime").build());
    trendAggregations.add(QLCEViewAggregation.builder().operationType(MIN).columnName("startTime").build());
    return trendAggregations;
  }

  private long getStartTimeForTrendFilters(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);
    List<QLCEViewTimeFilter> trendTimeFilters = getTrendFilters(timeFilters);

    Optional<QLCEViewTimeFilter> startTimeFilter =
        trendTimeFilters.stream().filter(filter -> filter.getOperator() == AFTER).findFirst();
    if (startTimeFilter.isPresent()) {
      return startTimeFilter.get().getValue().longValue();
    } else {
      throw new InvalidRequestException("Start time cannot be null");
    }
  }

  private double getCostTrendForEntity(Double currentCost, ViewCostData prevCostData, long startTimeForTrend) {
    if (prevCostData != null && prevCostData.getCost() != 0 && currentCost != null) {
      Double prevCost = prevCostData.getCost();
      long startTimeForPrevCost = prevCostData.getMinStartTime() / 1000;
      double costDifference = currentCost - prevCost;
      if (startTimeForTrend + ONE_DAY_MILLIS > startTimeForPrevCost) {
        return Math.round((costDifference * 100 / prevCost) * 100D) / 100D;
      }
    }
    return 0;
  }

  private List<QLCEViewGroupBy> addAdditionalRequiredGroupBy(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewGroupBy> modifiedGroupBy = new ArrayList<>();
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null) {
        switch (groupBy.getEntityGroupBy().getFieldName()) {
          case GROUP_BY_WORKLOAD_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_TYPE, WORKLOAD_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_NAME, WORKLOAD_NAME, CLUSTER));
            break;
          case GROUP_BY_NAMESPACE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            break;
          case GROUP_BY_ECS_SERVICE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_SERVICE, CLOUD_SERVICE_NAME, CLUSTER));
            break;
          case GROUP_BY_ECS_LAUNCH_TYPE_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            break;
          case GROUP_BY_ECS_TASK_ID:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_LAUNCH_TYPE, LAUNCH_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_SERVICE, CLOUD_SERVICE_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_ECS_TASK, TASK_ID, CLUSTER));
            break;
          case GROUP_BY_PRODUCT:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            break;
          case GROUP_BY_NODE:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_ID, CLUSTER_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_ID, INSTANCE_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_TYPE, INSTANCE_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_NAME, INSTANCE_NAME, CLUSTER));
            break;
          case GROUP_BY_POD:
          case GROUP_BY_STORAGE:
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_ID, CLUSTER_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLUSTER_NAME, CLUSTER_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_NAMESPACE, NAMESPACE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_WORKLOAD_NAME, WORKLOAD_NAME, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_CLOUD_PROVIDER, CLOUD_PROVIDER, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_ID, INSTANCE_ID, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_TYPE, INSTANCE_TYPE, CLUSTER));
            modifiedGroupBy.add(getGroupBy(GROUP_BY_INSTANCE_NAME, INSTANCE_NAME, CLUSTER));
            break;
          default:
            modifiedGroupBy.add(groupBy);
        }
      } else {
        modifiedGroupBy.add(groupBy);
      }
    });

    return modifiedGroupBy;
  }

  private List<QLCEViewFilter> addNotNullFilters(List<QLCEViewFilter> filters, List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFilter> updatedFilters = new ArrayList<>(filters);
    groupByList.forEach(groupBy -> {
      if (groupBy.getEntityGroupBy() != null) {
        switch (groupBy.getEntityGroupBy().getFieldName()) {
          case GROUP_BY_INSTANCE_ID:
          case GROUP_BY_INSTANCE_NAME:
          case GROUP_BY_INSTANCE_TYPE:
            break;
          default:
            updatedFilters.add(QLCEViewFilter.builder()
                                   .field(groupBy.getEntityGroupBy())
                                   .operator(QLCEViewFilterOperator.NOT_NULL)
                                   .values(new String[] {""})
                                   .build());
        }
      }
    });
    return updatedFilters;
  }

  private QLCEViewGroupBy getGroupBy(String fieldName, String fieldId, ViewFieldIdentifier identifier) {
    return QLCEViewGroupBy.builder()
        .entityGroupBy(
            QLCEViewFieldInput.builder().fieldId(fieldId).fieldName(fieldName).identifier(identifier).build())
        .build();
  }

  // Methods for determining table
  public String getUpdatedCloudProviderTableName(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, String accountId, String cloudProviderTableName,
      boolean isClusterQuery) {
    return getUpdatedCloudProviderTableName(
        filters, groupBy, aggregateFunction, accountId, cloudProviderTableName, isClusterQuery, false);
  }

  public String getUpdatedCloudProviderTableName(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, String accountId, String cloudProviderTableName,
      boolean isClusterQuery, boolean isPodQuery) {
    if (!isClusterPerspective(filters) && !isClusterQuery) {
      return cloudProviderTableName;
    }
    String[] tableNameSplit = cloudProviderTableName.split("\\.");
    String tableName;
    List<QLCEViewFieldInput> entityGroupBy = new ArrayList<>();
    if (groupBy != null) {
      entityGroupBy = groupBy.stream()
                          .filter(entry -> entry.getEntityGroupBy() != null)
                          .map(QLCEViewGroupBy::getEntityGroupBy)
                          .collect(Collectors.toList());
    }
    if (aggregateFunction == null) {
      aggregateFunction = new ArrayList<>();
    }

    if (featureFlagService.isEnabled(CE_BILLING_DATA_PRE_AGGREGATION, accountId) && !isPodQuery
        && areAggregationsValidForPreAggregation(aggregateFunction) && isValidGroupByForPreAggregation(entityGroupBy)) {
      tableName = isGroupByHour(groupBy) || shouldUseHourlyData(getTimeFilters(filters))
          ? CLUSTER_TABLE_HOURLY_AGGREGRATED
          : CLUSTER_TABLE_AGGREGRATED;
    } else {
      tableName =
          isGroupByHour(groupBy) || shouldUseHourlyData(getTimeFilters(filters)) ? CLUSTER_TABLE_HOURLY : CLUSTER_TABLE;
    }
    return format("%s.%s.%s", tableNameSplit[0], tableNameSplit[1], tableName);
  }

  private boolean isGroupByHour(List<QLCEViewGroupBy> groupBy) {
    QLCEViewTimeTruncGroupBy groupByTime = viewsQueryBuilder.getGroupByTime(groupBy);
    return groupByTime != null && groupByTime.getResolution() != null
        && groupByTime.getResolution() == QLCEViewTimeGroupType.HOUR;
  }

  private boolean shouldUseHourlyData(List<QLCEViewTimeFilter> timeFilters) {
    if (!timeFilters.isEmpty()) {
      QLCEViewTimeFilter startTimeFilter =
          timeFilters.stream().filter(timeFilter -> timeFilter.getOperator().equals(AFTER)).findFirst().orElse(null);

      if (startTimeFilter != null) {
        long startTime = startTimeFilter.getValue().longValue();
        ZoneId zoneId = ZoneId.of(STANDARD_TIME_ZONE);
        LocalDate today = LocalDate.now(zoneId);
        ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
        long cutoffTime = zdtStart.toEpochSecond() * 1000 - 7 * ONE_DAY_MILLIS;
        return startTime >= cutoffTime;
      }
    }
    return false;
  }

  private boolean areAggregationsValidForPreAggregation(List<QLCEViewAggregation> aggregateFunctions) {
    if (aggregateFunctions.isEmpty()) {
      return true;
    }
    return !aggregateFunctions.stream().anyMatch(aggregationFunction
        -> aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_CPU_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(EFFECTIVE_MEMORY_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(AVG_CPU_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(AVG_MEMORY_UTILIZATION_VALUE)
            || aggregationFunction.getColumnName().equalsIgnoreCase(CPU_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(CPU_LIMIT)
            || aggregationFunction.getColumnName().equalsIgnoreCase(MEMORY_REQUEST)
            || aggregationFunction.getColumnName().equalsIgnoreCase(MEMORY_LIMIT));
  }

  private boolean isMetricsQuery(List<QLCEViewAggregation> aggregateFunctions) {
    return !areAggregationsValidForPreAggregation(aggregateFunctions);
  }

  // Check for pod/pv/cloudservicename/taskid/launchtype
  private boolean isValidGroupByForPreAggregation(List<QLCEViewFieldInput> groupByList) {
    if (groupByList.isEmpty()) {
      return true;
    }
    return groupByList.stream().noneMatch(groupBy
        -> groupBy.getFieldId().equals(CLOUD_SERVICE_NAME) || groupBy.getFieldId().equals(TASK_ID)
            || groupBy.getFieldId().equals(LAUNCH_TYPE) || groupBy.getFieldId().equals(PRICING_SOURCE));
  }

  private boolean areFiltersValidForPreAggregation(List<QLCEViewFilter> filters) {
    if (filters.isEmpty()) {
      return true;
    }
    return filters.stream().noneMatch(filter
        -> filter.getField().getFieldId().equalsIgnoreCase(INSTANCE_NAME)
            || filter.getField().getFieldId().equalsIgnoreCase(TASK_ID)
            || filter.getField().getFieldId().equalsIgnoreCase(LAUNCH_TYPE)
            || filter.getField().getFieldId().equalsIgnoreCase(CLOUD_SERVICE_NAME)
            || filter.getField().getFieldId().equalsIgnoreCase(PARENT_INSTANCE_ID));
  }

  private boolean isInstanceDetailsQuery(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFieldInput> entityGroupBy = groupByList.stream()
                                                 .filter(groupBy -> groupBy.getEntityGroupBy() != null)
                                                 .map(QLCEViewGroupBy::getEntityGroupBy)
                                                 .collect(Collectors.toList());
    return entityGroupBy.stream().anyMatch(groupBy
        -> groupBy.getFieldName().equals(GROUP_BY_NODE) || groupBy.getFieldName().equals(GROUP_BY_POD)
            || groupBy.getFieldName().equals(GROUP_BY_STORAGE));
  }

  private boolean isPodQuery(List<QLCEViewGroupBy> groupByList) {
    List<QLCEViewFieldInput> entityGroupBy = groupByList.stream()
                                                 .filter(groupBy -> groupBy.getEntityGroupBy() != null)
                                                 .map(QLCEViewGroupBy::getEntityGroupBy)
                                                 .collect(Collectors.toList());
    return entityGroupBy.stream().anyMatch(groupBy -> groupBy.getFieldName().equals(GROUP_BY_POD));
  }

  private QLCEViewFilter getFilterForInstanceDetails(List<QLCEViewGroupBy> groupByList) {
    List<String> entityGroupBy = groupByList.stream()
                                     .filter(groupBy -> groupBy.getEntityGroupBy() != null)
                                     .map(entry -> entry.getEntityGroupBy().getFieldName())
                                     .collect(Collectors.toList());
    String[] values;
    if (entityGroupBy.contains(GROUP_BY_NODE)) {
      values = new String[] {K8S_NODE};
    } else if (entityGroupBy.contains(GROUP_BY_STORAGE)) {
      values = new String[] {K8S_PV};
    } else {
      values = new String[] {K8S_POD, K8S_POD_FARGATE};
    }

    return QLCEViewFilter.builder()
        .field(QLCEViewFieldInput.builder().fieldId(INSTANCE_TYPE).fieldName(INSTANCE_TYPE).identifier(CLUSTER).build())
        .operator(QLCEViewFilterOperator.IN)
        .values(values)
        .build();
  }

  private List<QLCEViewFilterWrapper> getModifiedFilters(
      List<QLCEViewFilterWrapper> filters, QLCEViewGridData gridData) {
    if (filters == null) {
      filters = new ArrayList<>();
    }

    List<String> fields = gridData.getFields();
    Map<String, java.lang.reflect.Field> clusterDataFields = new HashMap<>();
    for (java.lang.reflect.Field clusterField : ClusterData.class.getDeclaredFields()) {
      clusterDataFields.put(clusterField.getName().toLowerCase(), clusterField);
    }

    for (String field : fields) {
      Set<String> values = new HashSet<>();
      try {
        for (QLCEViewEntityStatsDataPoint dataPoint : gridData.getData()) {
          ClusterData clusterData = dataPoint.getClusterData();
          java.lang.reflect.Field clusterField = clusterDataFields.get(field.toLowerCase());
          clusterField.setAccessible(true);
          values.add((String) clusterField.get(clusterData));
        }
      } catch (Exception e) {
        log.error("Unable to fetch field values for filter: {}", e.toString());
      }

      if (!values.isEmpty()) {
        filters.add(QLCEViewFilterWrapper.builder()
                        .idFilter(QLCEViewFilter.builder()
                                      .field(QLCEViewFieldInput.builder()
                                                 .fieldId(field.toLowerCase())
                                                 .fieldName(field.toLowerCase())
                                                 .identifier(CLUSTER)
                                                 .build())
                                      .operator(QLCEViewFilterOperator.IN)
                                      .values(values.toArray(new String[0]))
                                      .build())
                        .build());
      }
    }
    return filters;
  }

  private List<QLCEViewAggregation> getModifiedAggregations(List<QLCEViewAggregation> aggregateFunctions) {
    List<QLCEViewAggregation> modifiedAggregations;
    if (aggregateFunctions == null) {
      return new ArrayList<>();
    }
    boolean isCostAggregationPresent =
        aggregateFunctions.stream().anyMatch(function -> function.getColumnName().equals(COST));
    if (isCostAggregationPresent) {
      modifiedAggregations = aggregateFunctions.stream()
                                 .filter(function -> !function.getColumnName().equals(COST))
                                 .collect(Collectors.toList());
      modifiedAggregations.add(QLCEViewAggregation.builder()
                                   .columnName(BILLING_AMOUNT)
                                   .operationType(QLCEViewAggregateOperation.SUM)
                                   .build());
    } else {
      modifiedAggregations = aggregateFunctions;
    }
    return modifiedAggregations;
  }

  private List<QLCEViewSortCriteria> getModifiedSort(List<QLCEViewSortCriteria> sortCriteria) {
    List<QLCEViewSortCriteria> modifiedSortCriteria;
    if (sortCriteria == null) {
      return new ArrayList<>();
    }
    boolean isSortByCostPresent = sortCriteria.stream().anyMatch(sort -> sort.getSortType() == QLCEViewSortType.COST);
    if (isSortByCostPresent) {
      QLCEViewSortCriteria costSort =
          sortCriteria.stream().filter(sort -> sort.getSortType() == QLCEViewSortType.COST).findFirst().get();
      modifiedSortCriteria = sortCriteria.stream()
                                 .filter(sort -> sort.getSortType() != QLCEViewSortType.COST)
                                 .collect(Collectors.toList());
      modifiedSortCriteria.add(QLCEViewSortCriteria.builder()
                                   .sortOrder(costSort.getSortOrder())
                                   .sortType(QLCEViewSortType.CLUSTER_COST)
                                   .build());
    } else {
      modifiedSortCriteria = sortCriteria;
    }
    return modifiedSortCriteria;
  }

  // Methods for calculating forecast cost
  public Double getForecastCost(ViewCostData costData, Instant endInstant) {
    if (costData == null) {
      return null;
    }
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return null;
    }

    double totalCost = costData.getCost();
    long actualTimeDiffMillis =
        (endInstant.plus(1, ChronoUnit.SECONDS).toEpochMilli()) - (costData.getMaxStartTime() / 1000);

    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (costData.getMaxStartTime() != costData.getMinStartTime()) {
      billingTimeDiffMillis = (costData.getMaxStartTime() - costData.getMinStartTime()) / 1000 + ONE_DAY_MILLIS;
    }
    if (billingTimeDiffMillis < OBSERVATION_PERIOD) {
      return null;
    }

    return totalCost
        * (new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP))
              .doubleValue();
  }

  private Instant getStartInstantForForecastCost() {
    return Instant.ofEpochMilli(viewsQueryHelper.getStartOfCurrentDay());
  }

  private String getInstanceType(Set<String> instanceTypes) {
    if (instanceTypes.contains(K8S_NODE)) {
      return K8S_NODE;
    } else if (instanceTypes.contains(K8S_PV)) {
      return K8S_PV;
    } else {
      return K8S_POD;
    }
  }

  // If conversion  of entity id to name is required
  private List<QLCEViewEntityStatsDataPoint> getUpdatedDataPoints(
      List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints, List<String> entityIds, String harnessAccountId,
      String fieldName) {
    Map<String, String> entityIdToName =
        entityMetadataService.getEntityIdToNameMapping(entityIds, harnessAccountId, fieldName);
    List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
    entityStatsDataPoints.forEach(dataPoint -> {
      final QLCEViewEntityStatsDataPointBuilder qlceViewEntityStatsDataPointBuilder =
          QLCEViewEntityStatsDataPoint.builder();
      qlceViewEntityStatsDataPointBuilder.id(dataPoint.getId())
          .name(entityIdToName.getOrDefault(dataPoint.getName(), dataPoint.getName()))
          .cost(dataPoint.getCost())
          .costTrend(dataPoint.getCostTrend());
      if (AWS_ACCOUNT_FIELD.equals(fieldName)) {
        qlceViewEntityStatsDataPointBuilder.name(AwsAccountFieldHelper.mergeAwsAccountIdAndName(
            dataPoint.getName(), entityIdToName.get(dataPoint.getName())));
      }
      updatedDataPoints.add(qlceViewEntityStatsDataPointBuilder.build());
    });
    return updatedDataPoints;
  }
}
