/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.ccm.commons.constants.DataTypeConstants.DATE;
import static io.harness.ccm.commons.constants.DataTypeConstants.DATETIME;
import static io.harness.ccm.commons.constants.DataTypeConstants.FLOAT64;
import static io.harness.ccm.commons.constants.DataTypeConstants.STRING;
import static io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy.HIDE;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantClusterCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantIdleCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMaxStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMinStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantSystemCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantUnallocatedCost;
import static io.harness.ccm.views.utils.ClusterTableKeys.ACTUAL_IDLE_COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.AVG_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_DOUBLE_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_STRING_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.MAX_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MAX_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.PRICING_SOURCE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.SharedCostParameters;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.DataPoint;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.dto.Reference;
import io.harness.ccm.views.entities.ClusterData;
import io.harness.ccm.views.entities.ClusterData.ClusterDataBuilder;
import io.harness.ccm.views.entities.EntitySharedCostDetails;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewCostData.ViewCostDataBuilder;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;

import com.google.cloud.Timestamp;
import com.google.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClickHouseQueryResponseHelper {
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;
  @Inject EntityMetadataService entityMetadataService;
  @Inject BusinessMappingService businessMappingService;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private InstanceDetailsHelper instanceDetailsHelper;
  @Inject PerspectiveTimeSeriesResponseHelper perspectiveTimeSeriesResponseHelper;
  @Inject ViewBusinessMappingResponseHelper viewBusinessMappingResponseHelper;
  private static final int MAX_LIMIT_VALUE = 10_000;

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for filter panel
  // ----------------------------------------------------------------------------------------------------------------
  public List<String> getFilterValuesData(final String harnessAccountId, final ViewsQueryMetadata viewsQueryMetadata,
      final ResultSet resultSet, final List<QLCEViewFilter> idFilters, final boolean isClusterQuery)
      throws SQLException {
    List<String> filterValuesData =
        convertToFilterValuesData(resultSet, viewsQueryMetadata.getFields(), isClusterQuery);
    if (viewParametersHelper.isDataFilteredByAwsAccount(idFilters)) {
      filterValuesData = awsAccountFieldHelper.mergeAwsAccountNameWithValues(filterValuesData, harnessAccountId);
      filterValuesData = awsAccountFieldHelper.spiltAndSortAWSAccountIdListBasedOnAccountName(filterValuesData);
    }
    return filterValuesData;
  }

  private List<String> convertToFilterValuesData(
      ResultSet resultSet, List<QLCEViewFieldInput> viewFieldList, boolean isClusterQuery) throws SQLException {
    List<String> filterValues = new ArrayList<>();
    while (resultSet != null && resultSet.next()) {
      for (QLCEViewFieldInput field : viewFieldList) {
        final String filterStringValue =
            fetchStringValue(resultSet, viewsQueryBuilder.getModifiedQLCEViewFieldInput(field, isClusterQuery));
        if (Objects.nonNull(filterStringValue) && !filterStringValue.isEmpty()) {
          filterValues.add(filterStringValue);
        }
      }
    }
    return filterValues;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for Grid
  // ----------------------------------------------------------------------------------------------------------------
  // Here conversion field is not null if id to name conversion is required for the main group by field
  public QLCEViewGridData convertToEntityStatsData(ResultSet resultSet, String cloudProviderTableName,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isClusterPerspective,
      boolean isUsedByTimeSeriesStats, boolean skipRoundOff, String conversionField, String accountId,
      List<QLCEViewGroupBy> groupBy, BusinessMapping businessMapping, boolean addSharedCostFromGroupBy)
      throws SQLException {
    if (isClusterPerspective) {
      return convertToEntityStatsDataForCluster(resultSet, costTrendData, startTimeForTrend, isUsedByTimeSeriesStats,
          skipRoundOff, groupBy, accountId, cloudProviderTableName);
    }
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    List<String> entityNames = new ArrayList<>();
    Map<String, String> fieldToDataTypeMapping = getFieldToDataTypeMapping(resultSet);
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
    double totalSharedCostInUnattributed = 0.0D;
    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      int columnIndex = 1;
      Double cost = null;
      double sharedCostInUnattributed = 0.0D;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String id = DEFAULT_STRING_VALUE;

      while (columnIndex <= totalColumns) {
        String columnName = resultSet.getMetaData().getColumnName(columnIndex);
        String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
          name = fetchStringValue(resultSet, columnName, fieldName);
          id = perspectiveTimeSeriesResponseHelper.getUpdatedId(id, name);
          entityNames.add(name);
        } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
          if (columnName.equalsIgnoreCase(COST)) {
            cost = fetchNumericValue(resultSet, columnName, skipRoundOff);
            dataPointBuilder.cost(cost);
          } else if (sharedCostBucketNames.contains(columnName)) {
            sharedCostInUnattributed = fetchNumericValue(resultSet, columnName, skipRoundOff);
            sharedCosts.put(
                columnName, sharedCosts.get(columnName) + fetchNumericValue(resultSet, columnName, skipRoundOff));
          }
        }
        columnIndex++;
      }
      dataPointBuilder.id(id);
      dataPointBuilder.name(name);
      if (!isUsedByTimeSeriesStats) {
        dataPointBuilder.costTrend(
            viewBillingServiceHelper.getCostTrendForEntity(cost, costTrendData.get(id), startTimeForTrend));
      }
      if (businessMapping != null && businessMapping.getUnallocatedCost() != null
          && name.equals(businessMapping.getUnallocatedCost().getLabel())) {
        totalSharedCostInUnattributed += sharedCostInUnattributed;
      }
      entityStatsDataPoints.add(dataPointBuilder.build());
    }

    if (conversionField != null) {
      entityStatsDataPoints =
          viewBillingServiceHelper.getUpdatedDataPoints(entityStatsDataPoints, entityNames, accountId, conversionField);
    }

    if (businessMapping != null) {
      if (!sharedCostBucketNames.isEmpty() && addSharedCostFromGroupBy) {
        entityStatsDataPoints =
            viewBusinessMappingResponseHelper.addSharedCosts(entityStatsDataPoints, sharedCosts, businessMapping);
      }
      entityStatsDataPoints = viewBusinessMappingResponseHelper.subtractDuplicateSharedCostFromUnattributed(
          entityStatsDataPoints, totalSharedCostInUnattributed, businessMapping);
    }

    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size: {}", entityStatsDataPoints.size());
    }
    return QLCEViewGridData.builder()
        .data(entityStatsDataPoints)
        .fields(getStringFieldNames(fieldToDataTypeMapping, cloudProviderTableName))
        .build();
  }

  private QLCEViewGridData convertToEntityStatsDataForCluster(ResultSet resultSet,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isUsedByTimeSeriesStats,
      boolean skipRoundOff, List<QLCEViewGroupBy> groupBy, String accountId, String cloudProviderTableName)
      throws SQLException {
    Map<String, String> fieldToDataTypeMapping = getFieldToDataTypeMapping(resultSet);
    List<String> fields = new ArrayList<>(fieldToDataTypeMapping.keySet());
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    String fieldId = viewParametersHelper.getEntityGroupByFieldId(groupBy);
    boolean isInstanceDetailsData = fields.contains(INSTANCE_ID);
    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    Set<String> instanceTypes = new HashSet<>();
    while (resultSet != null && resultSet.next()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      ClusterDataBuilder clusterDataBuilder = ClusterData.builder();
      Double cost = null;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String nameForGroupByField = DEFAULT_GRID_ENTRY_NAME;
      String entityId = DEFAULT_STRING_VALUE;
      String pricingSource = DEFAULT_STRING_VALUE;

      Map<String, java.lang.reflect.Field> builderFields = new HashMap<>();
      for (java.lang.reflect.Field builderField : clusterDataBuilder.getClass().getDeclaredFields()) {
        builderFields.put(builderField.getName().toLowerCase(), builderField);
      }
      for (String field : fields) {
        java.lang.reflect.Field builderField = builderFields.get(field.toLowerCase(Locale.ROOT));
        try {
          if (builderField != null) {
            builderField.setAccessible(true);
            if (builderField.getType().equals(String.class)) {
              builderField.set(clusterDataBuilder, fetchStringValue(resultSet, field, fieldName));
            } else {
              builderField.set(clusterDataBuilder, fetchNumericValue(resultSet, field, skipRoundOff));
            }
          } else {
            switch (field.toLowerCase(Locale.ROOT)) {
              case BILLING_AMOUNT:
                cost = fetchNumericValue(resultSet, field, skipRoundOff);
                clusterDataBuilder.totalCost(cost);
                break;
              case ACTUAL_IDLE_COST:
                clusterDataBuilder.idleCost(fetchNumericValue(resultSet, field, skipRoundOff));
                break;
              case PRICING_SOURCE:
                pricingSource = fetchStringValue(resultSet, field);
                break;
              default:
                break;
            }
          }
        } catch (Exception e) {
          log.error("Exception in convertToEntityStatsDataForCluster: {}", e.toString());
        }
        if (fieldToDataTypeMapping.get(field).toUpperCase(Locale.ROOT).contains(STRING)) {
          name = fetchStringValue(resultSet, field, fieldName);
          entityId = perspectiveTimeSeriesResponseHelper.getUpdatedId(entityId, name);
          if (field.equalsIgnoreCase(fieldId)) {
            nameForGroupByField = name;
          }
        }
      }
      clusterDataBuilder.id(entityId);
      clusterDataBuilder.name(nameForGroupByField);
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
      dataPointBuilder.name(nameForGroupByField);
      dataPointBuilder.pricingSource(pricingSource);
      entityStatsDataPoints.add(dataPointBuilder.build());
    }
    if (isInstanceDetailsData && !isUsedByTimeSeriesStats) {
      return QLCEViewGridData.builder()
          .data(instanceDetailsHelper.getInstanceDetails(
              entityStatsDataPoints, viewParametersHelper.getInstanceType(instanceTypes), accountId))
          .fields(fields)
          .build();
    }
    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size (for cluster): {}", entityStatsDataPoints.size());
    }
    return QLCEViewGridData.builder()
        .data(entityStatsDataPoints)
        .fields(getStringFieldNames(fieldToDataTypeMapping, cloudProviderTableName))
        .build();
  }

  public Map<String, ViewCostData> convertToEntityStatsCostTrendData(ResultSet resultSet, boolean isClusterTableQuery,
      boolean skipRoundOff, List<QLCEViewGroupBy> groupBy) throws SQLException {
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    Map<String, String> fieldToDataTypeMapping = getFieldToDataTypeMapping(resultSet);
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      String name;
      String id = DEFAULT_STRING_VALUE;
      ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();
      int columnIndex = 1;
      while (columnIndex <= totalColumns) {
        String columnName = resultSet.getMetaData().getColumnName(columnIndex);
        String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
          name = fetchStringValue(resultSet, columnName, fieldName);
          id = perspectiveTimeSeriesResponseHelper.getUpdatedId(id, name);
        } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
          if (columnName.equalsIgnoreCase(COST)) {
            viewCostDataBuilder.cost(fetchNumericValue(resultSet, columnName, skipRoundOff));
            break;
          }
        } else if (columnType.toUpperCase(Locale.ROOT).contains(DATETIME)
            || columnType.toUpperCase(Locale.ROOT).contains(DATE)) {
          if (columnName.equalsIgnoreCase(entityConstantMinStartTime)) {
            viewCostDataBuilder.minStartTime(fetchTimestampValue(resultSet, entityConstantMinStartTime));
          } else if (columnName.equalsIgnoreCase(entityConstantMaxStartTime)) {
            viewCostDataBuilder.maxStartTime(fetchTimestampValue(resultSet, entityConstantMaxStartTime));
          }
        }
        columnIndex++;
      }
      costTrendData.put(id, viewCostDataBuilder.build());
    }
    if (costTrendData.size() > MAX_LIMIT_VALUE) {
      log.warn("Cost trend result set size: {}", costTrendData.size());
    }
    return costTrendData;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for column list
  // ----------------------------------------------------------------------------------------------------------------
  public List<String> convertToColumnList(ResultSet resultSet) throws SQLException {
    List<String> columns = new ArrayList<>();
    while (resultSet != null && resultSet.next()) {
      columns.add(fetchStringValue(resultSet, "column_name"));
    }
    return columns;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for Total cost summary card
  // ----------------------------------------------------------------------------------------------------------------

  public ViewCostData convertToTrendStatsData(ResultSet resultSet, List<String> fields,
      boolean addSharedCostFromGroupBy, BusinessMapping businessMappingFromGroupBy,
      double sharedCostFromFiltersAndRules) throws SQLException {
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
    while (resultSet != null && resultSet.next()) {
      double cost = 0.0;
      double sharedCostInUnattributed = 0.0;
      String entityName = null;
      for (String field : fields) {
        switch (field) {
          case entityConstantMinStartTime:
            viewCostDataBuilder.minStartTime(
                resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime());
            break;
          case entityConstantMaxStartTime:
            viewCostDataBuilder.maxStartTime(
                resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime());
            break;
          case entityConstantCost:
          case entityConstantClusterCost:
            try {
              cost = Math.round(resultSet.getFloat(field) * 100D) / 100D;
            } catch (Exception e) {
              cost = Math.round(resultSet.getFloat(entityConstantClusterCost) * 100D) / 100D;
            }
            viewCostDataBuilder.cost(totalCost);
            break;
          case entityConstantIdleCost:
            idleCost = Math.round(resultSet.getFloat(field) * 100D) / 100D;
            viewCostDataBuilder.idleCost(idleCost);
            break;
          case entityConstantUnallocatedCost:
            unallocatedCost = Math.round(resultSet.getFloat(field) * 100D) / 100D;
            viewCostDataBuilder.unallocatedCost(unallocatedCost);
            break;
          case entityConstantSystemCost:
            viewCostDataBuilder.systemCost(Math.round(resultSet.getFloat(field) * 100D) / 100D);
            break;
          default:
            if (sharedCostBucketNames.contains(field)) {
              sharedCost += Math.round(resultSet.getFloat(field) * 100D) / 100D;
              sharedCostInUnattributed += Math.round(resultSet.getFloat(field) * 100D) / 100D;
            } else {
              try {
                entityName = fetchStringValue(resultSet, field, fieldName);
              } catch (Exception ignored) {
              }
            }
            break;
        }
      }
      if (entityName == null
          || (includeOthersCost || !entityName.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName()))) {
        totalCost += cost;
      }
      if (businessMappingFromGroupBy != null && businessMappingFromGroupBy.getUnallocatedCost() != null
          && entityName != null && entityName.equals(businessMappingFromGroupBy.getUnallocatedCost().getLabel())) {
        totalCost -= sharedCostInUnattributed;
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

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for Timeseries data
  // ----------------------------------------------------------------------------------------------------------------
  public PerspectiveTimeSeriesData convertToTimeSeriesData(ResultSet resultSet, long timePeriod, String conversionField,
      String businessMappingId, String accountId, List<QLCEViewGroupBy> groupBy,
      Map<String, Map<Timestamp, Double>> sharedCostFromFilters, boolean addSharedCostFromGroupBy) throws SQLException {
    BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
    UnallocatedCostStrategy strategy = businessMapping != null && businessMapping.getUnallocatedCost() != null
        ? businessMapping.getUnallocatedCost().getStrategy()
        : null;

    List<String> sharedCostBucketNames = new ArrayList<>();
    Map<String, Map<Timestamp, Double>> sharedCostFromGroupBy = new HashMap<>();
    List<String> costTargetNames = new ArrayList<>();
    if (businessMapping != null) {
      if (businessMapping.getSharedCosts() != null) {
        List<SharedCost> sharedCostBuckets = businessMapping.getSharedCosts();
        sharedCostBucketNames =
            sharedCostBuckets.stream()
                .map(sharedCostBucket -> viewsQueryBuilder.modifyStringToComplyRegex(sharedCostBucket.getName()))
                .collect(Collectors.toList());
      }
      if (businessMapping.getCostTargets() != null) {
        costTargetNames =
            businessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());
      }
    }

    String fieldName = PerspectiveTimeSeriesResponseHelper.getEntityGroupByFieldName(groupBy);
    Set<String> entityNames = new HashSet<>();

    Map<Timestamp, List<DataPoint>> costDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> cpuLimitDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> cpuRequestDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> cpuUtilizationValueDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> memoryLimitDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> memoryRequestDataPointsMap = new LinkedHashMap<>();
    Map<Timestamp, List<DataPoint>> memoryUtilizationValueDataPointsMap = new LinkedHashMap<>();

    double totalCost = 0.0;
    double numberOfEntities = 0.0;
    Map<String, Double> costPerEntity = new HashMap<>();
    Map<String, Reference> entityReference = new HashMap<>();
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      Timestamp startTimeTruncatedTimestamp = null;
      double value = 0d;
      double cpuLimit = DEFAULT_DOUBLE_VALUE;
      double cpuRequest = DEFAULT_DOUBLE_VALUE;
      double cpuUtilizationValue = DEFAULT_DOUBLE_VALUE;
      double maxCpuUtilizationValue = DEFAULT_DOUBLE_VALUE;
      double memoryLimit = DEFAULT_DOUBLE_VALUE;
      double memoryRequest = DEFAULT_DOUBLE_VALUE;
      double memoryUtilizationValue = DEFAULT_DOUBLE_VALUE;
      double maxMemoryUtilizationValue = DEFAULT_DOUBLE_VALUE;

      String id = DEFAULT_STRING_VALUE;
      String stringValue = DEFAULT_GRID_ENTRY_NAME;
      String type = DEFAULT_STRING_VALUE;
      double sharedCostInUnattributed = 0.0D;
      int columnIndex = 1;
      while (columnIndex <= totalColumns) {
        String field = resultSet.getMetaData().getColumnName(columnIndex);
        String fieldType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        if (fieldType.toUpperCase(Locale.ROOT).contains(DATETIME)
            || fieldType.toUpperCase(Locale.ROOT).contains(DATE)) {
          startTimeTruncatedTimestamp = Timestamp.ofTimeMicroseconds(
              resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime() * 1000);
        } else if (fieldType.toUpperCase(Locale.ROOT).contains(STRING)) {
          stringValue = fetchStringValue(resultSet, field, fieldName);
          entityNames.add(stringValue);
          type = field.toUpperCase(Locale.ROOT);
          id = perspectiveTimeSeriesResponseHelper.getUpdatedId(id, stringValue);
        } else if (fieldType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
          switch (field) {
            case COST:
            case BILLING_AMOUNT:
              value += fetchNumericValue(resultSet, field);
              break;
            case TIME_AGGREGATED_CPU_LIMIT:
              cpuLimit = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case TIME_AGGREGATED_CPU_REQUEST:
              cpuRequest = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case TIME_AGGREGATED_CPU_UTILIZATION_VALUE:
              cpuUtilizationValue = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case TIME_AGGREGATED_MEMORY_LIMIT:
              memoryLimit = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case TIME_AGGREGATED_MEMORY_REQUEST:
              memoryRequest = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE:
              memoryUtilizationValue = fetchNumericValue(resultSet, field) / (timePeriod * 1024);
              break;
            case CPU_LIMIT:
              cpuLimit = fetchNumericValue(resultSet, field) / 1024;
              break;
            case CPU_REQUEST:
              cpuRequest = fetchNumericValue(resultSet, field) / 1024;
              break;
            case AVG_CPU_UTILIZATION_VALUE:
              cpuUtilizationValue = fetchNumericValue(resultSet, field) / 1024;
              break;
            case MAX_CPU_UTILIZATION_VALUE:
              maxCpuUtilizationValue = fetchNumericValue(resultSet, field) / 1024;
              break;
            case MEMORY_LIMIT:
              memoryLimit = fetchNumericValue(resultSet, field) / 1024;
              break;
            case MEMORY_REQUEST:
              memoryRequest = fetchNumericValue(resultSet, field) / 1024;
              break;
            case AVG_MEMORY_UTILIZATION_VALUE:
              memoryUtilizationValue = fetchNumericValue(resultSet, field) / 1024;
              break;
            case MAX_MEMORY_UTILIZATION_VALUE:
              maxMemoryUtilizationValue = fetchNumericValue(resultSet, field) / 1024;
              break;
            default:
              if (sharedCostBucketNames.contains(field)) {
                perspectiveTimeSeriesResponseHelper.updateSharedCostMap(
                    sharedCostFromGroupBy, fetchNumericValue(resultSet, field), field, startTimeTruncatedTimestamp);
                sharedCostInUnattributed = fetchNumericValue(resultSet, field);
              }
              break;
          }
        }
        columnIndex++;
      }

      boolean addDataPoint = true;
      if (strategy != null
          && (stringValue.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())
              || stringValue.equals(businessMapping.getUnallocatedCost().getLabel()))) {
        switch (strategy) {
          case HIDE:
            addDataPoint = false;
            break;
          case DISPLAY_NAME:
            stringValue = businessMapping.getUnallocatedCost().getLabel();
            break;
          case SHARE:
          default:
            throw new InvalidRequestException(
                "Invalid Unallocated Cost Strategy / Unallocated Cost Strategy not supported");
        }
      }

      if (addDataPoint) {
        if (costTargetNames.contains(stringValue)) {
          if (!costPerEntity.containsKey(stringValue)) {
            costPerEntity.put(stringValue, 0.0);
            numberOfEntities += 1;
          }
          costPerEntity.put(stringValue, costPerEntity.get(stringValue) + value);
          entityReference.put(stringValue, PerspectiveTimeSeriesResponseHelper.getReference(id, stringValue, type));
          totalCost += value;
        }
        if (businessMapping != null && businessMapping.getUnallocatedCost() != null
            && businessMapping.getUnallocatedCost().getLabel().equals(stringValue)) {
          value -= sharedCostInUnattributed;
          value = Math.max(value, 0.0D);
        }
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, stringValue, type, value, costDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "LIMIT", "UTILIZATION", cpuLimit, cpuLimitDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "REQUEST", "UTILIZATION", cpuRequest, cpuRequestDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "AVG", "UTILIZATION", cpuUtilizationValue,
            cpuUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "MAX", "UTILIZATION", maxCpuUtilizationValue,
            cpuUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "LIMIT", "UTILIZATION", memoryLimit, memoryLimitDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "REQUEST", "UTILIZATION", memoryRequest, memoryRequestDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "AVG", "UTILIZATION", memoryUtilizationValue,
            memoryUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        perspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "MAX", "UTILIZATION", maxMemoryUtilizationValue,
            memoryUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
      }
    }

    if (!sharedCostBucketNames.isEmpty() && addSharedCostFromGroupBy) {
      costDataPointsMap = perspectiveTimeSeriesResponseHelper.addSharedCosts(costDataPointsMap,
          SharedCostParameters.builder()
              .totalCost(totalCost)
              .numberOfEntities(numberOfEntities)
              .costPerEntity(costPerEntity)
              .entityReference(entityReference)
              .sharedCostFromGroupBy(sharedCostFromGroupBy)
              .businessMappingFromGroupBy(businessMapping)
              .sharedCostFromFilters(sharedCostFromFilters)
              .build());
    }

    if (sharedCostFromFilters != null) {
      costDataPointsMap = perspectiveTimeSeriesResponseHelper.addSharedCostsFromFiltersAndRules(
          costDataPointsMap, sharedCostFromFilters);
    }

    if (conversionField != null) {
      costDataPointsMap = perspectiveTimeSeriesResponseHelper.getUpdatedDataPointsMap(
          costDataPointsMap, new ArrayList<>(entityNames), accountId, conversionField);
    }

    return PerspectiveTimeSeriesData.builder()
        .stats(PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(costDataPointsMap))
        .cpuLimit(PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(cpuLimitDataPointsMap))
        .cpuRequest(PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(cpuRequestDataPointsMap))
        .cpuUtilValues(
            PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(cpuUtilizationValueDataPointsMap))
        .memoryLimit(PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(memoryLimitDataPointsMap))
        .memoryRequest(PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(memoryRequestDataPointsMap))
        .memoryUtilValues(
            PerspectiveTimeSeriesResponseHelper.convertTimeSeriesPointsMapToList(memoryUtilizationValueDataPointsMap))
        .build();
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to build response for shared cost from filters data
  // ----------------------------------------------------------------------------------------------------------------
  public Map<String, Double> convertToSharedCostFromFiltersData(ResultSet resultSet,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, BusinessMapping sharedCostBusinessMapping,
      String groupByBusinessMappingId, Map<String, Double> sharedCostsFromFilters, boolean skipRoundOff,
      List<ViewRule> viewRules) throws SQLException {
    boolean groupByCurrentBusinessMapping =
        groupByBusinessMappingId != null && groupByBusinessMappingId.equals(sharedCostBusinessMapping.getUuid());
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

    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    double totalCost = 0.0;
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      String name = DEFAULT_GRID_ENTRY_NAME;
      Double cost = null;
      int columnIndex = 1;
      while (columnIndex <= totalColumns) {
        String columnName = resultSet.getMetaData().getColumnName(columnIndex);
        String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
          name = fetchStringValue(resultSet, columnName, fieldName);
        } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
          if (columnName.equalsIgnoreCase(COST) || columnName.equals(BILLING_AMOUNT)) {
            cost = fetchNumericValue(resultSet, columnName, skipRoundOff);
          } else if (sharedCostBucketNames.contains(columnName)) {
            sharedCosts.put(
                columnName, sharedCosts.get(columnName) + fetchNumericValue(resultSet, columnName, skipRoundOff));
          }
        }
        columnIndex++;
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
    return sharedCostsFromFilters;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Method to get others total cost data
  // ----------------------------------------------------------------------------------------------------------------
  public Map<Long, Double> convertToCostData(final ResultSet resultSet) throws SQLException {
    Map<Long, Double> costMapping = new HashMap<>();
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      long timestamp = 0L;
      double cost = 0.0D;
      int columnIndex = 1;
      while (columnIndex <= totalColumns) {
        String columnName = resultSet.getMetaData().getColumnName(columnIndex);
        String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        if (columnType.toUpperCase(Locale.ROOT).contains(DATETIME)
            || columnType.toUpperCase(Locale.ROOT).contains(DATE)) {
          timestamp = fetchTimestampValue(resultSet, columnName);
        } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)) {
          cost = fetchNumericValue(resultSet, columnName);
        }
        columnIndex++;
      }
      if (cost != 0L) {
        costMapping.put(timestamp, cost);
      }
    }
    return costMapping;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Miscellaneous helper methods
  // ----------------------------------------------------------------------------------------------------------------
  public String fetchStringValue(ResultSet resultSet, QLCEViewFieldInput field) throws SQLException {
    return resultSet.getString(viewsQueryBuilder.getAliasFromField(field));
  }

  public String fetchStringValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getString(field);
  }

  public String fetchStringValue(ResultSet resultSet, String field, String fieldName) throws SQLException {
    String value = resultSet.getString(field);
    if (isEmpty(value)) {
      return value;
    }
    return fieldName;
  }

  public double fetchNumericValue(ResultSet resultSet, String field) throws SQLException {
    return fetchNumericValue(resultSet, field, false);
  }

  public double fetchNumericValue(ResultSet resultSet, String field, boolean skipRoundOff) throws SQLException {
    double value = resultSet.getFloat(field);
    if (!skipRoundOff) {
      value = Math.round(value * 100D) / 100D;
    }
    return value;
  }

  public long fetchTimestampValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime();
  }

  public int getTotalColumnsCount(ResultSet resultSet) {
    if (resultSet != null) {
      try {
        return resultSet.getMetaData().getColumnCount();
      } catch (SQLException e) {
        return 0;
      }
    }
    return 0;
  }

  public Map<String, String> getFieldToDataTypeMapping(ResultSet resultSet) {
    Map<String, String> fieldToDataTypeMapping = new HashMap<>();
    if (resultSet != null) {
      try {
        int totalColumnsCount = resultSet.getMetaData().getColumnCount();
        int currentColumnIndex = 1;
        while (currentColumnIndex <= totalColumnsCount) {
          fieldToDataTypeMapping.put(resultSet.getMetaData().getColumnName(currentColumnIndex),
              resultSet.getMetaData().getColumnTypeName(currentColumnIndex));
          currentColumnIndex++;
        }
      } catch (SQLException ignored) {
      }
    }
    return fieldToDataTypeMapping;
  }

  private List<String> getStringFieldNames(Map<String, String> fieldToDataTypeMapping, String cloudProviderTableName) {
    List<String> fieldNames = new ArrayList<>();
    fieldToDataTypeMapping.keySet().forEach(key -> {
      if (fieldToDataTypeMapping.get(key).toUpperCase(Locale.ROOT).contains(STRING)) {
        fieldNames.add(
            viewsQueryBuilder.getColumnNameForField(viewsQueryBuilder.getTableIdentifier(cloudProviderTableName), key));
      }
    });
    return fieldNames;
  }
}
