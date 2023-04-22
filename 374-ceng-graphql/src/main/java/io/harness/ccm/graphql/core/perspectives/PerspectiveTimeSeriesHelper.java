/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.perspectives;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
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
import static io.harness.ccm.views.utils.ClusterTableKeys.MAX_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MAX_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE;

import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.ccm.views.businessmapping.entities.SharedCostParameters;
import io.harness.ccm.views.businessmapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessmapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.DataPoint;
import io.harness.ccm.views.dto.DataPoint.DataPointBuilder;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.dto.Reference;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import io.fabric8.utils.Maps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerspectiveTimeSeriesHelper {
  @Inject EntityMetadataService entityMetadataService;
  @Inject BusinessMappingService businessMappingService;

  private static final String OTHERS = "Others";
  private static final String UNALLOCATED_COST = "Unallocated";
  private static final long ONE_DAY_SEC = 86400;
  private static final long ONE_HOUR_SEC = 3600;

  public PerspectiveTimeSeriesData fetch(TableResult result, long timePeriod, List<QLCEViewGroupBy> groupBy) {
    return fetch(result, timePeriod, null, null, null, groupBy, null, true);
  }

  public PerspectiveTimeSeriesData fetch(TableResult result, long timePeriod, String conversionField,
      String businessMappingId, String accountId, List<QLCEViewGroupBy> groupBy,
      Map<String, Map<Timestamp, Double>> sharedCostFromFilters, boolean addSharedCostFromGroupBy) {
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
        sharedCostBucketNames = sharedCostBuckets.stream()
                                    .map(sharedCostBucket -> modifyStringToComplyRegex(sharedCostBucket.getName()))
                                    .collect(Collectors.toList());
      }
      if (businessMapping.getCostTargets() != null) {
        costTargetNames =
            businessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());
      }
    }

    String fieldName = getEntityGroupByFieldName(groupBy);

    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
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

    for (FieldValueList row : result.iterateAll()) {
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
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case TIMESTAMP:
            startTimeTruncatedTimestamp = Timestamp.ofTimeMicroseconds(row.get(field.getName()).getTimestampValue());
            break;
          case STRING:
            stringValue = fetchStringValue(row, field, fieldName);
            entityNames.add(stringValue);
            type = field.getName();
            id = getUpdatedId(id, stringValue);
            break;
          case FLOAT64:
            switch (field.getName()) {
              case COST:
              case BILLING_AMOUNT:
                value += getNumericValue(row, field);
                break;
              case TIME_AGGREGATED_CPU_LIMIT:
                cpuLimit = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case TIME_AGGREGATED_CPU_REQUEST:
                cpuRequest = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case TIME_AGGREGATED_CPU_UTILIZATION_VALUE:
                cpuUtilizationValue = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case TIME_AGGREGATED_MEMORY_LIMIT:
                memoryLimit = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case TIME_AGGREGATED_MEMORY_REQUEST:
                memoryRequest = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE:
                memoryUtilizationValue = getNumericValue(row, field) / (timePeriod * 1024);
                break;
              case CPU_LIMIT:
                cpuLimit = getNumericValue(row, field) / 1024;
                break;
              case CPU_REQUEST:
                cpuRequest = getNumericValue(row, field) / 1024;
                break;
              case AVG_CPU_UTILIZATION_VALUE:
                cpuUtilizationValue = getNumericValue(row, field) / 1024;
                break;
              case MAX_CPU_UTILIZATION_VALUE:
                maxCpuUtilizationValue = getNumericValue(row, field) / 1024;
                break;
              case MEMORY_LIMIT:
                memoryLimit = getNumericValue(row, field) / 1024;
                break;
              case MEMORY_REQUEST:
                memoryRequest = getNumericValue(row, field) / 1024;
                break;
              case AVG_MEMORY_UTILIZATION_VALUE:
                memoryUtilizationValue = getNumericValue(row, field) / 1024;
                break;
              case MAX_MEMORY_UTILIZATION_VALUE:
                maxMemoryUtilizationValue = getNumericValue(row, field) / 1024;
                break;
              default:
                if (sharedCostBucketNames.contains(field.getName())) {
                  updateSharedCostMap(
                      sharedCostFromGroupBy, getNumericValue(row, field), field.getName(), startTimeTruncatedTimestamp);
                }
                break;
            }
            break;
          default:
            break;
        }
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
          entityReference.put(stringValue, getReference(id, stringValue, type));
          totalCost += value;
        }
        addDataPointToMap(id, stringValue, type, value, costDataPointsMap, startTimeTruncatedTimestamp);
        addDataPointToMap(id, "LIMIT", "UTILIZATION", cpuLimit, cpuLimitDataPointsMap, startTimeTruncatedTimestamp);
        addDataPointToMap(
            id, "REQUEST", "UTILIZATION", cpuRequest, cpuRequestDataPointsMap, startTimeTruncatedTimestamp);
        addDataPointToMap(id, "AVG", "UTILIZATION", cpuUtilizationValue, cpuUtilizationValueDataPointsMap,
            startTimeTruncatedTimestamp);
        addDataPointToMap(id, "MAX", "UTILIZATION", maxCpuUtilizationValue, cpuUtilizationValueDataPointsMap,
            startTimeTruncatedTimestamp);
        addDataPointToMap(
            id, "LIMIT", "UTILIZATION", memoryLimit, memoryLimitDataPointsMap, startTimeTruncatedTimestamp);
        addDataPointToMap(
            id, "REQUEST", "UTILIZATION", memoryRequest, memoryRequestDataPointsMap, startTimeTruncatedTimestamp);
        addDataPointToMap(id, "AVG", "UTILIZATION", memoryUtilizationValue, memoryUtilizationValueDataPointsMap,
            startTimeTruncatedTimestamp);
        addDataPointToMap(id, "MAX", "UTILIZATION", maxMemoryUtilizationValue, memoryUtilizationValueDataPointsMap,
            startTimeTruncatedTimestamp);
      }
    }

    if (!sharedCostBucketNames.isEmpty() && addSharedCostFromGroupBy) {
      costDataPointsMap = addSharedCosts(costDataPointsMap,
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
      costDataPointsMap = addSharedCostsFromFiltersAndRules(costDataPointsMap, sharedCostFromFilters);
    }

    if (conversionField != null) {
      costDataPointsMap =
          getUpdatedDataPointsMap(costDataPointsMap, new ArrayList<>(entityNames), accountId, conversionField);
    }

    return PerspectiveTimeSeriesData.builder()
        .stats(convertTimeSeriesPointsMapToList(costDataPointsMap))
        .cpuLimit(convertTimeSeriesPointsMapToList(cpuLimitDataPointsMap))
        .cpuRequest(convertTimeSeriesPointsMapToList(cpuRequestDataPointsMap))
        .cpuUtilValues(convertTimeSeriesPointsMapToList(cpuUtilizationValueDataPointsMap))
        .memoryLimit(convertTimeSeriesPointsMapToList(memoryLimitDataPointsMap))
        .memoryRequest(convertTimeSeriesPointsMapToList(memoryRequestDataPointsMap))
        .memoryUtilValues(convertTimeSeriesPointsMapToList(memoryUtilizationValueDataPointsMap))
        .build();
  }

  private void addDataPointToMap(String id, String name, String type, double value,
      Map<Timestamp, List<DataPoint>> dataPointsMap, Timestamp startTimeTruncatedTimestamp) {
    if (value != DEFAULT_DOUBLE_VALUE) {
      List<DataPoint> dataPoints = dataPointsMap.getOrDefault(startTimeTruncatedTimestamp, new ArrayList<>());
      dataPoints.add(getDataPoint(id, name, type, value));
      dataPointsMap.put(startTimeTruncatedTimestamp, dataPoints);
    }
  }

  private DataPoint getDataPoint(String id, String name, String type, double value) {
    DataPointBuilder dataPointBuilder = DataPoint.builder();
    if (value != DEFAULT_DOUBLE_VALUE) {
      dataPointBuilder.key(getReference(id, name, type));
      dataPointBuilder.value(getRoundedDoubleValue(value));
    }
    return dataPointBuilder.build();
  }

  private List<TimeSeriesDataPoints> convertTimeSeriesPointsMapToList(
      Map<Timestamp, List<DataPoint>> timeSeriesDataPointsMap) {
    return timeSeriesDataPointsMap.entrySet()
        .stream()
        .map(e
            -> TimeSeriesDataPoints.builder().time(e.getKey().toSqlTimestamp().getTime()).values(e.getValue()).build())
        .collect(Collectors.toList());
  }

  private static String fetchStringValue(FieldValueList row, Field field, String fieldName) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return fieldName;
  }

  private double getNumericValue(FieldValueList row, Field field) {
    FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return Math.round(value.getNumericValue().doubleValue() * 100D) / 100D;
    }
    return 0;
  }

  private double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  public PerspectiveTimeSeriesData postFetch(PerspectiveTimeSeriesData data, boolean includeOthers,
      Map<Long, Double> othersTotalCostMapping, Map<Long, Double> unallocatedCostMapping) {
    boolean isEmptyData = true;
    String type = DEFAULT_STRING_VALUE;
    for (final TimeSeriesDataPoints dataPoint : data.getStats()) {
      for (final DataPoint entry : dataPoint.getValues()) {
        final Reference qlReference = entry.getKey();
        if (Objects.nonNull(qlReference) && Objects.nonNull(qlReference.getId())) {
          type = qlReference.getType();
          isEmptyData = false;
          break;
        }
      }
    }
    if (isEmptyData) {
      return data;
    }

    return PerspectiveTimeSeriesData.builder()
        .stats(getDataAfterLimit(data, includeOthers, othersTotalCostMapping, unallocatedCostMapping, type))
        .cpuLimit(data.getCpuLimit())
        .cpuRequest(data.getCpuRequest())
        .cpuUtilValues(data.getCpuUtilValues())
        .memoryLimit(data.getMemoryLimit())
        .memoryRequest(data.getMemoryRequest())
        .memoryUtilValues(data.getMemoryUtilValues())
        .build();
  }

  private Map<Timestamp, List<DataPoint>> addSharedCostsFromFiltersAndRules(
      Map<Timestamp, List<DataPoint>> costDataPointsMap,
      Map<String, Map<Timestamp, Double>> sharedCostsFromRulesAndFilters) {
    Map<Timestamp, List<DataPoint>> updatedDataPointsMap = new TreeMap<>();
    for (Map.Entry<Timestamp, List<DataPoint>> entry : costDataPointsMap.entrySet()) {
      updatedDataPointsMap.put(
          entry.getKey(), addSharedCostsToDataPoint(entry.getValue(), sharedCostsFromRulesAndFilters, entry.getKey()));
    }
    if (Maps.isNullOrEmpty(costDataPointsMap)) {
      updatedDataPointsMap = getDataPointsFromSharedCosts(sharedCostsFromRulesAndFilters);
    }

    return updatedDataPointsMap;
  }

  private Map<Timestamp, List<DataPoint>> getDataPointsFromSharedCosts(
      Map<String, Map<Timestamp, Double>> sharedCostsFromRulesAndFilters) {
    Map<Timestamp, List<DataPoint>> updatedDataPointsMap = new TreeMap<>();
    for (Map.Entry<String, Map<Timestamp, Double>> entry1 : sharedCostsFromRulesAndFilters.entrySet()) {
      String name = entry1.getKey();
      String id = getUpdatedId(DEFAULT_STRING_VALUE, name);
      for (Map.Entry<Timestamp, Double> entry2 : entry1.getValue().entrySet()) {
        List<DataPoint> dataPoints = updatedDataPointsMap.getOrDefault(entry2.getKey(), new ArrayList<>());
        dataPoints.add(getDataPoint(id, name, DEFAULT_STRING_VALUE, entry2.getValue()));
        updatedDataPointsMap.put(entry2.getKey(), dataPoints);
      }
    }
    return updatedDataPointsMap;
  }

  private Map<Timestamp, List<DataPoint>> addSharedCosts(
      Map<Timestamp, List<DataPoint>> costDataPointsMap, SharedCostParameters sharedCostParameters) {
    Map<Timestamp, List<DataPoint>> updatedDataPointsMap = new TreeMap<>();
    costDataPointsMap.keySet().forEach(timestamp
        -> updatedDataPointsMap.put(
            timestamp, addSharedCostsToDataPoint(costDataPointsMap.get(timestamp), sharedCostParameters, timestamp)));
    return updatedDataPointsMap;
  }

  private List<DataPoint> addSharedCostsToDataPoint(List<DataPoint> dataPoints,
      Map<String, Map<Timestamp, Double>> sharedCostsFromRulesAndFilters, Timestamp timestamp) {
    List<DataPoint> updatedDataPoints = new ArrayList<>();
    Map<String, Boolean> entityDataPointAdded = new HashMap<>();

    dataPoints.forEach(dataPoint -> {
      String name = dataPoint.getKey().getName();
      entityDataPointAdded.put(name, true);
      double updatedCost = dataPoint.getValue().doubleValue();
      if (sharedCostsFromRulesAndFilters.containsKey(name)) {
        updatedCost += sharedCostsFromRulesAndFilters.get(name).getOrDefault(timestamp, 0.0);
      }
      updatedDataPoints.add(DataPoint.builder()
                                .value(getRoundedDoubleValue(updatedCost))
                                .key(getReference(dataPoint.getKey().getId(), name, dataPoint.getKey().getType()))
                                .build());
    });

    sharedCostsFromRulesAndFilters.keySet().forEach(entity -> {
      if (!entityDataPointAdded.containsKey(entity)) {
        entityDataPointAdded.put(entity, true);
        double cost = sharedCostsFromRulesAndFilters.get(entity).getOrDefault(timestamp, 0.0);
        updatedDataPoints.add(
            DataPoint.builder().value(getRoundedDoubleValue(cost)).key(getReference(entity, entity, "")).build());
      }
    });

    return updatedDataPoints;
  }

  private List<DataPoint> addSharedCostsToDataPoint(
      List<DataPoint> dataPoints, SharedCostParameters sharedCostParameters, Timestamp timestamp) {
    Set<String> entities = sharedCostParameters.getCostPerEntity().keySet();
    Map<String, Boolean> entityDataPointAdded = new HashMap<>();
    List<DataPoint> updatedDataPoints = new ArrayList<>();
    List<SharedCost> sharedCostBucketsFromGroupBy = sharedCostParameters.getBusinessMappingFromGroupBy() != null
        ? sharedCostParameters.getBusinessMappingFromGroupBy().getSharedCosts()
        : Collections.emptyList();
    List<String> costTargets = sharedCostParameters.getBusinessMappingFromGroupBy().getCostTargets() != null
        ? sharedCostParameters.getBusinessMappingFromGroupBy()
              .getCostTargets()
              .stream()
              .map(CostTarget::getName)
              .collect(Collectors.toList())
        : Collections.emptyList();

    dataPoints.forEach(dataPoint -> {
      String name = dataPoint.getKey().getName();
      entityDataPointAdded.put(name, true);
      double sharedCostForEntity = 0.0;

      if (costTargets.contains(name)) {
        sharedCostForEntity = calculateSharedCost(sharedCostParameters, timestamp, name,
            sharedCostParameters.getCostPerEntity().get(name), sharedCostBucketsFromGroupBy,
            sharedCostParameters.getSharedCostFromGroupBy());
      }

      updatedDataPoints.add(DataPoint.builder()
                                .value(getRoundedDoubleValue(dataPoint.getValue().doubleValue() + sharedCostForEntity))
                                .key(getReference(dataPoint.getKey().getId(), name, dataPoint.getKey().getType()))
                                .build());
    });

    entities.forEach(entity -> {
      if (!entityDataPointAdded.containsKey(entity)) {
        entityDataPointAdded.put(entity, true);
        double sharedCostForEntity = 0.0;
        if (costTargets.contains(entity)) {
          sharedCostForEntity = calculateSharedCost(sharedCostParameters, timestamp, entity,
              sharedCostParameters.getCostPerEntity().get(entity), sharedCostBucketsFromGroupBy,
              sharedCostParameters.getSharedCostFromGroupBy());
        }
        updatedDataPoints.add(DataPoint.builder()
                                  .value(getRoundedDoubleValue(sharedCostForEntity))
                                  .key(sharedCostParameters.getEntityReference().get(entity))
                                  .build());
      }
    });

    return updatedDataPoints;
  }

  private double calculateSharedCost(SharedCostParameters sharedCostParameters, Timestamp timestamp, String entity,
      Double entityCost, List<SharedCost> sharedCostBuckets, Map<String, Map<Timestamp, Double>> sharedCosts) {
    double sharedCost = 0.0;
    for (SharedCost sharedCostBucket : sharedCostBuckets) {
      Map<Timestamp, Double> sharedCostsPerTimestamp =
          sharedCosts.get(modifyStringToComplyRegex(sharedCostBucket.getName()));
      if (Objects.nonNull(sharedCostsPerTimestamp)) {
        double sharedCostForGivenTimestamp = sharedCostsPerTimestamp.getOrDefault(timestamp, 0.0D);
        switch (sharedCostBucket.getStrategy()) {
          case PROPORTIONAL:
            sharedCost += sharedCostForGivenTimestamp * (entityCost / sharedCostParameters.getTotalCost());
            break;
          case EQUAL:
            sharedCost += sharedCostForGivenTimestamp * (1.0 / sharedCostParameters.getNumberOfEntities());
            break;
          case FIXED:
            for (final SharedCostSplit sharedCostSplit : sharedCostBucket.getSplits()) {
              if (entity.equals(sharedCostSplit.getCostTargetName())) {
                sharedCost += sharedCostForGivenTimestamp * (sharedCostSplit.getPercentageContribution() / 100.0D);
                break;
              }
            }
            break;
          default:
            log.error("Invalid shared cost strategy for shared cost bucket: {}", sharedCostBucket);
            break;
        }
      }
    }
    return sharedCost;
  }

  // If conversion  of entity id to name is required
  private Map<Timestamp, List<DataPoint>> getUpdatedDataPointsMap(Map<Timestamp, List<DataPoint>> costDataPointsMap,
      List<String> entityIds, String harnessAccountId, String fieldName) {
    Map<String, String> entityIdToName =
        entityMetadataService.getEntityIdToNameMapping(entityIds, harnessAccountId, fieldName);
    Map<Timestamp, List<DataPoint>> updatedDataPointsMap = new TreeMap<>();
    if (entityIdToName != null) {
      costDataPointsMap.keySet().forEach(timestamp
          -> updatedDataPointsMap.put(
              timestamp, getUpdatedDataPoints(costDataPointsMap.get(timestamp), entityIdToName, fieldName)));
      return updatedDataPointsMap;
    } else {
      return costDataPointsMap;
    }
  }

  private List<DataPoint> getUpdatedDataPoints(
      List<DataPoint> dataPoints, Map<String, String> entityIdToName, String fieldName) {
    List<DataPoint> updatedDataPoints = new ArrayList<>();

    dataPoints.forEach(dataPoint -> {
      String name = entityIdToName.getOrDefault(dataPoint.getKey().getName(), dataPoint.getKey().getName());
      if (AWS_ACCOUNT_FIELD.equals(fieldName)) {
        name = AwsAccountFieldHelper.mergeAwsAccountIdAndName(
            dataPoint.getKey().getName(), entityIdToName.get(dataPoint.getKey().getName()));
      }
      updatedDataPoints.add(DataPoint.builder()
                                .value(dataPoint.getValue())
                                .key(getReference(dataPoint.getKey().getId(), name, dataPoint.getKey().getType()))
                                .build());
    });
    return updatedDataPoints;
  }

  private List<TimeSeriesDataPoints> getDataAfterLimit(PerspectiveTimeSeriesData data, boolean includeOthers,
      Map<Long, Double> othersTotalCostMapping, Map<Long, Double> unallocatedCostMapping, String type) {
    List<TimeSeriesDataPoints> limitProcessedData = new ArrayList<>();
    data.getStats().forEach(dataPoint -> {
      List<DataPoint> limitProcessedValues = new ArrayList<>();
      double topLimitCost = 0D;
      for (DataPoint entry : dataPoint.getValues()) {
        limitProcessedValues.add(entry);
        topLimitCost += entry.getValue().doubleValue();
      }

      if (includeOthers) {
        Number value = 0D;
        if (othersTotalCostMapping.containsKey(dataPoint.getTime())) {
          value = Math.max(getRoundedDoubleValue(othersTotalCostMapping.get(dataPoint.getTime()) - topLimitCost),
              value.doubleValue());
        }
        limitProcessedValues.add(DataPoint.builder()
                                     .key(Reference.builder().id(OTHERS).name(OTHERS).type(type).build())
                                     .value(value)
                                     .build());
      }

      if (!unallocatedCostMapping.isEmpty()) {
        limitProcessedValues.add(
            DataPoint.builder()
                .key(Reference.builder().id(UNALLOCATED_COST).name(UNALLOCATED_COST).type(type).build())
                .value(getRoundedDoubleValue(unallocatedCostMapping.getOrDefault(dataPoint.getTime(), 0D)))
                .build());
      }

      limitProcessedData.add(
          TimeSeriesDataPoints.builder().time(dataPoint.getTime()).values(limitProcessedValues).build());
    });
    return limitProcessedData;
  }

  private String getUpdatedId(String id, String newField) {
    return id.equals(DEFAULT_STRING_VALUE) ? newField : id + ID_SEPARATOR + newField;
  }

  private Reference getReference(String id, String name, String type) {
    return Reference.builder().id(id).name(name).type(type).build();
  }

  private static String getEntityGroupByFieldName(final List<QLCEViewGroupBy> groupBy) {
    String entityGroupByFieldName = OTHERS;
    final Optional<String> groupByFieldName = groupBy.stream()
                                                  .filter(entry -> Objects.nonNull(entry.getEntityGroupBy()))
                                                  .map(entry -> entry.getEntityGroupBy().getFieldName())
                                                  .findFirst();
    if (groupByFieldName.isPresent()) {
      entityGroupByFieldName = "No " + groupByFieldName.get();
    }
    return entityGroupByFieldName;
  }

  public long getTimePeriod(List<QLCEViewGroupBy> groupBy) {
    try {
      List<QLCEViewTimeTruncGroupBy> timeGroupBy = groupBy.stream()
                                                       .map(QLCEViewGroupBy::getTimeTruncGroupBy)
                                                       .filter(Objects::nonNull)
                                                       .collect(Collectors.toList());
      switch (timeGroupBy.get(0).getResolution()) {
        case HOUR:
          return ONE_HOUR_SEC;
        case WEEK:
          return 7 * ONE_DAY_SEC;
        case MONTH:
          return 30 * ONE_DAY_SEC;
        case DAY:
        default:
          return ONE_DAY_SEC;
      }
    } catch (Exception e) {
      log.info("Time group by can't be null for timeSeries query");
      return ONE_DAY_SEC;
    }
  }

  private String modifyStringToComplyRegex(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  private void updateSharedCostMap(Map<String, Map<Timestamp, Double>> sharedCostFromGroupBy, Double sharedCostValue,
      String sharedCostName, Timestamp timeStamp) {
    if (!sharedCostFromGroupBy.containsKey(sharedCostName)) {
      sharedCostFromGroupBy.put(sharedCostName, new HashMap<>());
    }
    if (!sharedCostFromGroupBy.get(sharedCostName).containsKey(timeStamp)) {
      sharedCostFromGroupBy.get(sharedCostName).put(timeStamp, 0.0);
    }
    Double currentValue = sharedCostFromGroupBy.get(sharedCostName).get(timeStamp);
    sharedCostFromGroupBy.get(sharedCostName).put(timeStamp, currentValue + sharedCostValue);
  }
}
