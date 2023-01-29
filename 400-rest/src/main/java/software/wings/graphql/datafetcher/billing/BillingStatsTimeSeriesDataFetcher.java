/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.graphql.datafetcher.billing.BillingDataQueryBuilder.INVALID_FILTER_MSG;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.AbstractStatsDataFetcherWithAggregationListAndTags;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.schema.type.aggregation.QLBillingDataPoint;
import software.wings.graphql.schema.type.aggregation.QLBillingStackedTimeSeriesData;
import software.wings.graphql.schema.type.aggregation.QLBillingStackedTimeSeriesData.QLBillingStackedTimeSeriesDataBuilder;
import software.wings.graphql.schema.type.aggregation.QLBillingStackedTimeSeriesDataPoint;
import software.wings.graphql.schema.type.aggregation.QLBillingStackedTimeSeriesDataPoint.QLBillingStackedTimeSeriesDataPointBuilder;
import software.wings.graphql.schema.type.aggregation.QLBillingTimeDataPoint;
import software.wings.graphql.schema.type.aggregation.QLBillingTimeDataPoint.QLBillingTimeDataPointBuilder;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagType;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BillingStatsTimeSeriesDataFetcher
    extends AbstractStatsDataFetcherWithAggregationListAndTags<QLCCMAggregationFunction, QLBillingDataFilter,
        QLCCMGroupBy, QLBillingSortCriteria, QLBillingDataTagType, QLBillingDataTagAggregation,
        QLBillingDataLabelAggregation, QLCCMEntityGroupBy> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private QLBillingStatsHelper statsHelper;
  @Inject BillingDataQueryBuilder billingDataQueryBuilder;
  @Inject CeAccountExpirationChecker accountChecker;
  @Inject InstanceDataService instanceDataService;

  private static final long ONE_DAY_MILLIS = 86400000;
  private static final long ONE_HOUR_SEC = 3600;
  private static final long ONE_DAY_SEC = 86400;
  private static final String UNALLOCATED_COST_ENTRY = "Unallocated";
  private static final String EMPTY = "";

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLData fetch(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sortCriteria,
      Integer limit, Integer offset) {
    accountChecker.checkIsCeEnabled(accountId);
    boolean timeScaleDBServiceValid = timeScaleDBService.isValid();
    log.info("Timescale db service status {}", timeScaleDBServiceValid);
    if (!timeScaleDBServiceValid) {
      throw new InvalidRequestException("Cannot process request in BillingStatsTimeSeriesDataFetcher");
    }
    try {
      return getData(accountId, filters, aggregateFunction, groupBy, sortCriteria);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching billing data {}", e);
    }
  }

  protected QLData getData(@NotNull String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMGroupBy> groupByList,
      List<QLBillingSortCriteria> sortCriteria) {
    BillingDataQueryMetadata queryData;
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;
    long timePeriod;
    List<QLCCMEntityGroupBy> groupByEntityList = billingDataQueryBuilder.getGroupByEntity(groupByList);
    List<QLBillingDataTagAggregation> groupByTagList = getGroupByTag(groupByList);
    List<QLBillingDataLabelAggregation> groupByLabelList = getGroupByLabel(groupByList);
    QLCCMTimeSeriesAggregation groupByTime = billingDataQueryBuilder.getGroupByTime(groupByList);
    timePeriod = getTimePeriod(groupByTime);

    if (filters == null) {
      filters = new ArrayList<>();
    }

    // For calculating unallocated cost
    Map<Long, Double> unallocatedCostMapping = null;
    if (billingDataQueryBuilder.showUnallocatedCost(groupByEntityList, filters)) {
      unallocatedCostMapping = getUnallocatedCostData(accountId, filters, groupByList);
      filters = billingDataQueryBuilder.removeInstanceTypeFilter(filters);
    }

    if (!groupByTagList.isEmpty()) {
      groupByEntityList = getGroupByEntityListFromTags(groupByList, groupByEntityList, groupByTagList);
    } else if (!groupByLabelList.isEmpty()) {
      groupByEntityList.add(QLCCMEntityGroupBy.Cluster);
      groupByEntityList = getGroupByEntityListFromLabels(groupByList, groupByEntityList, groupByLabelList);
    }

    if (!billingDataQueryBuilder.isFilterCombinationValid(filters, groupByEntityList)) {
      return QLBillingStackedTimeSeriesData.builder().data(null).info(INVALID_FILTER_MSG).build();
    }
    billingDataQueryBuilder.addFiltersToExcludeUnallocatedRows(filters, groupByEntityList);

    queryData = billingDataQueryBuilder.formQuery(accountId, filters, aggregateFunction, groupByEntityList, groupByTime,
        sortCriteria, !isGroupByNodeOrPodPresent(groupByEntityList));
    log.info("BillingStatsTimeSeriesDataFetcher query: {}", queryData.getQuery());

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        return generateStackedTimeSeriesData(queryData, resultSet, getMinStartTimeFromFilters(filters), timePeriod,
            unallocatedCostMapping, groupByEntityList, accountId);
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingStatsTimeSeriesDataFetcher, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingStatsTimeSeriesDataFetcher, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  protected QLBillingStackedTimeSeriesData generateStackedTimeSeriesData(BillingDataQueryMetadata queryData,
      ResultSet resultSet, long startTimeFromFilters, long timePeriod, Map<Long, Double> unallocatedCostMapping,
      List<QLCCMEntityGroupBy> groupByEntityList, String accountId) throws SQLException {
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeDataPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeCpuPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeMemoryPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeMemoryUtilsPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeCpuUtilsPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeMemoryUtilValuesMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeCpuUtilValuesPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeMemoryLimitPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeMemoryRequestPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeCpuLimitPointMap = new LinkedHashMap<>();
    Map<Long, List<QLBillingTimeDataPoint>> qlTimeCpuRequestPointMap = new LinkedHashMap<>();
    Set<String> instanceIds = new HashSet<>();

    // Checking if namespace should be appended to entity Id in order to distinguish between same workloadNames across
    // Distinct namespaces
    boolean addNamespaceToEntityId = groupByEntityList.contains(QLCCMEntityGroupBy.WorkloadName);
    boolean addClusterIdToEntityId = billingDataQueryBuilder.isClusterDrilldown(groupByEntityList)
        || groupByEntityList.contains(QLCCMEntityGroupBy.Node);
    boolean addAppIdToEntityId = billingDataQueryBuilder.isApplicationDrillDown(groupByEntityList);
    boolean isKeyTypeInstanceId =
        groupByEntityList.contains(QLCCMEntityGroupBy.Node) || groupByEntityList.contains(QLCCMEntityGroupBy.PV);
    boolean isKeyTypeNode = groupByEntityList.contains(QLCCMEntityGroupBy.Node);

    boolean dataPresent = checkAndAddPrecedingZeroValuedData(
        queryData, resultSet, startTimeFromFilters, qlTimeDataPointMap, addClusterIdToEntityId);

    if (dataPresent) {
      do {
        String additionalInfo = "";
        QLBillingTimeDataPointBuilder dataPointBuilder = QLBillingTimeDataPoint.builder();
        // For First Level Idle Cost Drill Down
        QLBillingTimeDataPointBuilder cpuPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryPointBuilder = QLBillingTimeDataPoint.builder();
        // For Leaf level Idle cost Drill Down
        QLBillingTimeDataPointBuilder memoryAvgUtilsPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryMaxUtilsPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryAvgUtilValuePointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryAvgRequestPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryAvgLimitPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder memoryMaxUtilValuePointBuilder = QLBillingTimeDataPoint.builder();

        QLBillingTimeDataPointBuilder cpuAvgUtilsPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder cpuMaxUtilsPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder cpuAvgUtilValuePointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder cpuAvgRequestPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder cpuAvgLimitPointBuilder = QLBillingTimeDataPoint.builder();
        QLBillingTimeDataPointBuilder cpuMaxUtilValuePointBuilder = QLBillingTimeDataPoint.builder();

        String clusterId = "";
        String instanceId = "";
        for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
          switch (field.getDataType()) {
            case DOUBLE:
              switch (field) {
                case CPUIDLECOST:
                  cpuPointBuilder.value(roundingDoubleFieldValue(field, resultSet));
                  break;
                case MEMORYIDLECOST:
                  memoryPointBuilder.value(roundingDoubleFieldValue(field, resultSet));
                  break;
                case MAXCPUUTILIZATION:
                  cpuMaxUtilsPointBuilder.value(roundingDoubleFieldPercentageValue(field, resultSet));
                  break;
                case AVGCPUUTILIZATION:
                  cpuAvgUtilsPointBuilder.value(roundingDoubleFieldPercentageValue(field, resultSet));
                  break;
                case AGGREGATEDCPUUTILIZATIONVALUE:
                  cpuAvgUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case AGGREGATEDCPUREQUEST:
                  cpuAvgRequestPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case AGGREGATEDCPULIMIT:
                  cpuAvgLimitPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case MAXMEMORYUTILIZATION:
                  memoryMaxUtilsPointBuilder.value(roundingDoubleFieldPercentageValue(field, resultSet));
                  break;
                case AVGMEMORYUTILIZATION:
                  memoryAvgUtilsPointBuilder.value(roundingDoubleFieldPercentageValue(field, resultSet));
                  break;
                case AGGREGATEDMEMORYUTILIZATIONVALUE:
                  memoryAvgUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case AGGREGATEDMEMORYREQUEST:
                  memoryAvgRequestPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case AGGREGATEDMEMORYLIMIT:
                  memoryAvgLimitPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / (timePeriod * 1024)));
                  break;
                case AVGCPUUTILIZATIONVALUE:
                  cpuAvgUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                case AVGMEMORYUTILIZATIONVALUE:
                  memoryAvgUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                case MAXCPUUTILIZATIONVALUE:
                  cpuMaxUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                case MAXMEMORYUTILIZATIONVALUE:
                  memoryMaxUtilValuePointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                case CPUREQUEST:
                  cpuAvgRequestPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                case MEMORYREQUEST:
                  memoryAvgRequestPointBuilder.value(
                      roundingDoubleValue(resultSet.getDouble(field.getFieldName()) / 1024));
                  break;
                default:
                  dataPointBuilder.value(roundingDoubleFieldValue(field, resultSet));
              }
              break;
            case STRING:
              if (field == BillingDataMetaDataFields.CLUSTERID) {
                clusterId = resultSet.getString(field.getFieldName());
              } else if (field == BillingDataMetaDataFields.INSTANCEID) {
                instanceId = resultSet.getString(field.getFieldName());
                instanceIds.add(instanceId);
              }
              // Group by has been re-arranged such that additional info gets populated first
              if ((addNamespaceToEntityId && field == BillingDataMetaDataFields.NAMESPACE)
                  || (addClusterIdToEntityId && field == BillingDataMetaDataFields.CLUSTERID)
                  || (addAppIdToEntityId && field == BillingDataMetaDataFields.APPID)
                  || field == BillingDataMetaDataFields.INSTANCENAME) {
                additionalInfo = additionalInfo.equals(EMPTY)
                    ? resultSet.getString(field.getFieldName())
                    : additionalInfo + BillingStatsDefaultKeys.TOKEN + resultSet.getString(field.getFieldName());
                break;
              }

              String entityId = resultSet.getString(field.getFieldName());
              String idWithInfo = (addNamespaceToEntityId || addClusterIdToEntityId || addAppIdToEntityId)
                      && !additionalInfo.equals(EMPTY)
                  ? additionalInfo + BillingStatsDefaultKeys.TOKEN + entityId
                  : entityId;
              cpuPointBuilder.key(buildQLReference(field, entityId, idWithInfo, resultSet));
              memoryPointBuilder.key(buildQLReference(field, entityId, idWithInfo, resultSet));
              dataPointBuilder.key(buildQLReference(field, entityId, idWithInfo, resultSet));

              cpuMaxUtilsPointBuilder.key(buildQLReferenceForUtilization("MAX", idWithInfo));
              cpuAvgUtilsPointBuilder.key(buildQLReferenceForUtilization("AVG", idWithInfo));
              cpuAvgUtilValuePointBuilder.key(buildQLReferenceForUtilization("AVG", idWithInfo));
              cpuAvgRequestPointBuilder.key(buildQLReferenceForUtilization("REQUEST", idWithInfo));
              cpuAvgLimitPointBuilder.key(buildQLReferenceForUtilization("LIMIT", idWithInfo));
              cpuMaxUtilValuePointBuilder.key(buildQLReferenceForUtilization("MAX", idWithInfo));

              memoryMaxUtilsPointBuilder.key(buildQLReferenceForUtilization("MAX", idWithInfo));
              memoryAvgUtilsPointBuilder.key(buildQLReferenceForUtilization("AVG", idWithInfo));
              memoryAvgUtilValuePointBuilder.key(buildQLReferenceForUtilization("AVG", idWithInfo));
              memoryAvgRequestPointBuilder.key(buildQLReferenceForUtilization("REQUEST", idWithInfo));
              memoryAvgLimitPointBuilder.key(buildQLReferenceForUtilization("LIMIT", idWithInfo));
              memoryMaxUtilValuePointBuilder.key(buildQLReferenceForUtilization("MAX", idWithInfo));
              break;
            case TIMESTAMP:
              long time = resultSet.getTimestamp(field.getFieldName(), utils.getDefaultCalendar()).getTime();
              cpuPointBuilder.time(time);
              memoryPointBuilder.time(time);
              dataPointBuilder.time(time);
              cpuMaxUtilsPointBuilder.time(time);
              cpuAvgUtilsPointBuilder.time(time);
              cpuAvgUtilValuePointBuilder.time(time);
              cpuAvgRequestPointBuilder.time(time);
              cpuAvgLimitPointBuilder.time(time);
              cpuMaxUtilValuePointBuilder.time(time);
              memoryMaxUtilsPointBuilder.time(time);
              memoryAvgUtilsPointBuilder.time(time);
              memoryAvgUtilValuePointBuilder.time(time);
              memoryAvgRequestPointBuilder.time(time);
              memoryAvgLimitPointBuilder.time(time);
              memoryMaxUtilValuePointBuilder.time(time);
              break;
            default:
              throw new InvalidRequestException("UnsupportedType " + field.getDataType());
          }
        }

        if (isKeyTypeInstanceId) {
          dataPointBuilder.key(
              QLReference.builder()
                  .name(statsHelper.getEntityName(BillingDataMetaDataFields.INSTANCEID, instanceId, resultSet))
                  .id(clusterId + BillingStatsDefaultKeys.TOKEN + instanceId)
                  .type(BillingDataMetaDataFields.INSTANCEID.name())
                  .build());
        }

        checkDataPointIsValidAndInsert(dataPointBuilder.build(), qlTimeDataPointMap);
        checkDataPointIsValidAndInsert(cpuPointBuilder.build(), qlTimeCpuPointMap);
        checkDataPointIsValidAndInsert(memoryPointBuilder.build(), qlTimeMemoryPointMap);
        checkDataPointIsValidAndInsert(cpuMaxUtilsPointBuilder.build(), qlTimeCpuUtilsPointMap);
        checkDataPointIsValidAndInsert(memoryMaxUtilsPointBuilder.build(), qlTimeMemoryUtilsPointMap);
        checkDataPointIsValidAndInsert(cpuAvgUtilsPointBuilder.build(), qlTimeCpuUtilsPointMap);
        checkDataPointIsValidAndInsert(memoryAvgUtilsPointBuilder.build(), qlTimeMemoryUtilsPointMap);
        checkDataPointIsValidAndInsert(cpuAvgRequestPointBuilder.build(), qlTimeCpuRequestPointMap);
        checkDataPointIsValidAndInsert(cpuAvgLimitPointBuilder.build(), qlTimeCpuLimitPointMap);
        checkDataPointIsValidAndInsert(memoryAvgRequestPointBuilder.build(), qlTimeMemoryRequestPointMap);
        checkDataPointIsValidAndInsert(memoryAvgLimitPointBuilder.build(), qlTimeMemoryLimitPointMap);
        checkDataPointIsValidAndInsert(cpuAvgUtilValuePointBuilder.build(), qlTimeCpuUtilValuesPointMap);
        checkDataPointIsValidAndInsert(memoryAvgUtilValuePointBuilder.build(), qlTimeMemoryUtilValuesMap);
        checkDataPointIsValidAndInsert(cpuMaxUtilValuePointBuilder.build(), qlTimeCpuUtilValuesPointMap);
        checkDataPointIsValidAndInsert(memoryMaxUtilValuePointBuilder.build(), qlTimeMemoryUtilValuesMap);
      } while (resultSet != null && resultSet.next());
    }

    QLBillingStackedTimeSeriesDataBuilder timeSeriesDataBuilder = QLBillingStackedTimeSeriesData.builder();

    List<QLBillingStackedTimeSeriesDataPoint> dataPoints = prepareStackedTimeSeriesData(queryData, qlTimeDataPointMap);

    if (isKeyTypeNode) {
      List<InstanceData> instanceData =
          instanceDataService.fetchInstanceDataForGivenInstances(accountId, new ArrayList<>(instanceIds));
      if (instanceData != null) {
        Map<String, String> idToName = instanceData.stream().collect(Collectors.toMap(entry
            -> entry.getClusterId() + BillingStatsDefaultKeys.TOKEN + entry.getInstanceId(),
            InstanceData::getInstanceName));
        dataPoints.forEach(dataPoint
            -> dataPoint.getValues().forEach(value -> value.getKey().setName(idToName.get(value.getKey().getId()))));
      }
    }

    if (unallocatedCostMapping != null) {
      dataPoints.forEach(dataPoint -> {
        long time = dataPoint.getTime();
        if (unallocatedCostMapping.containsKey(time)) {
          dataPoint.getValues().add(QLBillingDataPoint.builder()
                                        .key(QLReference.builder()
                                                 .name(UNALLOCATED_COST_ENTRY)
                                                 .id(UNALLOCATED_COST_ENTRY + ":" + UNALLOCATED_COST_ENTRY)
                                                 .build())
                                        .value(unallocatedCostMapping.get(time))
                                        .build());
        }
      });
    }
    return timeSeriesDataBuilder.data(dataPoints)
        .cpuIdleCost(prepareStackedTimeSeriesData(queryData, qlTimeCpuPointMap))
        .memoryIdleCost(prepareStackedTimeSeriesData(queryData, qlTimeMemoryPointMap))
        .cpuUtilMetrics(prepareStackedTimeSeriesData(queryData, qlTimeCpuUtilsPointMap))
        .memoryUtilMetrics(prepareStackedTimeSeriesData(queryData, qlTimeMemoryUtilsPointMap))
        .cpuUtilValues(prepareStackedTimeSeriesData(queryData, qlTimeCpuUtilValuesPointMap))
        .memoryUtilValues(prepareStackedTimeSeriesData(queryData, qlTimeMemoryUtilValuesMap))
        .cpuRequest(prepareStackedTimeSeriesData(queryData, qlTimeCpuRequestPointMap))
        .cpuLimit(prepareStackedTimeSeriesData(queryData, qlTimeCpuLimitPointMap))
        .memoryRequest(prepareStackedTimeSeriesData(queryData, qlTimeMemoryRequestPointMap))
        .memoryLimit(prepareStackedTimeSeriesData(queryData, qlTimeMemoryLimitPointMap))
        .build();
  }

  private void checkDataPointIsValidAndInsert(
      QLBillingTimeDataPoint dataPoint, Map<Long, List<QLBillingTimeDataPoint>> qlTimeDataPointMap) {
    if (dataPoint.getValue() != null) {
      qlTimeDataPointMap.computeIfAbsent(dataPoint.getTime(), k -> new ArrayList<>());
      qlTimeDataPointMap.get(dataPoint.getTime()).add(dataPoint);
    }
  }

  private long getMinStartTimeFromFilters(List<QLBillingDataFilter> filters) {
    long minStartTime = Long.MAX_VALUE;
    for (QLBillingDataFilter filter : filters) {
      if (filter.getStartTime() != null) {
        minStartTime = Math.min(filter.getStartTime().getValue().longValue(), minStartTime);
      }
    }
    return minStartTime;
  }

  private boolean checkStartTimeFilterIsValid(long startTime) {
    return startTime != Long.MAX_VALUE;
  }

  private double roundingDoubleFieldValue(BillingDataMetaDataFields field, ResultSet resultSet) throws SQLException {
    return Math.round(resultSet.getDouble(field.getFieldName()) * 100D) / 100D;
  }

  private double roundingDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  private double roundingDoubleFieldPercentageValue(BillingDataMetaDataFields field, ResultSet resultSet)
      throws SQLException {
    return 100 * roundingDoubleFieldValue(field, resultSet);
  }

  // returns true if data is present
  private boolean checkAndAddPrecedingZeroValuedData(BillingDataQueryMetadata queryData, ResultSet resultSet,
      long startTimeFromFilters, Map<Long, List<QLBillingTimeDataPoint>> qlTimeDataPointMap, boolean isClusterDrilldown)
      throws SQLException {
    if (resultSet != null && resultSet.next()) {
      String entityId = "";
      String idWithInfo = "";
      String additionalInfo = EMPTY;
      String timeFieldName = BillingDataMetaDataFields.STARTTIME.getFieldName();
      boolean addNamespaceToEntityId = queryData.groupByFields.contains(BillingDataMetaDataFields.WORKLOADNAME);
      for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
        switch (field.getDataType()) {
          case STRING:
            if ((addNamespaceToEntityId && field == BillingDataMetaDataFields.NAMESPACE)
                || (isClusterDrilldown && field == BillingDataMetaDataFields.CLUSTERID)
                || field == BillingDataMetaDataFields.INSTANCENAME) {
              additionalInfo = additionalInfo.equals(EMPTY)
                  ? resultSet.getString(field.getFieldName())
                  : additionalInfo + BillingStatsDefaultKeys.TOKEN + resultSet.getString(field.getFieldName());
              break;
            }
            entityId = resultSet.getString(field.getFieldName());
            idWithInfo = (addNamespaceToEntityId || isClusterDrilldown) && !additionalInfo.equals(EMPTY)
                ? additionalInfo + BillingStatsDefaultKeys.TOKEN + entityId
                : entityId;
            break;
          case TIMESTAMP:
            timeFieldName = field.getFieldName();
            break;
          default:
            break;
        }
      }
      if (checkStartTimeFilterIsValid(startTimeFromFilters)) {
        long timeOfFirstEntry = resultSet.getTimestamp(timeFieldName, utils.getDefaultCalendar()).getTime();
        addPrecedingZeroValuedData(
            queryData, qlTimeDataPointMap, entityId, idWithInfo, timeOfFirstEntry, startTimeFromFilters, resultSet);
      }
      return true;
    } else {
      return false;
    }
  }

  private void addPrecedingZeroValuedData(BillingDataQueryMetadata queryData,
      Map<Long, List<QLBillingTimeDataPoint>> qlTimeDataPointMap, String entityId, String idWithInfo,
      long timeOfFirstEntry, long startTimeFromFilters, ResultSet resultSet) throws SQLException {
    int missingDays = (int) ((timeOfFirstEntry - startTimeFromFilters) / ONE_DAY_MILLIS);
    long startTime = timeOfFirstEntry - missingDays * ONE_DAY_MILLIS;
    while (timeOfFirstEntry > startTime) {
      QLBillingTimeDataPointBuilder dataPointBuilder = QLBillingTimeDataPoint.builder();
      for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
        switch (field.getDataType()) {
          case DOUBLE:
            dataPointBuilder.value(0);
            break;
          case STRING:
            dataPointBuilder.key(buildQLReference(field, entityId, idWithInfo, resultSet));
            break;
          case TIMESTAMP:
            dataPointBuilder.time(startTime);
            break;
          default:
            throw new InvalidRequestException("UnsupportedType " + field.getDataType());
        }
      }
      QLBillingTimeDataPoint dataPoint = dataPointBuilder.build();
      qlTimeDataPointMap.computeIfAbsent(dataPoint.getTime(), k -> new ArrayList<>());
      qlTimeDataPointMap.get(dataPoint.getTime()).add(dataPoint);
      startTime += ONE_DAY_MILLIS;
    }
  }

  private QLReference buildQLReference(BillingDataMetaDataFields field, String key, String id, ResultSet resultSet)
      throws SQLException {
    return QLReference.builder()
        .type(field.getFieldName())
        .id(id)
        .name(statsHelper.getEntityName(field, key, resultSet))
        .build();
  }

  private QLReference buildQLReferenceForUtilization(String name, String id) {
    return QLReference.builder().name(name).id(id).type("Utilization").build();
  }

  private List<QLBillingStackedTimeSeriesDataPoint> prepareStackedTimeSeriesData(
      BillingDataQueryMetadata queryData, Map<Long, List<QLBillingTimeDataPoint>> qlTimeDataPointMap) {
    List<QLBillingStackedTimeSeriesDataPoint> timeSeriesDataPoints = new ArrayList<>();

    qlTimeDataPointMap.keySet().forEach(time -> {
      List<QLBillingTimeDataPoint> timeDataPoints = qlTimeDataPointMap.get(time);
      QLBillingStackedTimeSeriesDataPointBuilder builder = QLBillingStackedTimeSeriesDataPoint.builder();
      List<QLBillingDataPoint> dataPoints =
          timeDataPoints.stream().map(QLBillingTimeDataPoint::getQLBillingDataPoint).collect(Collectors.toList());
      if (queryData.getGroupByFields() != null) {
        dataPoints = filterQLDataPoints(dataPoints, queryData.getFilters(), queryData.getGroupByFields().get(0));
      }
      builder.values(dataPoints).time(time);
      timeSeriesDataPoints.add(builder.build());
    });
    return timeSeriesDataPoints;
  }

  private List<QLBillingDataPoint> filterQLDataPoints(
      List<QLBillingDataPoint> dataPoints, List<QLBillingDataFilter> filters, BillingDataMetaDataFields groupBy) {
    if (groupBy != null) {
      Map<BillingDataMetaDataFields, String[]> filterValueMap = getFilterDeploymentMetaDataField(filters);
      String[] values = filterValueMap.get(groupBy);
      if (values != null) {
        final Set valueSet = Sets.newHashSet(values);
        dataPoints.removeIf(dataPoint -> !valueSet.contains(dataPoint.getKey().getId()));
      }
    }
    return dataPoints;
  }

  private Map<BillingDataMetaDataFields, String[]> getFilterDeploymentMetaDataField(List<QLBillingDataFilter> filters) {
    Map<BillingDataMetaDataFields, String[]> filterMap = new EnumMap<>(BillingDataMetaDataFields.class);
    for (QLBillingDataFilter filter : filters) {
      if (filter.getApplication() != null) {
        filterMap.put(BillingDataMetaDataFields.APPID, filter.getApplication().getValues());
      }
    }
    return filterMap;
  }

  private boolean isGroupByNodeOrPodPresent(List<QLCCMEntityGroupBy> entityGroupBy) {
    for (QLCCMEntityGroupBy groupBy : entityGroupBy) {
      switch (groupBy) {
        case Node:
        case Pod:
          return true;
        default:
      }
    }
    return false;
  }

  @Override
  public QLData postFetch(String accountId, List<QLCCMGroupBy> groupBy,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLBillingSortCriteria> sortCriteria, QLData qlData,
      Integer limit, boolean includeOthers) {
    qlData = super.postFetch(accountId, groupBy, aggregateFunction, sortCriteria, qlData, limit, includeOthers);
    if (limit.equals(BillingStatsDefaultKeys.DEFAULT_LIMIT)
        || (!isEntityGroupByPresent(groupBy) && getGroupByLabel(groupBy).isEmpty() && getGroupByTag(groupBy).isEmpty())
        || qlData == null) {
      return qlData;
    }
    Map<String, Double> aggregatedData = new HashMap<>();
    QLBillingStackedTimeSeriesData data = (QLBillingStackedTimeSeriesData) qlData;
    if (data.getData() == null) {
      return qlData;
    }
    data.getData().forEach(dataPoint -> {
      for (QLBillingDataPoint entry : dataPoint.getValues()) {
        String key = entry.getKey().getId();
        if (aggregatedData.containsKey(key)) {
          aggregatedData.put(key, entry.getValue().doubleValue() + aggregatedData.get(key));
        } else {
          aggregatedData.put(key, entry.getValue().doubleValue());
        }
      }
    });
    List<String> selectedIdsAfterLimit = billingDataHelper.getElementIdsAfterLimit(aggregatedData, limit);

    return QLBillingStackedTimeSeriesData.builder()
        .data(getDataAfterLimit(data, selectedIdsAfterLimit, includeOthers))
        .cpuIdleCost(data.getCpuIdleCost())
        .memoryIdleCost(data.getMemoryIdleCost())
        .cpuUtilMetrics(data.getCpuUtilMetrics())
        .memoryUtilMetrics(data.getMemoryUtilMetrics())
        .info(data.getInfo())
        .build();
  }

  @Override
  protected QLData fetchSelectedFields(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sort, Integer limit,
      Integer offset, boolean skipRoundOff, DataFetchingEnvironment dataFetchingEnvironment) {
    return null;
  }

  private List<QLBillingStackedTimeSeriesDataPoint> getDataAfterLimit(
      QLBillingStackedTimeSeriesData data, List<String> selectedIdsAfterLimit, boolean includeOthers) {
    List<QLBillingStackedTimeSeriesDataPoint> limitProcessedData = new ArrayList<>();
    data.getData().forEach(dataPoint -> {
      List<QLBillingDataPoint> limitProcessedValues = new ArrayList<>();
      QLBillingDataPoint others =
          QLBillingDataPoint.builder()
              .key(
                  QLReference.builder().id(BillingStatsDefaultKeys.OTHERS).name(BillingStatsDefaultKeys.OTHERS).build())
              .value(0)
              .build();
      for (QLBillingDataPoint entry : dataPoint.getValues()) {
        String key = entry.getKey().getId();
        if (selectedIdsAfterLimit.contains(key)) {
          limitProcessedValues.add(entry);
        } else {
          others.setValue(others.getValue().doubleValue() + entry.getValue().doubleValue());
        }
      }

      if (others.getValue().doubleValue() > 0 && includeOthers) {
        others.setValue(billingDataHelper.getRoundedDoubleValue(others.getValue().doubleValue()));
        limitProcessedValues.add(others);
      }

      limitProcessedData.add(
          QLBillingStackedTimeSeriesDataPoint.builder().time(dataPoint.getTime()).values(limitProcessedValues).build());
    });
    return limitProcessedData;
  }

  private boolean isEntityGroupByPresent(List<QLCCMGroupBy> groupByList) {
    for (QLCCMGroupBy groupBy : groupByList) {
      if (groupBy.getEntityGroupBy() != null) {
        return true;
      }
    }
    return false;
  }

  private long getTimePeriod(QLCCMTimeSeriesAggregation groupByTime) {
    if (groupByTime == null) {
      return ONE_DAY_SEC;
    }
    switch (groupByTime.getTimeGroupType()) {
      case HOUR:
        return ONE_HOUR_SEC;
      case MONTH:
        return ONE_DAY_SEC * 30;
      case DAY:
      default:
        return ONE_DAY_SEC;
    }
  }

  protected Map<Long, Double> getUnallocatedCostData(
      @NotNull String accountId, List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupByList) {
    BillingDataQueryMetadata queryData;
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;

    List<QLCCMAggregationFunction> aggregateFunction =
        Collections.singletonList(QLCCMAggregationFunction.builder()
                                      .operationType(QLCCMAggregateOperation.SUM)
                                      .columnName("unallocatedcost")
                                      .build());
    QLCCMTimeSeriesAggregation groupByTime = billingDataQueryBuilder.getGroupByTime(groupByList);

    queryData = billingDataQueryBuilder.formQuery(
        accountId, filters, aggregateFunction, Collections.emptyList(), groupByTime, Collections.emptyList(), true);
    log.info("BillingStatsTimeSeriesDataFetcher query for unallocated cost: {}", queryData.getQuery());
    log.info(queryData.getQuery());

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        Map<Long, Double> unallocatedCostMapping = new HashMap<>();
        while (resultSet != null && resultSet.next()) {
          double unallocatedCost = 0;
          long time = 0L;
          for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
            switch (field.getDataType()) {
              case DOUBLE:
                unallocatedCost = roundingDoubleFieldValue(field, resultSet);
                break;
              case TIMESTAMP:
                time = resultSet.getTimestamp(field.getFieldName(), utils.getDefaultCalendar()).getTime();
                break;
              default:
                break;
            }
          }
          unallocatedCostMapping.put(time, unallocatedCost);
        }
        return unallocatedCostMapping;
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingStatsTimeSeriesDataFetcher for unallocated cost, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingStatsTimeSeriesDataFetcher for unallocated cost, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  @Override
  public String getEntityType() {
    return NameService.deployment;
  }

  @Override
  protected QLBillingDataTagAggregation getTagAggregation(QLCCMGroupBy groupBy) {
    return groupBy.getTagAggregation();
  }

  @Override
  protected QLBillingDataLabelAggregation getLabelAggregation(QLCCMGroupBy groupBy) {
    return groupBy.getLabelAggregation();
  }

  @Override
  protected QLCCMEntityGroupBy getGroupByEntityFromTag(QLBillingDataTagAggregation groupByTag) {
    return billingDataQueryBuilder.getGroupByEntityFromTag(groupByTag);
  }

  @Override
  protected QLCCMEntityGroupBy getGroupByEntityFromLabel(QLBillingDataLabelAggregation groupByLabel) {
    return billingDataQueryBuilder.getGroupByEntityFromLabel(groupByLabel);
  }

  @Override
  protected EntityType getEntityType(QLBillingDataTagType entityType) {
    return billingDataQueryBuilder.getEntityType(entityType);
  }

  @Override
  protected QLCCMEntityGroupBy getEntityAggregation(QLCCMGroupBy groupBy) {
    return groupBy.getEntityGroupBy();
  }

  @Override
  public boolean isCESampleAccountIdAllowed() {
    return true;
  }
}
