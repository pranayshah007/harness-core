/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.ccm.commons.constants.DataTypeConstants.DATETIME;
import static io.harness.ccm.commons.constants.DataTypeConstants.FLOAT64;
import static io.harness.ccm.commons.constants.DataTypeConstants.STRING;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_INSTANCE_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_SERVICE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_USAGE_TYPE_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.PRODUCT_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.REGION_FIELD_ID;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantCost;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMaxStartTime;
import static io.harness.ccm.views.graphql.ViewMetaDataConstants.entityConstantMinStartTime;
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
import static io.harness.ccm.views.utils.ClusterTableKeys.ID_SEPARATOR;
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
import java.util.Arrays;
import java.util.Calendar;
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
  public QLCEViewGridData convertToEntityStatsData(ResultSet resultSet, List<String> fields,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isClusterPerspective,
      boolean isUsedByTimeSeriesStats, boolean skipRoundOff, String conversionField, String accountId,
      List<QLCEViewGroupBy> groupBy, BusinessMapping businessMapping, boolean addSharedCostFromGroupBy)
      throws SQLException {
    if (isClusterPerspective) {
      return convertToEntityStatsDataForCluster(resultSet, fields, costTrendData, startTimeForTrend,
          isUsedByTimeSeriesStats, skipRoundOff, groupBy, accountId);
    }
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
    int totalColumns = getTotalColumnsCount(resultSet);
    while (resultSet != null && resultSet.next()) {
      QLCEViewEntityStatsDataPointBuilder dataPointBuilder = QLCEViewEntityStatsDataPoint.builder();
      int columnIndex = 1;
      Double cost = null;
      String name = DEFAULT_GRID_ENTRY_NAME;
      String id = DEFAULT_STRING_VALUE;

      while (columnIndex <= totalColumns) {
        String columnName = resultSet.getMetaData().getColumnName(columnIndex);
        String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        switch (columnType.toUpperCase(Locale.ROOT)) {
          case STRING:
            name = fetchStringValue(resultSet, columnName, fieldName);
            id = getUpdatedId(id, name);
            entityNames.add(name);
            break;
          case FLOAT64:
            if (columnName.equalsIgnoreCase(COST)) {
              cost = fetchNumericValue(resultSet, columnName, skipRoundOff);
              dataPointBuilder.cost(cost);
            } else if (sharedCostBucketNames.contains(columnName)) {
              if (sharedCostBucketNames.contains(columnName)) {
                sharedCosts.put(
                    columnName, sharedCosts.get(columnName) + fetchNumericValue(resultSet, columnName, skipRoundOff));
              }
            }
            break;
          default:
            break;
        }
        columnIndex++;
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

    if (!sharedCostBucketNames.isEmpty() && addSharedCostFromGroupBy) {
      entityStatsDataPoints =
          viewBusinessMappingResponseHelper.addSharedCosts(entityStatsDataPoints, sharedCosts, businessMapping);
    }

    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size: {}", entityStatsDataPoints.size());
    }

    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fields).build();
  }

  private QLCEViewGridData convertToEntityStatsDataForCluster(ResultSet resultSet, List<String> fields,
      Map<String, ViewCostData> costTrendData, long startTimeForTrend, boolean isUsedByTimeSeriesStats,
      boolean skipRoundOff, List<QLCEViewGroupBy> groupBy, String accountId) throws SQLException {
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    boolean isInstanceDetailsData = fields.contains(INSTANCE_ID);
    List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints = new ArrayList<>();
    Set<String> instanceTypes = new HashSet<>();
    Map<String, String> fieldToDataTypeMapping = getFieldToDataTypeMapping(resultSet);
    while (resultSet != null && resultSet.next()) {
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

      for (String field : fields) {
        java.lang.reflect.Field builderField = builderFields.get(field.toLowerCase(Locale.ROOT));
        try {
          if (builderField != null) {
            builderField.setAccessible(true);
            if (builderField.getType().equals(String.class)) {
              builderField.set(clusterDataBuilder, fetchStringValue(resultSet, field));
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

        if (fieldToDataTypeMapping.get(field).equalsIgnoreCase(STRING)) {
          name = fetchStringValue(resultSet, field);
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
          .fields(fields)
          .build();
    }
    if (entityStatsDataPoints.size() > MAX_LIMIT_VALUE) {
      log.warn("Grid result set size (for cluster): {}", entityStatsDataPoints.size());
    }
    return QLCEViewGridData.builder().data(entityStatsDataPoints).fields(fields).build();
  }

  public Map<String, ViewCostData> convertToEntityStatsCostTrendData(ResultSet resultSet, List<String> fields,
      boolean isClusterTableQuery, boolean skipRoundOff, List<QLCEViewGroupBy> groupBy) throws SQLException {
    Map<String, ViewCostData> costTrendData = new HashMap<>();
    String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    while (resultSet != null && resultSet.next()) {
      String name;
      String id = DEFAULT_STRING_VALUE;
      ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();
      for (String field : fields) {
        switch (field) {
          case AWS_SERVICE_FIELD_ID:
          case AWS_ACCOUNT_FIELD_ID:
          case AWS_INSTANCE_TYPE_FIELD_ID:
          case AWS_USAGE_TYPE_ID:
          case REGION_FIELD_ID:
          case PRODUCT_FIELD_ID:
            name = fetchStringValue(resultSet, field);
            id = getUpdatedId(id, name);
            break;
          case BILLING_AMOUNT:
          case COST:
            viewCostDataBuilder.cost(fetchNumericValue(resultSet, field));
            break;
          case entityConstantMinStartTime:
            viewCostDataBuilder.minStartTime(fetchTimestampValue(resultSet, entityConstantMinStartTime));
            break;
          case entityConstantMaxStartTime:
            viewCostDataBuilder.maxStartTime(fetchTimestampValue(resultSet, entityConstantMaxStartTime));
            break;
          default:
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
  public ViewCostData convertToTrendStatsData(ResultSet resultSet) throws SQLException {
    ViewCostDataBuilder viewCostDataBuilder = ViewCostData.builder();

    while (resultSet != null && resultSet.next()) {
      for (String field : ViewFieldUtils.getTrendStatsFieldFieldNames()) {
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
            viewCostDataBuilder.cost(Math.round(resultSet.getFloat(field) * 100D) / 100D);
            break;
          default:
            break;
        }
      }
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
      int columnIndex = 1;
      while (columnIndex <= totalColumns) {
        String field = resultSet.getMetaData().getColumnName(columnIndex);
        String fieldType = resultSet.getMetaData().getColumnTypeName(columnIndex);
        switch (fieldType.toUpperCase(Locale.ROOT)) {
          case DATETIME:
            startTimeTruncatedTimestamp = Timestamp.ofTimeMicroseconds(
                resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime());
            break;
          case STRING:
            stringValue = fetchStringValue(resultSet, field);
            entityNames.add(stringValue);
            type = field.toUpperCase(Locale.ROOT);
            id = getUpdatedId(id, stringValue);
            break;
          case FLOAT64:
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
                }
                break;
            }
            break;
          default:
            break;
        }
        columnIndex++;
      }

      boolean addDataPoint = true;
      if (strategy != null && stringValue.equals(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName())) {
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
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, stringValue, type, value, costDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "LIMIT", "UTILIZATION", cpuLimit, cpuLimitDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "REQUEST", "UTILIZATION", cpuRequest, cpuRequestDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "AVG", "UTILIZATION", cpuUtilizationValue,
            cpuUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "MAX", "UTILIZATION", maxCpuUtilizationValue,
            cpuUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "LIMIT", "UTILIZATION", memoryLimit, memoryLimitDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(
            id, "REQUEST", "UTILIZATION", memoryRequest, memoryRequestDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "AVG", "UTILIZATION", memoryUtilizationValue,
            memoryUtilizationValueDataPointsMap, startTimeTruncatedTimestamp);
        PerspectiveTimeSeriesResponseHelper.addDataPointToMap(id, "MAX", "UTILIZATION", maxMemoryUtilizationValue,
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
        switch (columnType.toUpperCase(Locale.ROOT)) {
          case STRING:
            name = fetchStringValue(resultSet, columnName);
            break;
          case FLOAT64:
            if (columnName.equalsIgnoreCase(COST)) {
              cost = fetchNumericValue(resultSet, columnName, skipRoundOff);
            } else if (sharedCostBucketNames.contains(columnName)) {
              if (sharedCostBucketNames.contains(columnName)) {
                sharedCosts.put(
                    columnName, sharedCosts.get(columnName) + fetchNumericValue(resultSet, columnName, skipRoundOff));
              }
            }
            break;
          default:
            break;
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

    for (String entity : entitySharedCostDetails.keySet()) {
      if (selectedCostTargets.contains(entity)) {
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
        switch (columnType.toUpperCase(Locale.ROOT)) {
          case DATETIME:
            timestamp = fetchTimestampValue(resultSet, columnName);
            break;
          case FLOAT64:
            cost = fetchNumericValue(resultSet, columnName);
            break;
          default:
            break;
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
  private String getUpdatedId(String id, String newField) {
    return id.equals(DEFAULT_STRING_VALUE) ? newField : id + ID_SEPARATOR + newField;
  }

  private String fetchStringValue(ResultSet resultSet, QLCEViewFieldInput field) throws SQLException {
    return resultSet.getString(viewsQueryBuilder.getAliasFromField(field));
  }

  private String fetchStringValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getString(field);
  }

  private String fetchStringValue(ResultSet resultSet, String field, String fieldName) throws SQLException {
    String value = resultSet.getString(field);
    if (value != null) {
      return value;
    }
    return fieldName;
  }

  private double fetchNumericValue(ResultSet resultSet, String field) throws SQLException {
    return fetchNumericValue(resultSet, field, false);
  }

  private double fetchNumericValue(ResultSet resultSet, String field, boolean skipRoundOff) throws SQLException {
    double value = resultSet.getFloat(field);
    if (!skipRoundOff) {
      value = Math.round(value * 100D) / 100D;
    }
    return value;
  }

  private long fetchTimestampValue(ResultSet resultSet, String field) throws SQLException {
    return resultSet.getTimestamp(field, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).getTime();
  }

  private int getTotalColumnsCount(ResultSet resultSet) {
    if (resultSet != null) {
      try {
        return resultSet.getMetaData().getColumnCount();
      } catch (SQLException e) {
        return 0;
      }
    }
    return 0;
  }

  private Map<String, String> getFieldToDataTypeMapping(ResultSet resultSet) {
    Map<String, String> fieldToDataTypeMapping = new HashMap<>();
    if (resultSet != null) {
      try {
        int totalColumnsCount = resultSet.getMetaData().getColumnCount();
        int currentColumnIndex = 0;
        while (currentColumnIndex < totalColumnsCount) {
          fieldToDataTypeMapping.put(resultSet.getMetaData().getColumnName(currentColumnIndex),
              resultSet.getMetaData().getColumnTypeName(currentColumnIndex));
          currentColumnIndex++;
        }
      } catch (SQLException ignored) {
      }
    }
    return fieldToDataTypeMapping;
  }
}
