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
import io.harness.ccm.graphql.dto.common.DataPoint;
import io.harness.ccm.graphql.dto.common.DataPoint.DataPointBuilder;
import io.harness.ccm.graphql.dto.common.Reference;
import io.harness.ccm.graphql.dto.common.TimeSeriesDataPoints;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTimeSeriesData;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerspectiveTimeSeriesHelper {
  @Inject EntityMetadataService entityMetadataService;
  @Inject BusinessMappingService businessMappingService;
  private static final String nullStringValueConstant = "Others";
  private static final String OTHERS = "Others";
  private static final String UNALLOCATED_COST = "Unallocated";
  private static final long ONE_DAY_SEC = 86400;
  private static final long ONE_HOUR_SEC = 3600;

  public PerspectiveTimeSeriesData fetch(TableResult result, long timePeriod) {
    return fetch(result, timePeriod, null, null, null);
  }

  public PerspectiveTimeSeriesData fetch(
      TableResult result, long timePeriod, String conversionField, String businessMappingId, String accountId) {
    BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
    UnallocatedCostStrategy strategy =
        businessMapping != null ? businessMapping.getUnallocatedCost().getStrategy() : null;

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
            stringValue = fetchStringValue(row, field);
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
                break;
            }
            break;
          default:
            break;
        }
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
      DataPointBuilder dataPointBuilder = DataPoint.builder();
      dataPointBuilder.key(getReference(id, name, type));
      dataPointBuilder.value(getRoundedDoubleValue(value));
      List<DataPoint> dataPoints = dataPointsMap.getOrDefault(startTimeTruncatedTimestamp, new ArrayList<>());
      dataPoints.add(dataPointBuilder.build());
      dataPointsMap.put(startTimeTruncatedTimestamp, dataPoints);
    }
  }

  private List<TimeSeriesDataPoints> convertTimeSeriesPointsMapToList(
      Map<Timestamp, List<DataPoint>> timeSeriesDataPointsMap) {
    return timeSeriesDataPointsMap.entrySet()
        .stream()
        .map(e
            -> TimeSeriesDataPoints.builder().time(e.getKey().toSqlTimestamp().getTime()).values(e.getValue()).build())
        .collect(Collectors.toList());
  }

  private static String fetchStringValue(FieldValueList row, Field field) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return nullStringValueConstant;
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

  public PerspectiveTimeSeriesData postFetch(PerspectiveTimeSeriesData data, Integer limit, boolean includeOthers,
      @Nullable Map<Long, Double> unallocatedCostMapping) {
    Map<String, Double> aggregatedDataPerUniqueId = new HashMap<>();
    data.getStats().forEach(dataPoint -> {
      for (DataPoint entry : dataPoint.getValues()) {
        Reference qlReference = entry.getKey();
        if (qlReference != null && qlReference.getId() != null) {
          String key = qlReference.getId();
          if (aggregatedDataPerUniqueId.containsKey(key)) {
            aggregatedDataPerUniqueId.put(key, entry.getValue().doubleValue() + aggregatedDataPerUniqueId.get(key));
          } else {
            aggregatedDataPerUniqueId.put(key, entry.getValue().doubleValue());
          }
        }
      }
    });
    if (aggregatedDataPerUniqueId.isEmpty()) {
      return data;
    }
    List<String> selectedIdsAfterLimit = getElementIdsAfterLimit(aggregatedDataPerUniqueId, limit);

    return PerspectiveTimeSeriesData.builder()
        .stats(getDataAfterLimit(data, selectedIdsAfterLimit, includeOthers, unallocatedCostMapping))
        .cpuLimit(data.getCpuLimit())
        .cpuRequest(data.getCpuRequest())
        .cpuUtilValues(data.getCpuUtilValues())
        .memoryLimit(data.getMemoryLimit())
        .memoryRequest(data.getMemoryRequest())
        .memoryUtilValues(data.getMemoryUtilValues())
        .build();
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

  private List<TimeSeriesDataPoints> getDataAfterLimit(PerspectiveTimeSeriesData data,
      List<String> selectedIdsAfterLimit, boolean includeOthers, @Nullable Map<Long, Double> unallocatedCostMapping) {
    List<TimeSeriesDataPoints> limitProcessedData = new ArrayList<>();
    data.getStats().forEach(dataPoint -> {
      List<DataPoint> limitProcessedValues = new ArrayList<>();
      DataPoint others = DataPoint.builder().key(Reference.builder().id(OTHERS).name(OTHERS).build()).value(0).build();
      for (DataPoint entry : dataPoint.getValues()) {
        String key = entry.getKey().getId();
        if (selectedIdsAfterLimit.contains(key)) {
          limitProcessedValues.add(entry);
        } else {
          others.setValue(others.getValue().doubleValue() + entry.getValue().doubleValue());
        }
      }

      if (Objects.nonNull(unallocatedCostMapping) && unallocatedCostMapping.containsKey(dataPoint.getTime())) {
        limitProcessedValues.add(DataPoint.builder()
                                     .key(Reference.builder().id(UNALLOCATED_COST).name(UNALLOCATED_COST).build())
                                     .value(unallocatedCostMapping.get(dataPoint.getTime()))
                                     .build());
      }

      if (others.getValue().doubleValue() > 0 && includeOthers) {
        others.setValue(getRoundedDoubleValue(others.getValue().doubleValue()));
        limitProcessedValues.add(others);
      }

      limitProcessedData.add(
          TimeSeriesDataPoints.builder().time(dataPoint.getTime()).values(limitProcessedValues).build());
    });
    return limitProcessedData;
  }

  private List<String> getElementIdsAfterLimit(Map<String, Double> aggregatedData, Integer limit) {
    List<Map.Entry<String, Double>> list = new ArrayList<>(aggregatedData.entrySet());
    list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
    list = list.stream().limit(limit).collect(Collectors.toList());
    List<String> topNElementIds = new ArrayList<>();
    list.forEach(entry -> topNElementIds.add(entry.getKey()));
    return topNElementIds;
  }

  private String getUpdatedId(String id, String newField) {
    return id.equals(DEFAULT_STRING_VALUE) ? newField : id + ID_SEPARATOR + newField;
  }

  private Reference getReference(String id, String name, String type) {
    return Reference.builder().id(id).name(name).type(type).build();
  }

  public long getTimePeriod(List<QLCEViewGroupBy> groupBy) {
    try {
      List<QLCEViewTimeTruncGroupBy> timeGroupBy = groupBy.stream()
                                                       .filter(entry -> entry.getTimeTruncGroupBy() != null)
                                                       .map(QLCEViewGroupBy::getTimeTruncGroupBy)
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
}
