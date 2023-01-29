/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_NODE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD_FARGATE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PV;

import static software.wings.graphql.datafetcher.billing.BillingDataQueryBuilder.INVALID_FILTER_MSG;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.AbstractStatsDataFetcherWithAggregationListAndLimit;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableData;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableData.QLNodeAndPodDetailsTableDataBuilder;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableRow;
import software.wings.graphql.schema.type.aggregation.billing.QLNodeAndPodDetailsTableRow.QLNodeAndPodDetailsTableRowBuilder;
import software.wings.graphql.schema.type.aggregation.billing.QLPVDetailsTableRow;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class NodeAndPodDetailsDataFetcher
    extends AbstractStatsDataFetcherWithAggregationListAndLimit<QLCCMAggregationFunction, QLBillingDataFilter,
        QLCCMGroupBy, QLBillingSortCriteria> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject BillingDataQueryBuilder billingDataQueryBuilder;
  @Inject BillingDataHelper billingDataHelper;
  @Inject InstanceDataServiceImpl instanceDataService;
  @Inject CeAccountExpirationChecker accountChecker;

  private static final String INSTANCE_CATEGORY = "instance_category";
  private static final String OPERATING_SYSTEM = "operating_system";
  private static final String INSTANCE_TYPE_NODE = "K8S_NODE";
  private static final String INSTANCE_TYPE_PODS = "K8S_POD";
  private static final String INSTANCE_TYPE_PV = "K8S_PV";
  private static final String NAMESPACE = "namespace";
  private static final String WORKLOAD = "workload_name";
  private static final String PARENT_RESOURCE_ID = "parent_resource_id";
  private static final String NODE_POOL_NAME = "node_pool_name";
  public static final String CLOUD_PROVIDER_INSTANCE_ID = "cloud_provider_instance_id";
  private static final String K8S_POD_CAPACITY = "pod_capacity";
  private static final String DEFAULT_STRING_VALUE = "-";
  private static final InstanceData DEFAULT_INSTANCE_DATA = InstanceData.builder().metaData(new HashMap<>()).build();
  private static final String CLAIM_NAME = "claim_name";
  private static final String CLAIM_NAMESPACE = "claim_namespace";

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLData fetch(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sortCriteria,
      Integer limit, Integer offset) {
    accountChecker.checkIsCeEnabled(accountId);
    try {
      if (timeScaleDBService.isValid()) {
        return getData(accountId, filters, aggregateFunction, groupBy, sortCriteria, limit, offset);
      } else {
        throw new InvalidRequestException("Cannot process request in NodeAndPodDetailsDataFetcher");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching billing data {}", e);
    }
  }

  protected QLNodeAndPodDetailsTableData getData(@NotNull String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMGroupBy> groupByList,
      List<QLBillingSortCriteria> sortCriteria, Integer limit, Integer offset) {
    return getNodeAndPodData(accountId, filters, aggregateFunction, groupByList, sortCriteria, limit, offset, false);
  }

  public QLNodeAndPodDetailsTableData getNodeAndPodData(@NotNull String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMGroupBy> groupByList,
      List<QLBillingSortCriteria> sortCriteria, Integer limit, Integer offset, boolean skipRoundOff) {
    BillingDataQueryMetadata queryData;
    ResultSet resultSet = null;
    QLNodeAndPodDetailsTableData costData = null;
    List<QLCCMEntityGroupBy> groupByEntityList = billingDataQueryBuilder.getGroupByEntity(groupByList);
    QLCCMTimeSeriesAggregation groupByTime = billingDataQueryBuilder.getGroupByTime(groupByList);

    if (!billingDataQueryBuilder.isFilterCombinationValid(filters, groupByEntityList)) {
      return QLNodeAndPodDetailsTableData.builder().data(null).info(INVALID_FILTER_MSG).build();
    }
    queryData = billingDataQueryBuilder.formNodeAndPodDetailsQuery(
        accountId, filters, aggregateFunction, groupByEntityList, groupByTime, sortCriteria, limit, offset);

    log.info("NodeAndPodDetailsDataFetcher query!! {}", queryData.getQuery());
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(queryData.getQuery());
        successful = true;
        costData = generateCostData(queryData, resultSet, skipRoundOff);
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in NodeAndPodDetailsDataFetcher, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in NodeAndPodDetailsDataFetcher, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }

    if (costData != null && !(costData.getData().isEmpty() && costData.getPvData().isEmpty())) {
      return getFieldsFromInstanceData(costData, filters, accountId);
    }

    return null;
  }

  private QLNodeAndPodDetailsTableData generateCostData(
      BillingDataQueryMetadata queryData, ResultSet resultSet, boolean skipRoundOff) throws SQLException {
    List<QLNodeAndPodDetailsTableRow> qlNodeAndPodDetailsTableRows = new ArrayList<>();
    List<QLPVDetailsTableRow> qlpvDetailsTableRows = new ArrayList<>();

    while (resultSet != null && resultSet.next()) {
      String entityId = BillingStatsDefaultKeys.ENTITYID;
      String name = BillingStatsDefaultKeys.NAME;
      Double totalCost = BillingStatsDefaultKeys.TOTALCOST;
      Double idleCost = BillingStatsDefaultKeys.IDLECOST;
      Double systemCost = BillingStatsDefaultKeys.SYSTEMCOST;
      Double networkCost = BillingStatsDefaultKeys.NETWORKCOST;
      Double unallocatedCost = BillingStatsDefaultKeys.UNALLOCATEDCOST;
      Double memoryBillingAmount = BillingStatsDefaultKeys.TOTALCOST;
      Double cpuBillingAmount = BillingStatsDefaultKeys.TOTALCOST;
      Double storageUnallocatedCost = BillingStatsDefaultKeys.TOTALCOST;
      String clusterName = BillingStatsDefaultKeys.CLUSTERNAME;
      String clusterId = BillingStatsDefaultKeys.CLUSTERID;
      String instanceType = BillingStatsDefaultKeys.INSTANCETYPE;
      String namespace = DEFAULT_STRING_VALUE;
      String workloadName = DEFAULT_STRING_VALUE;
      String cloudProviderName = DEFAULT_STRING_VALUE;
      String region = DEFAULT_STRING_VALUE;

      Double storageCost = -1D;
      Double storageUsed = -1D;
      Double storageRequested = -1D;
      Double storageIdleCost = -1D;

      Double memoryUnallocatedCost = BillingStatsDefaultKeys.TOTALCOST;
      Double cpuUnallocatedCost = BillingStatsDefaultKeys.TOTALCOST;
      Double memoryIdleCost = BillingStatsDefaultKeys.TOTALCOST;
      Double cpuIdleCost = BillingStatsDefaultKeys.TOTALCOST;

      for (BillingDataQueryMetadata.BillingDataMetaDataFields field : queryData.getFieldNames()) {
        switch (field) {
          case INSTANCEID:
            entityId = resultSet.getString(field.getFieldName());
            break;
          case SUM:
            totalCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case IDLECOST:
            idleCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case UNALLOCATEDCOST:
            unallocatedCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case SYSTEMCOST:
            systemCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case NETWORKCOST:
            networkCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case CLUSTERNAME:
            clusterName = resultSet.getString(field.getFieldName());
            break;
          case CLUSTERID:
            clusterId = resultSet.getString(field.getFieldName());
            break;
          case INSTANCENAME:
            name = resultSet.getString(field.getFieldName());
            break;
          case INSTANCETYPE:
            instanceType = resultSet.getString(field.getFieldName());
            break;
          case NAMESPACE:
            namespace
            = resultSet.getString(field.getFieldName());
            break;
          case STORAGECOST:
            storageCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case STORAGEUTILIZATIONVALUE:
            storageUsed = resultSet.getDouble(field.getFieldName());
            break;
          case STORAGEREQUEST:
            storageRequested = resultSet.getDouble(field.getFieldName());
            break;
          case STORAGEACTUALIDLECOST:
            storageIdleCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case WORKLOADNAME:
            workloadName = resultSet.getString(field.getFieldName());
            break;
          case REGION:
            region = resultSet.getString(field.getFieldName());
            break;
          case CLOUDPROVIDER:
            cloudProviderName = resultSet.getString(field.getFieldName());
            break;
          case MEMORYBILLINGAMOUNT:
            memoryBillingAmount = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case CPUBILLINGAMOUNT:
            cpuBillingAmount = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case STORAGEUNALLOCATEDCOST:
            storageUnallocatedCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case MEMORYUNALLOCATEDCOST:
            memoryUnallocatedCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case CPUUNALLOCATEDCOST:
            cpuUnallocatedCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case MEMORYIDLECOST:
            memoryIdleCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          case CPUIDLECOST:
            cpuIdleCost = billingDataHelper.roundingDoubleFieldValue(field, resultSet, skipRoundOff);
            break;
          default:
            break;
        }
      }
      if (INSTANCE_TYPE_PV.equals(instanceType)) {
        qlpvDetailsTableRows.add(QLPVDetailsTableRow.builder()
                                     .storageCost(storageCost)
                                     .storageActualIdleCost(storageIdleCost)
                                     .storageUnallocatedCost(storageUnallocatedCost)
                                     .storageUtilizationValue(storageUsed)
                                     .storageRequest(storageRequested)
                                     .instanceId(entityId)
                                     .id(clusterId + ":" + entityId)
                                     .instanceName(name)
                                     .claimName(workloadName)
                                     .clusterName(clusterName)
                                     .clusterId(clusterId)
                                     .region(region)
                                     .storageClass(DEFAULT_STRING_VALUE)
                                     .volumeType(DEFAULT_STRING_VALUE)
                                     .cloudProvider(cloudProviderName)
                                     .claimNamespace(namespace)
                                     .build());
      } else {
        qlNodeAndPodDetailsTableRows.add(QLNodeAndPodDetailsTableRow.builder()
                                             .name(name)
                                             .id(entityId)
                                             .totalCost(totalCost)
                                             .idleCost(idleCost)
                                             .systemCost(systemCost)
                                             .unallocatedCost(unallocatedCost)
                                             .workload(workloadName)
                                             .networkCost(networkCost)
                                             .clusterName(clusterName)
                                             .clusterId(clusterId)
                                             .namespace(namespace)
                                             .storageCost(storageCost)
                                             .storageRequest(storageRequested)
                                             .storageUtilizationValue(storageUsed)
                                             .memoryBillingAmount(memoryBillingAmount)
                                             .cpuBillingAmount(cpuBillingAmount)
                                             .storageUnallocatedCost(storageUnallocatedCost)
                                             .memoryUnallocatedCost(memoryUnallocatedCost)
                                             .cpuUnallocatedCost(cpuUnallocatedCost)
                                             .memoryIdleCost(memoryIdleCost)
                                             .cpuIdleCost(cpuIdleCost)
                                             .storageActualIdleCost(storageIdleCost)
                                             .build());
      }
    }
    return QLNodeAndPodDetailsTableData.builder()
        .data(qlNodeAndPodDetailsTableRows)
        .pvData(qlpvDetailsTableRows)
        .build();
  }

  private QLNodeAndPodDetailsTableData getFieldsFromInstanceData(
      QLNodeAndPodDetailsTableData costData, List<QLBillingDataFilter> filters, String accountId) {
    Set<String> instanceIds = new HashSet<>();
    List<String> instanceIdWithCluster = new ArrayList<>();

    Map<String, QLNodeAndPodDetailsTableRow> instanceIdToCostData = new HashMap<>();
    Map<String, QLPVDetailsTableRow> instanceIdToPVCostData = new HashMap<>();

    List<InstanceType> instanceTypes = getInstanceType(filters);

    if (instanceTypes.contains(K8S_NODE)) {
      for (QLNodeAndPodDetailsTableRow entry : costData.getData()) {
        instanceIdToCostData.put(entry.getClusterId() + BillingStatsDefaultKeys.TOKEN + entry.getId(), entry);
        instanceIdWithCluster.add(entry.getClusterId() + BillingStatsDefaultKeys.TOKEN + entry.getId());
        instanceIds.add(entry.getId());
      }
    }
    if (instanceTypes.contains(K8S_POD) || instanceTypes.contains(K8S_POD_FARGATE)) {
      for (QLNodeAndPodDetailsTableRow entry : costData.getData()) {
        instanceIdToCostData.put(entry.getId(), entry);
        instanceIdWithCluster.add(entry.getId());
        instanceIds.add(entry.getId());
      }
    }
    if (instanceTypes.contains(K8S_PV)) {
      for (QLPVDetailsTableRow entry : costData.getPvData()) {
        instanceIdToPVCostData.put(entry.getClusterId() + BillingStatsDefaultKeys.TOKEN + entry.getInstanceId(), entry);
        instanceIdWithCluster.add(entry.getClusterId() + BillingStatsDefaultKeys.TOKEN + entry.getInstanceId());
        instanceIds.add(entry.getInstanceId());
      }
    }

    Map<String, InstanceData> instanceIdToInstanceData = new HashMap<>();
    List<InstanceData> instanceDataList =
        instanceDataService.fetchInstanceDataForGivenInstances(accountId, new ArrayList<>(instanceIds));

    for (InstanceData instanceData : instanceDataList) {
      String key = instanceData.getInstanceId();
      if (instanceData.getInstanceType() == K8S_NODE || instanceData.getInstanceType() == K8S_PV) {
        key = instanceData.getClusterId() + BillingStatsDefaultKeys.TOKEN + instanceData.getInstanceId();
      }
      instanceIdToInstanceData.put(key, instanceData);
    }

    QLNodeAndPodDetailsTableDataBuilder qlNodeAndPodDetailsTableDataBuilder = QLNodeAndPodDetailsTableData.builder();
    List<QLNodeAndPodDetailsTableRow> data = new ArrayList<>();

    if (instanceTypes.contains(K8S_NODE)) {
      data.addAll(getDataForNodes(instanceIdToCostData, instanceIdToInstanceData, instanceIdWithCluster));
    }
    if (instanceTypes.contains(K8S_POD) || instanceTypes.contains(K8S_POD_FARGATE)) {
      data.addAll(getDataForPods(instanceIdToCostData, instanceIdToInstanceData, instanceIdWithCluster));
    }
    if (instanceTypes.contains(K8S_PV)) {
      qlNodeAndPodDetailsTableDataBuilder.pvData(
          getDataForPV(instanceIdToPVCostData, instanceIdToInstanceData, instanceIdWithCluster));
    }

    return qlNodeAndPodDetailsTableDataBuilder.data(data).build();
  }

  private List<QLPVDetailsTableRow> getDataForPV(Map<String, QLPVDetailsTableRow> instanceIdToPVCostData,
      Map<String, InstanceData> pvToInstanceDataMap, List<String> pvInstanceIdsWithCluster) {
    List<QLPVDetailsTableRow> qlpvDetailsTableRowList = new ArrayList<>();

    for (String instanceIdWithCluster : pvInstanceIdsWithCluster) {
      QLPVDetailsTableRow billingData = instanceIdToPVCostData.get(instanceIdWithCluster);
      // Since instanceData can be purged, check for null for each access
      InstanceData instanceData = pvToInstanceDataMap.getOrDefault(instanceIdWithCluster, DEFAULT_INSTANCE_DATA);
      if (instanceData.getUsageStopTime() != null) {
        billingData.setDeleteTime(instanceData.getUsageStopTime().toEpochMilli());
      }
      if (instanceData.getUsageStartTime() != null) {
        billingData.setCreateTime(instanceData.getUsageStartTime().toEpochMilli());
      }
      if (instanceData.getStorageResource() != null) {
        billingData.setCapacity(
            billingDataHelper.getRoundedDoubleValue(instanceData.getStorageResource().getCapacity() / 1024D));
      }
      if (instanceData.getMetaData().get(CLAIM_NAME) != null) {
        billingData.setClaimName(instanceData.getMetaData().get(CLAIM_NAME));
      }
      if (instanceData.getMetaData().get(CLAIM_NAMESPACE) != null) {
        billingData.setClaimNamespace(instanceData.getMetaData().get(CLAIM_NAMESPACE));
      }
      if (instanceData.getMetaData().get("region") != null) {
        billingData.setRegion(instanceData.getMetaData().get("region"));
      }
      if (instanceData.getMetaData().get("type") != null) {
        billingData.setStorageClass(instanceData.getMetaData().get("type"));
      }
      if (instanceData.getMetaData().get("pv_type") != null) {
        billingData.setVolumeType(instanceData.getMetaData().get("pv_type").substring(8));
      }
      // The storage values returned from the DB is in MB
      billingData.setStorageUtilizationValue(
          billingDataHelper.getRoundedDoubleValue(billingData.getStorageUtilizationValue() / 1024D));
      billingData.setStorageRequest(billingDataHelper.getRoundedDoubleValue(billingData.getStorageRequest() / 1024D));
      qlpvDetailsTableRowList.add(billingData);
    }
    return qlpvDetailsTableRowList;
  }

  private List<QLNodeAndPodDetailsTableRow> getDataForNodes(
      Map<String, QLNodeAndPodDetailsTableRow> instanceIdToCostData, Map<String, InstanceData> instanceIdToInstanceData,
      List<String> instanceIdsWithCluster) {
    List<QLNodeAndPodDetailsTableRow> entityTableListData = new ArrayList<>();
    instanceIdsWithCluster.forEach(instanceIdWithCluster -> {
      InstanceData entry = instanceIdToInstanceData.getOrDefault(instanceIdWithCluster, DEFAULT_INSTANCE_DATA);
      QLNodeAndPodDetailsTableRow costDataEntry = instanceIdToCostData.get(instanceIdWithCluster);
      QLNodeAndPodDetailsTableRowBuilder builder = QLNodeAndPodDetailsTableRow.builder();
      builder.name(entry.getInstanceName())
          .id(costDataEntry.getClusterId() + BillingStatsDefaultKeys.TOKEN + costDataEntry.getId())
          .nodeId(costDataEntry.getId())
          .clusterName(costDataEntry.getClusterName())
          .clusterId(costDataEntry.getClusterId())
          .nodePoolName(entry.getMetaData().getOrDefault(NODE_POOL_NAME, DEFAULT_STRING_VALUE))
          .cloudProviderInstanceId(entry.getMetaData().getOrDefault(CLOUD_PROVIDER_INSTANCE_ID, DEFAULT_STRING_VALUE))
          .podCapacity(entry.getMetaData().getOrDefault(K8S_POD_CAPACITY, DEFAULT_STRING_VALUE))
          .totalCost(costDataEntry.getTotalCost())
          .idleCost(costDataEntry.getIdleCost())
          .systemCost(costDataEntry.getSystemCost())
          .unallocatedCost(costDataEntry.getUnallocatedCost())
          .networkCost(costDataEntry.getNetworkCost())
          .memoryBillingAmount(costDataEntry.getMemoryBillingAmount())
          .cpuBillingAmount(costDataEntry.getCpuBillingAmount())
          .storageUnallocatedCost(costDataEntry.getStorageUnallocatedCost())
          .memoryUnallocatedCost(costDataEntry.getMemoryUnallocatedCost())
          .cpuUnallocatedCost(costDataEntry.getCpuUnallocatedCost())
          .memoryIdleCost(costDataEntry.getMemoryIdleCost())
          .cpuIdleCost(costDataEntry.getCpuIdleCost())
          .storageCost(0D)
          .storageUtilizationValue(0D)
          .storageRequest(0D)
          .storageActualIdleCost(0D)
          .cpuAllocatable(-1D)
          .memoryAllocatable(-1D)
          .machineType(entry.getMetaData().getOrDefault(OPERATING_SYSTEM, DEFAULT_STRING_VALUE))
          .instanceCategory(entry.getMetaData().getOrDefault(INSTANCE_CATEGORY, DEFAULT_STRING_VALUE));
      if (entry.getTotalResource() != null) {
        builder.cpuAllocatable(billingDataHelper.getRoundedDoubleValue(entry.getTotalResource().getCpuUnits() / 1024))
            .memoryAllocatable(billingDataHelper.getRoundedDoubleValue(entry.getTotalResource().getMemoryMb() / 1024));
      }
      if (entry.getUsageStopTime() != null) {
        builder.deleteTime(entry.getUsageStopTime().toEpochMilli());
      }
      if (entry.getUsageStartTime() != null) {
        builder.createTime(entry.getUsageStartTime().toEpochMilli());
      }
      entityTableListData.add(builder.build());
    });
    return entityTableListData;
  }

  private List<QLNodeAndPodDetailsTableRow> getDataForPods(
      Map<String, QLNodeAndPodDetailsTableRow> instanceIdToCostData, Map<String, InstanceData> instanceIdToInstanceData,
      List<String> instanceIds) {
    List<QLNodeAndPodDetailsTableRow> entityTableListData = new ArrayList<>();
    instanceIds.forEach(instanceId -> {
      InstanceData entry = instanceIdToInstanceData.getOrDefault(instanceId, DEFAULT_INSTANCE_DATA);
      QLNodeAndPodDetailsTableRow costDataEntry = instanceIdToCostData.get(instanceId);
      QLNodeAndPodDetailsTableRowBuilder builder = QLNodeAndPodDetailsTableRow.builder();
      builder.name(costDataEntry.getName())
          .id(instanceId)
          .namespace(entry.getMetaData().getOrDefault(NAMESPACE, costDataEntry.getNamespace()))
          .workload(costDataEntry.getWorkload())
          .clusterName(costDataEntry.getClusterName())
          .clusterId(costDataEntry.getClusterId())
          .node(entry.getMetaData().getOrDefault(PARENT_RESOURCE_ID, DEFAULT_STRING_VALUE))
          .nodePoolName(entry.getMetaData().getOrDefault(NODE_POOL_NAME, DEFAULT_STRING_VALUE))
          .totalCost(costDataEntry.getTotalCost())
          .idleCost(costDataEntry.getIdleCost())
          .systemCost(costDataEntry.getSystemCost())
          .unallocatedCost(costDataEntry.getUnallocatedCost())
          .memoryBillingAmount(costDataEntry.getMemoryBillingAmount())
          .cpuBillingAmount(costDataEntry.getCpuBillingAmount())
          .storageUnallocatedCost(costDataEntry.getStorageUnallocatedCost())
          .memoryUnallocatedCost(costDataEntry.getMemoryUnallocatedCost())
          .cpuUnallocatedCost(costDataEntry.getCpuUnallocatedCost())
          .memoryIdleCost(costDataEntry.getMemoryIdleCost())
          .cpuIdleCost(costDataEntry.getCpuIdleCost())
          .networkCost(costDataEntry.getNetworkCost())
          .storageCost(billingDataHelper.getRoundedDoubleValue(costDataEntry.getStorageCost()))
          .storageActualIdleCost(billingDataHelper.getRoundedDoubleValue(costDataEntry.getStorageActualIdleCost()))
          .storageUtilizationValue(
              billingDataHelper.getRoundedDoubleValue(costDataEntry.getStorageUtilizationValue() / 1024D))
          .storageRequest(billingDataHelper.getRoundedDoubleValue(costDataEntry.getStorageRequest() / 1024D))
          .cpuRequested(-1D)
          .memoryRequested(-1D);
      if (entry.getUsageStopTime() != null) {
        builder.deleteTime(entry.getUsageStopTime().toEpochMilli());
      }

      if (entry.getTotalResource() != null) {
        builder.cpuRequested(billingDataHelper.getRoundedDoubleValue(entry.getTotalResource().getCpuUnits() / 1024))
            .memoryRequested(billingDataHelper.getRoundedDoubleValue(entry.getTotalResource().getMemoryMb() / 1024));
      }

      if (entry.getUsageStartTime() != null) {
        builder.createTime(entry.getUsageStartTime().toEpochMilli());
      }
      entityTableListData.add(builder.build());
    });
    return entityTableListData;
  }

  private List<InstanceType> getInstanceType(List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      if (filter.getInstanceType() != null) {
        return Arrays.stream(filter.getInstanceType().getValues())
            .map(InstanceType::valueOf)
            .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }

  @Override
  protected QLData postFetch(String accountId, List<QLCCMGroupBy> groupByList,
      List<QLCCMAggregationFunction> aggregations, List<QLBillingSortCriteria> sort, QLData qlData, Integer limit,
      boolean includeOthers) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }

  @Override
  protected QLData fetchSelectedFields(String accountId, List<QLCCMAggregationFunction> aggregateFunction,
      List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy, List<QLBillingSortCriteria> sort, Integer limit,
      Integer offset, boolean skipRoundOff, DataFetchingEnvironment dataFetchingEnvironment) {
    return null;
  }

  @Override
  public boolean isCESampleAccountIdAllowed() {
    return true;
  }
}
