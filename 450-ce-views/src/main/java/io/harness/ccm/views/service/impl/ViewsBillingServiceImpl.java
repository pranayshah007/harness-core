/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;
import static io.harness.ccm.views.businessmapping.entities.UnallocatedCostStrategy.HIDE;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.CLUSTER;
import static io.harness.ccm.views.graphql.QLCEViewFilterOperator.IN;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantClusterCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantIdleCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMaxStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMinStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantSystemCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantUnallocatedCost;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.LABEL_KEY_ALIAS;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.LABEL_VALUE_ALIAS;
import static io.harness.ccm.views.utils.ClusterTableKeys.ACTUAL_IDLE_COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.COUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_STRING_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.ID_SEPARATOR;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.PRICING_SOURCE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_GRANULARITY;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ClusterData;
import io.harness.ccm.views.entities.ClusterData.ClusterDataBuilder;
import io.harness.ccm.views.entities.EntitySharedCostDetails;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.EfficiencyScoreStats;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewDataPoint;
import io.harness.ccm.views.graphql.QLCEViewDataPoint.QLCEViewDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
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
import io.harness.ccm.views.graphql.ViewCostData.ViewCostDataBuilder;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.ccm.views.helper.BusinessMappingSharedCostHelper;
import io.harness.ccm.views.helper.InstanceDetailsHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewBusinessMappingResponseHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.utils.ViewFieldUtils;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import io.fabric8.utils.Lists;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ViewsBillingServiceImpl implements ViewsBillingService {
  private static final String IDLE_COST_LABEL = "Idle Cost";
  private static final String UNALLOCATED_COST_LABEL = "Unallocated Cost";
  private static final String UTILIZED_COST_LABEL = "Utilized Cost";
  private static final String SYSTEM_COST_LABEL = "System Cost";
  private static final int MAX_LIMIT_VALUE = 10_000;

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
  @Inject private BigQueryService bigQueryService;
  @Inject private BigQueryHelper bigQueryHelper;
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
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
    // Check if cost category filter values are requested
    String businessMappingId = viewsQueryHelper.getBusinessMappingIdFromFilters(filters);
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
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getViewFilterValueStats for query {}", viewsQueryMetadata.getQuery(), e);
      Thread.currentThread().interrupt();
      return null;
    }

    boolean isClusterPerspective =
        viewParametersHelper.isClusterPerspective(filters, Collections.emptyList()) || queryParams.isClusterQuery();

    return getFilterValuesData(queryParams.getAccountId(), viewsQueryMetadata, result, idFilters, isClusterPerspective);
  }

  private List<String> getFilterValuesData(final String harnessAccountId, final ViewsQueryMetadata viewsQueryMetadata,
      final TableResult result, final List<QLCEViewFilter> idFilters, final boolean isClusterQuery) {
    List<String> filterValuesData = convertToFilterValuesData(result, viewsQueryMetadata.getFields(), isClusterQuery);
    if (viewParametersHelper.isDataFilteredByAwsAccount(idFilters)) {
      filterValuesData = awsAccountFieldHelper.mergeAwsAccountNameWithValues(filterValuesData, harnessAccountId);
      filterValuesData = awsAccountFieldHelper.spiltAndSortAWSAccountIdListBasedOnAccountName(filterValuesData);
    }
    return filterValuesData;
  }

  public List<String> convertToFilterValuesData(
      TableResult result, List<QLCEViewFieldInput> viewFieldList, boolean isClusterQuery) {
    List<String> filterValues = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      for (QLCEViewFieldInput field : viewFieldList) {
        final String filterStringValue =
            fetchStringValue(row, viewsQueryBuilder.getModifiedQLCEViewFieldInput(field, isClusterQuery));
        if (Objects.nonNull(filterStringValue)) {
          filterValues.add(filterStringValue);
        }
      }
    }
    return filterValues;
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
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
    boolean isClusterPerspective = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
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
          bigQuery, cloudProviderTableName, isClusterPerspective, viewRules, sharedCostBusinessMappings,
          isGroupByBusinessMapping);
      startTimeForTrendData = viewParametersHelper.getStartTimeForTrendFilters(filters);
    }

    Map<String, Double> sharedCostsFromRulesAndFilters;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      return getEntityStatsSharedCostDataPoints(bigQuery, filters, groupBy, aggregateFunction, sort,
          cloudProviderTableName, limit, offset, queryParams, isClusterPerspective, businessMapping, viewRules,
          sharedCostBusinessMappings, conversionField, costTrendData, startTimeForTrendData);
    } else {
      sharedCostsFromRulesAndFilters =
          getSharedCostFromFilters(bigQuery, filters, groupBy, aggregateFunction, sort, cloudProviderTableName,
              queryParams, sharedCostBusinessMappings, limit, offset, queryParams.isSkipRoundOff(), viewRules);
    }

    SelectQuery query = viewBillingServiceHelper.getQuery(
        filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    log.info("Query for grid (with limit as {}): {}", limit, query);
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getEntityStatsDataPoints for query {}", query, e);
      Thread.currentThread().interrupt();
      return null;
    }

    boolean addSharedCostFromGroupBy = !businessMappingIds.contains(businessMappingId);

    return viewBusinessMappingResponseHelper.costCategoriesPostFetchResponseUpdate(
        convertToEntityStatsData(result, costTrendData, startTimeForTrendData, isClusterPerspective,
            queryParams.isUsedByTimeSeriesStats(), queryParams.isSkipRoundOff(), conversionField,
            queryParams.getAccountId(), groupBy, businessMapping, addSharedCostFromGroupBy),
        businessMappingId, sharedCostBusinessMappings, sharedCostsFromRulesAndFilters);
  }

  @Nullable
  private Map<String, ViewCostData> getEntityStatsCostTrendData(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final Integer limit, final Integer offset,
      final ViewQueryParams queryParams, final BigQuery bigQuery, final String cloudProviderTableName,
      final boolean isClusterPerspective, final List<ViewRule> viewRules,
      final List<BusinessMapping> sharedCostBusinessMappings, final boolean isGroupByBusinessMapping) {
    Map<String, ViewCostData> costTrendData;
    if (!sharedCostBusinessMappings.isEmpty() && !isGroupByBusinessMapping) {
      SelectQuery query = businessMappingSharedCostHelper.getEntityStatsSharedCostDataQueryForCostTrend(filters,
          groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
      final TableResult result = getTableResultWithLimitAndOffset(bigQuery, query, limit, offset);
      costTrendData =
          convertToEntityStatsCostTrendData(result, isClusterPerspective, queryParams.isSkipRoundOff(), groupBy);
    } else {
      costTrendData = getEntityStatsDataForCostTrend(
          bigQuery, filters, groupBy, aggregateFunction, sort, cloudProviderTableName, limit, offset, queryParams);
    }
    return costTrendData;
  }

  @Nullable
  private QLCEViewGridData getEntityStatsSharedCostDataPoints(final BigQuery bigQuery,
      final List<QLCEViewFilterWrapper> filters, final List<QLCEViewGroupBy> groupBy,
      final List<QLCEViewAggregation> aggregateFunction, final List<QLCEViewSortCriteria> sort,
      final String cloudProviderTableName, final Integer limit, final Integer offset, final ViewQueryParams queryParams,
      final boolean isClusterPerspective, final BusinessMapping businessMapping, final List<ViewRule> viewRules,
      final List<BusinessMapping> sharedCostBusinessMappings, final String conversionField,
      final Map<String, ViewCostData> costTrendData, final long startTimeForTrendData) {
    // Group by other than cost category and shared bucket is present in the rules.
    final SelectQuery query = businessMappingSharedCostHelper.getEntityStatsSharedCostDataQuery(filters, groupBy,
        aggregateFunction, sort, cloudProviderTableName, queryParams, sharedCostBusinessMappings, viewRules);
    if (Objects.isNull(query)) {
      return null;
    }
    final TableResult result = getTableResultWithLimitAndOffset(bigQuery, query, limit, offset);
    return convertToEntityStatsData(result, costTrendData, startTimeForTrendData, isClusterPerspective,
        queryParams.isUsedByTimeSeriesStats(), queryParams.isSkipRoundOff(), conversionField,
        queryParams.getAccountId(), groupBy, businessMapping, false);
  }

  // Here conversion field is not null if id to name conversion is required for the main group by field
  private QLCEViewGridData convertToEntityStatsData(TableResult result, Map<String, ViewCostData> costTrendData,
      long startTimeForTrend, boolean isClusterPerspective, boolean isUsedByTimeSeriesStats, boolean skipRoundOff,
      String conversionField, String accountId, List<QLCEViewGroupBy> groupBy, BusinessMapping businessMapping,
      boolean addSharedCostFromGroupBy) {
    if (isClusterPerspective) {
      return convertToEntityStatsDataForCluster(
          result, costTrendData, startTimeForTrend, isUsedByTimeSeriesStats, skipRoundOff, groupBy, accountId);
    }
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    List<String> fieldNames = getFieldNames(fields);
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    List<String> entityNames = new ArrayList<>();

    List<String> sharedCostBucketNames = new ArrayList<>();
    Map<String, Double> sharedCosts = new HashMap<>();
    if (businessMapping != null && businessMapping.getSharedCosts() != null) {
      List<SharedCost> sharedCostBuckets = businessMapping.getSharedCosts();
      sharedCostBucketNames =
          sharedCostBuckets.stream()
              .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
              .collect(Collectors.toList());
      sharedCostBucketNames.forEach(sharedCostBucketName -> sharedCosts.put(sharedCostBucketName, 0.0));
    }

    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    for (FieldValueList row : result.iterateAll()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      Double cost = null;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String id = DEFAULT_STRING_VALUE;
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case STRING:
            name = fetchStringValue(row, field, fieldName);
            entityNames.add(name);
            id = getUpdatedId(id, name);
            break;
          case FLOAT64:
            if (field.getName().equalsIgnoreCase(COST)) {
              cost = getNumericValue(row, field, skipRoundOff);
              dataPointBuilder.cost(cost);
            } else if (sharedCostBucketNames.contains(field.getName())) {
              sharedCosts.put(
                  field.getName(), sharedCosts.get(field.getName()) + getNumericValue(row, field, skipRoundOff));
            }
            break;
          default:
            break;
        }
      }
      dataPointBuilder.id(id);
      dataPointBuilder.name(name);
      if (!isUsedByTimeSeriesStats) {
        dataPointBuilder.costTrend(
            viewBillingServiceHelper.getCostTrendForEntity(cost, costTrendData.get(id), startTimeForTrend));
      }
      entityStatsDataPoints.add(dataPointBuilder.build());
    }

    if (conversionField != null) {
      entityStatsDataPoints =
          viewBillingServiceHelper.getUpdatedDataPoints(entityStatsDataPoints, entityNames, accountId, conversionField);
    }

    // TODO: Remove this code because addSharedCostFromGroupBy will always false.
    if (businessMapping != null && !sharedCostBucketNames.isEmpty() && addSharedCostFromGroupBy) {
      entityStatsDataPoints =
          viewBusinessMappingResponseHelper.addSharedCosts(entityStatsDataPoints, sharedCosts, businessMapping);
    }

    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size: {}", entityStatsDataPoints.size());
    }
    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fieldNames).build();
  }

  private QLCEViewGridData convertToEntityStatsDataForCluster(TableResult result,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isUsedByTimeSeriesStats,
      boolean skipRoundOff, List<QLCEViewGroupBy> groupBy, String accountId) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();

    List<String> fieldNames = getFieldNames(fields);
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
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
              builderField.set(clusterDataBuilder, fetchStringValue(row, field, fieldName));
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
                pricingSource = fetchStringValue(row, field, fieldName);
                break;
              default:
                break;
            }
          }
        } catch (Exception e) {
          log.error("Exception in convertToEntityStatsDataForCluster: {}", e.toString());
        }

        if (field.getType().getStandardType() == StandardSQLTypeName.STRING) {
          name = fetchStringValue(row, field, fieldName);
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
        dataPointBuilder.costTrend(
            viewBillingServiceHelper.getCostTrendForEntity(cost, costTrendData.get(entityId), startTimeForTrend));
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
          .data(instanceDetailsHelper.getInstanceDetails(
              entityStatsDataPoints, viewParametersHelper.getInstanceType(instanceTypes), accountId))
          .fields(fieldNames)
          .build();
    }
    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size (for cluster): {}", entityStatsDataPoints.size());
    }
    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fieldNames).build();
  }

  // Methods to get cost trend for grid data
  private Map<String, ViewCostData> getEntityStatsDataForCostTrend(BigQuery bigQuery,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewSortCriteria> sort, String cloudProviderTableName, Integer limit, Integer offset,
      ViewQueryParams queryParams) {
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
    SelectQuery query =
        viewBillingServiceHelper.getQuery(viewParametersHelper.getFiltersForEntityStatsCostTrend(filters), groupBy,
            viewParametersHelper.getAggregationsForEntityStatsCostTrend(aggregateFunction), sort,
            cloudProviderTableName, queryParams, Collections.emptyList());
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    log.info("Query for cost trend (with limit as {}): {}", limit, query);
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getEntityStatsDataForCostTrend for account {}", queryParams.getAccountId(), e);
      Thread.currentThread().interrupt();
      return null;
    }
    return convertToEntityStatsCostTrendData(result, isClusterTableQuery, queryParams.isSkipRoundOff(), groupBy);
  }

  private Map<String, ViewCostData> convertToEntityStatsCostTrendData(
      TableResult result, boolean isClusterTableQuery, boolean skipRoundOff, List<QLCEViewGroupBy> groupBy) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    for (FieldValueList row : result.iterateAll()) {
      String name = "";
      String id = DEFAULT_STRING_VALUE;
      ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case STRING:
            name = fetchStringValue(row, field, fieldName);
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
    if (costTrendData.size() > MAX_LIMIT_VALUE) {
      log.warn("Cost trend result set size: {}", costTrendData.size());
    }
    return costTrendData;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get data for Chart
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public TableResult getTimeSeriesStats(String accountId, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    SelectQuery query =
        viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, true);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      return bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTimeSeriesStats for account {}", accountId, e);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  @Override
  public TableResult getTimeSeriesStatsNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, boolean includeOthers,
      Integer limit, ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
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

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      return bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTimeSeriesStats for query: {}", query, e);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  public List<QLCEViewTimeSeriesData> convertToQLViewTimeSeriesData(
      TableResult result, String accountId, List<QLCEViewGroupBy> groupBy) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);

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
            String stringValue = fetchStringValue(row, field, fieldName);
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

    return viewBillingServiceHelper.convertTimeSeriesPointsMapToList(
        viewBillingServiceHelper.modifyTimeSeriesDataPointsMap(timeSeriesDataPointsMap, awsAccounts, accountId));
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to get cost overview and forecasted cost
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public Map<Long, Double> getUnallocatedCostDataNg(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewSortCriteria> sort, final ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
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
      final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
      try {
        return convertToCostData(bigQuery.query(queryConfig));
      } catch (final InterruptedException e) {
        log.error("Failed to getUnallocatedCostDataNg for query: {}", query, e);
        Thread.currentThread().interrupt();
      }
    }
    return Collections.emptyMap();
  }

  @Override
  public Map<Long, Double> getOthersTotalCostDataNg(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewSortCriteria> sort, final ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
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
    final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      return convertToCostData(bigQuery.query(queryConfig));
    } catch (final InterruptedException e) {
      log.error("Failed to getOthersTotalCostDataNg for query: {}", query, e);
      Thread.currentThread().interrupt();
    }
    return Collections.emptyMap();
  }

  private ViewCostData convertToTrendStatsData(TableResult result, boolean isClusterTableQuery,
      BusinessMapping businessMappingFromGroupBy, boolean addSharedCostFromGroupBy,
      double sharedCostFromFiltersAndRules) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();

    List<String> sharedCostBucketNames = new ArrayList<>();
    if (businessMappingFromGroupBy != null && businessMappingFromGroupBy.getSharedCosts() != null) {
      List<SharedCost> sharedCostBuckets = businessMappingFromGroupBy.getSharedCosts();
      sharedCostBucketNames =
          sharedCostBuckets.stream()
              .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
              .collect(Collectors.toList());
    }

    boolean includeOthersCost = !addSharedCostFromGroupBy || businessMappingFromGroupBy == null
        || businessMappingFromGroupBy.getUnallocatedCost() == null
        || businessMappingFromGroupBy.getUnallocatedCost().getStrategy() != HIDE;

    double totalCost = 0.0;
    Double idleCost = null;
    Double unallocatedCost = null;
    double sharedCost = 0.0;
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(Collections.emptyList());
    for (FieldValueList row : result.iterateAll()) {
      double cost = 0.0;
      String entityName = null;
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
            cost = getNumericValue(row, field);
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
            if (sharedCostBucketNames.contains(field.getName())) {
              sharedCost += getNumericValue(row, field);
            }
            if (field.getType().getStandardType() == StandardSQLTypeName.STRING) {
              entityName = fetchStringValue(row, field, fieldName);
            }
            break;
        }
      }
      if (entityName == null
          || (includeOthersCost || !entityName.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName()))) {
        totalCost += cost;
      }
    }

    if (!addSharedCostFromGroupBy) {
      sharedCost = 0.0D;
    }

    totalCost = viewsQueryHelper.getRoundedDoubleValue(totalCost + sharedCost + sharedCostFromFiltersAndRules);
    viewCostDataBuilder.cost(totalCost);
    if (idleCost != null) {
      double utilizedCost = totalCost - idleCost;
      if (unallocatedCost != null) {
        utilizedCost -= unallocatedCost;
      }
      viewCostDataBuilder.utilizedCost(viewsQueryHelper.getRoundedDoubleValue(utilizedCost));
    }
    return viewCostDataBuilder.build();
  }

  private Map<Long, Double> convertToCostData(final TableResult result) {
    Map<Long, Double> costMapping = new HashMap<>();
    final Schema schema = result.getSchema();
    final FieldList fields = schema.getFields();
    for (final FieldValueList row : result.iterateAll()) {
      long timestamp = 0L;
      double cost = 0.0D;
      for (final Field field : fields) {
        switch (field.getType().getStandardType()) {
          case TIMESTAMP:
            timestamp = row.get(field.getName()).getTimestampValue() / 1000;
            break;
          case FLOAT64:
            cost = getNumericValue(row, field, true);
            break;
          default:
            break;
        }
      }
      if (cost != 0L) {
        costMapping.put(timestamp, cost);
      }
    }
    return costMapping;
  }

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
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
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

    double sharedCostFromRulesAndFilters = getTotalSharedCostFromFilters(bigQuery, filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    double prevSharedCostFromRulesAndFilters = getTotalSharedCostFromFilters(bigQuery, filtersForPrevPeriod, groupBy,
        aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    ViewCostData costData = getViewTrendStatsCostData(
        bigQuery, query, isClusterTableQuery, businessMapping, addSharedCostFromGroupBy, sharedCostFromRulesAndFilters);
    ViewCostData prevCostData = getViewTrendStatsCostData(bigQuery, prevTrendStatsQuery, isClusterTableQuery,
        businessMapping, addSharedCostFromGroupBy, prevSharedCostFromRulesAndFilters);

    EfficiencyScoreStats efficiencyScoreStats = null;
    if (isClusterTableQuery) {
      efficiencyScoreStats = viewsQueryHelper.getEfficiencyScoreStats(costData, prevCostData);
    }
    Currency currency = getDestinationCurrency(queryParams.getAccountId());

    return QLCEViewTrendData.builder()
        .totalCost(viewBillingServiceHelper.getCostBillingStats(
            costData, prevCostData, timeFilters, trendStartInstant, isClusterTableQuery, currency))
        .idleCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, IDLE_COST_LABEL, currency))
        .unallocatedCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, UNALLOCATED_COST_LABEL, currency))
        .systemCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, SYSTEM_COST_LABEL, currency))
        .utilizedCost(viewBillingServiceHelper.getOtherCostBillingStats(costData, UTILIZED_COST_LABEL, currency))
        .efficiencyScoreStats(efficiencyScoreStats)
        .build();
  }

  private Currency getDestinationCurrency(String accountId) {
    Currency currency = ceMetadataRecordDao.getDestinationCurrency(accountId);
    if (Currency.NONE == currency) {
      currency = Currency.USD;
    }
    return currency;
  }

  @Override
  public QLCEViewTrendInfo getForecastCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    Instant endInstantForForecastCost = viewsQueryHelper.getEndInstantForForecastCost(filters);
    ViewCostData currentCostData =
        getCostData(viewsQueryHelper.getFiltersForForecastCost(filters), groupBy, aggregateFunction, queryParams);
    Double forecastCost = viewBillingServiceHelper.getForecastCost(currentCostData, endInstantForForecastCost);
    Currency currency = getDestinationCurrency(queryParams.getAccountId());
    return viewBillingServiceHelper.getForecastCostBillingStats(forecastCost, currentCostData.getCost(),
        viewParametersHelper.getStartInstantForForecastCost(), endInstantForForecastCost.plus(1, ChronoUnit.SECONDS),
        currency);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get columns of given table
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public List<String> getColumnsForTable(String informationSchemaView, String table) {
    BigQuery bigQuery = bigQueryService.get();
    SelectQuery query = viewsQueryBuilder.getInformationSchemaQueryForColumns(informationSchemaView, table);
    return getColumnsData(bigQuery, query);
  }

  private List<String> getColumnsData(BigQuery bigQuery, SelectQuery query) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTrendStatsData.", e);
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

  // ----------------------------------------------------------------------------------------------------------------
  // Method to check if given perspective is cluster only perspective
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public boolean isClusterPerspective(final List<QLCEViewFilterWrapper> filters, final List<QLCEViewGroupBy> groupBy) {
    return viewParametersHelper.isClusterPerspective(filters, groupBy);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to get cost data
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public ViewCostData getCostData(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    return getCostData(filters, null, aggregateFunction, queryParams);
  }

  @Override
  public ViewCostData getCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);
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
    double sharedCostFromFiltersAndRules = getTotalSharedCostFromFilters(bigQuery, filters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMappings);
    return getViewTrendStatsCostData(
        bigQuery, query, isClusterTableQuery, businessMapping, addSharedCostFromGroupBy, sharedCostFromFiltersAndRules);
  }

  private ViewCostData getViewTrendStatsCostData(BigQuery bigQuery, SelectQuery query, boolean isClusterTableQuery,
      BusinessMapping businessMappingFromGroupBy, boolean addSharedCostFromGroupBy,
      double sharedCostFromFiltersAndRules) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTrendStatsData.", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return convertToTrendStatsData(result, isClusterTableQuery, businessMappingFromGroupBy, addSharedCostFromGroupBy,
        sharedCostFromFiltersAndRules);
  }

  @Override
  public List<ValueDataPoint> getActualCostGroupedByPeriod(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
    boolean isClusterTableQuery = viewParametersHelper.isClusterTableQuery(filters, groupBy, queryParams);

    SelectQuery query = viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
        cloudProviderTableName, queryParams, Collections.emptyList());
    log.info("getActualCostGroupedByPeriod() query formed: " + query.toString());
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getActualCostGroupedByPeriod() while running the bugQuery", e);
      Thread.currentThread().interrupt();
      return null;
    }

    String colName = isClusterTableQuery ? BILLING_AMOUNT : COST;
    List<ValueDataPoint> costs = new ArrayList<>();

    for (FieldValueList row : result.iterateAll()) {
      ValueDataPoint costData =
          ValueDataPoint.builder()
              .time(row.get(TIME_GRANULARITY).getTimestampValue() / 1000)
              .value(BudgetUtils.getRoundedValue(row.get(colName).getNumericValue().doubleValue()))
              .build();
      costs.add(costData);
    }
    return costs;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get total count of rows returned by the query
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public Integer getTotalCountForQuery(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, ViewQueryParams queryParams) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);

    SelectQuery query = getTotalCountQuery(filters, groupBy, cloudProviderTableName, queryParams);

    if (Objects.isNull(query)) {
      return null;
    }

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getTotalCountForQuery.", e);
      Thread.currentThread().interrupt();
      return null;
    }
    Integer totalCount = null;
    for (FieldValueList row : result.iterateAll()) {
      totalCount = row.get(COUNT).getNumericValue().intValue();
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

  // ----------------------------------------------------------------------------------------------------------------
  // Method to check if data is grouped by AWS account
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy) {
    return viewParametersHelper.isDataGroupedByAwsAccount(filters, groupBy);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get labels for workloads
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public Map<String, Map<String, String>> getLabelsForWorkloads(
      String accountId, Set<String> workloads, List<QLCEViewFilterWrapper> filters) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
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
    SelectQuery query = viewsQueryBuilder.getLabelsForWorkloadsQuery(cloudProviderTableName, idFilters, timeFilters);
    return getLabelsForWorkloadsData(bigQuery, query);
  }

  private Map<String, Map<String, String>> getLabelsForWorkloadsData(BigQuery bigQuery, SelectQuery query) {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getLabelsForWorkloadsData.", e);
      Thread.currentThread().interrupt();
      return null;
    }
    Map<String, Map<String, String>> workloadToLabelsMapping = new HashMap<>();
    for (FieldValueList row : result.iterateAll()) {
      String workload = row.get(WORKLOAD_NAME).getValue().toString();
      String labelKey = row.get(LABEL_KEY_ALIAS).getValue().toString();
      String labelValue = row.get(LABEL_VALUE_ALIAS).getValue().toString();
      Map<String, String> labels = new HashMap<>();
      if (workloadToLabelsMapping.containsKey(workload)) {
        labels = workloadToLabelsMapping.get(workload);
      }
      labels.put(labelKey, labelValue);
      workloadToLabelsMapping.put(workload, labels);
    }
    return workloadToLabelsMapping;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get shared cost per timestamp for charts (Cost category)
  // ----------------------------------------------------------------------------------------------------------------
  @Override
  public Map<String, Map<Timestamp, Double>> getSharedCostPerTimestampFromFilters(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      ViewQueryParams queryParams, boolean skipRoundOff) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(queryParams.getAccountId(), UNIFIED_TABLE);
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
      QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
      TableResult result;
      try {
        result = bigQuery.query(queryConfig);
      } catch (InterruptedException e) {
        log.error("Failed to getSharedCostFromFilters.", e);
        Thread.currentThread().interrupt();
        return null;
      }

      List<String> sharedCostBucketNames =
          sharedCostBusinessMapping.getSharedCosts()
              .stream()
              .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
              .collect(Collectors.toList());
      List<String> costTargetBucketNames =
          sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());

      Schema schema = result.getSchema();
      FieldList fields = schema.getFields();
      String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
      Map<String, Double> entityCosts = new HashMap<>();
      costTargetBucketNames.forEach(costTarget -> entityCosts.put(costTarget, 0.0));
      double totalCost = 0.0;
      double numberOfEntities = costTargetBucketNames.size();
      Set<Timestamp> timestamps = new HashSet<>();
      for (FieldValueList row : result.iterateAll()) {
        Timestamp timestamp = null;
        String name = DEFAULT_GRID_ENTRY_NAME;
        Double cost = 0.0;
        Map<String, Double> sharedCostsPerTimestamp = new HashMap<>();
        sharedCostBucketNames.forEach(sharedCostBucketName -> sharedCostsPerTimestamp.put(sharedCostBucketName, 0.0));
        for (Field field : fields) {
          switch (field.getType().getStandardType()) {
            case STRING:
              name = fetchStringValue(row, field, fieldName);
              break;
            case TIMESTAMP:
              timestamp = Timestamp.ofTimeMicroseconds(row.get(field.getName()).getTimestampValue());
              timestamps.add(timestamp);
              break;
            case FLOAT64:
              if (field.getName().equalsIgnoreCase(COST) || field.getName().equalsIgnoreCase(BILLING_AMOUNT)) {
                cost = getNumericValue(row, field, skipRoundOff);
              } else if (sharedCostBucketNames.contains(field.getName())) {
                sharedCostsPerTimestamp.put(field.getName(),
                    sharedCostsPerTimestamp.get(field.getName()) + getNumericValue(row, field, skipRoundOff));
              }
              break;
            default:
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

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get view rules from filters
  // ----------------------------------------------------------------------------------------------------------------
  public List<ViewRule> getViewRules(List<QLCEViewFilterWrapper> filters) {
    return viewParametersHelper.getViewRules(filters);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get total shared cost value
  // ----------------------------------------------------------------------------------------------------------------
  private double getTotalSharedCostFromFilters(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, ViewQueryParams queryParams, List<BusinessMapping> sharedCostBusinessMappings) {
    double totalSharedCost = 0.0;
    List<ViewRule> viewRules = getViewRules(filters);
    if (!sharedCostBusinessMappings.isEmpty()) {
      Map<String, Double> sharedCostsFromRulesAndFilters =
          getSharedCostFromFilters(bigQuery, filters, groupBy, aggregateFunction, sort, cloudProviderTableName,
              queryParams, sharedCostBusinessMappings, MAX_LIMIT_VALUE, 0, false, viewRules);
      for (String entry : sharedCostsFromRulesAndFilters.keySet()) {
        totalSharedCost += sharedCostsFromRulesAndFilters.get(entry);
      }
    }
    return totalSharedCost;
  }

  private Map<String, Double> getSharedCostFromFilters(BigQuery bigQuery, List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      String cloudProviderTableName, ViewQueryParams queryParams, List<BusinessMapping> sharedCostBusinessMappings,
      Integer limit, Integer offset, boolean skipRoundOff, List<ViewRule> viewRules) {
    Map<String, Double> sharedCostsFromFilters = new HashMap<>();
    String groupByBusinessMappingId = viewsQueryHelper.getBusinessMappingIdFromGroupBy(groupBy);

    for (BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      final boolean groupByCurrentBusinessMapping =
          groupByBusinessMappingId != null && groupByBusinessMappingId.equals(sharedCostBusinessMapping.getUuid());

      List<QLCEViewGroupBy> businessMappingGroupBy =
          viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping);
      SelectQuery query = viewBillingServiceHelper.getQuery(
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid()), groupBy,
          businessMappingGroupBy, aggregateFunction, sort, cloudProviderTableName, queryParams,
          sharedCostBusinessMapping, Collections.emptyList());
      TableResult result = getTableResultWithLimitAndOffset(bigQuery, query, limit, offset);

      if (Objects.isNull(result)) {
        return Collections.emptyMap();
      }

      Map<String, Double> sharedCosts = new HashMap<>();
      Map<String, Double> entityCosts = new HashMap<>();
      List<String> sharedCostBucketNames =
          sharedCostBusinessMapping.getSharedCosts()
              .stream()
              .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
              .collect(Collectors.toList());
      List<String> costTargetBucketNames =
          sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());

      sharedCostBucketNames.forEach(sharedCostBucketName -> sharedCosts.put(sharedCostBucketName, 0.0));

      Schema schema = result.getSchema();
      FieldList fields = schema.getFields();
      String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
      double totalCost = 0.0;
      for (FieldValueList row : result.iterateAll()) {
        String name = DEFAULT_GRID_ENTRY_NAME;
        Double cost = null;
        for (Field field : fields) {
          switch (field.getType().getStandardType()) {
            case STRING:
              name = fetchStringValue(row, field, fieldName);
              break;
            case FLOAT64:
              if (field.getName().equalsIgnoreCase(COST)) {
                cost = getNumericValue(row, field, skipRoundOff);
              } else if (sharedCostBucketNames.contains(field.getName())) {
                sharedCosts.put(
                    field.getName(), sharedCosts.get(field.getName()) + getNumericValue(row, field, skipRoundOff));
              }
              break;
            default:
              break;
          }
        }
        if (Objects.nonNull(cost) && costTargetBucketNames.contains(name)) {
          entityCosts.put(name, cost);
          totalCost += cost;
        }
      }

      Map<String, List<EntitySharedCostDetails>> entitySharedCostDetails =
          viewBusinessMappingResponseHelper.calculateSharedCostPerEntity(
              sharedCostBusinessMapping, sharedCosts, entityCosts, totalCost);

      List<String> selectedCostTargets =
          viewsQueryHelper.getSelectedCostTargetsFromFilters(filters, viewRules, sharedCostBusinessMapping);

      for (String entity : entitySharedCostDetails.keySet()) {
        if (selectedCostTargets.contains(entity) || (groupByCurrentBusinessMapping && selectedCostTargets.isEmpty())) {
          entitySharedCostDetails.get(entity).forEach(sharedCostBucket -> {
            if (groupByCurrentBusinessMapping) {
              if (!sharedCostsFromFilters.containsKey(entity)) {
                sharedCostsFromFilters.put(entity, 0.0);
              }
              sharedCostsFromFilters.put(entity, sharedCostsFromFilters.get(entity) + sharedCostBucket.getCost());
            } else {
              if (!sharedCostsFromFilters.containsKey(fieldName)) {
                sharedCostsFromFilters.put(fieldName, 0.0);
              }
              sharedCostsFromFilters.put(fieldName, sharedCostsFromFilters.get(fieldName) + sharedCostBucket.getCost());
            }
          });
        }
      }
    }
    return sharedCostsFromFilters;
  }

  private TableResult getTableResultWithLimitAndOffset(
      final BigQuery bigQuery, final SelectQuery query, final Integer limit, final Integer offset) {
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    log.info("Query for shared cost (with limit as {}): {}", limit, query);
    TableResult result = null;
    try {
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to get query result", e);
      Thread.currentThread().interrupt();
    }
    return result;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to fetch fields from response
  // ----------------------------------------------------------------------------------------------------------------
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

  private String fetchStringValue(FieldValueList row, Field field, String fieldName) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return fieldName;
  }

  private String fetchStringValue(FieldValueList row, QLCEViewFieldInput field) {
    Object value = row.get(viewsQueryBuilder.getAliasFromField(field)).getValue();
    if (value != null) {
      return value.toString();
    }
    return null;
  }
}
