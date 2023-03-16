/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_NODE;
import static io.harness.ccm.views.graphql.ViewsQueryBuilder.K8S_PV;
import static io.harness.ccm.views.utils.ClusterTableKeys.ID_SEPARATOR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.InstanceDataService;
import io.harness.ccm.views.entities.InstanceDetails;
import io.harness.ccm.views.entities.InstanceDetails.InstanceDetailsBuilder;
import io.harness.ccm.views.entities.StorageDetails;
import io.harness.ccm.views.entities.StorageDetails.StorageDetailsBuilder;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class InstanceDetailsHelper {
  @Inject InstanceDataService instanceDataService;
  @Inject ViewsQueryHelper viewsQueryHelper;

  private static final String INSTANCE_CATEGORY = "instance_category";
  private static final String OPERATING_SYSTEM = "operating_system";
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

  public List<QLCEViewEntityStatsDataPoint> getInstanceDetails(
      List<QLCEViewEntityStatsDataPoint> costData, String instanceType, String accountId) {
    Set<String> instanceIds = new HashSet<>();
    List<String> instanceIdWithCluster = new ArrayList<>();

    Map<String, QLCEViewEntityStatsDataPoint> instanceIdToCostData = new HashMap<>();

    for (QLCEViewEntityStatsDataPoint entry : costData) {
      instanceIdToCostData.put(
          entry.getClusterData().getClusterId() + ID_SEPARATOR + entry.getClusterData().getInstanceId(), entry);
      instanceIdWithCluster.add(
          entry.getClusterData().getClusterId() + ID_SEPARATOR + entry.getClusterData().getInstanceId());
      instanceIds.add(entry.getClusterData().getInstanceId());
    }

    Map<String, InstanceData> instanceIdToInstanceData = new HashMap<>();
    List<InstanceData> instanceDataList =
        instanceDataService.fetchInstanceDataForGivenInstances(accountId, new ArrayList<>(instanceIds));

    for (InstanceData instanceData : instanceDataList) {
      String key = instanceData.getClusterId() + ID_SEPARATOR + instanceData.getInstanceId();
      instanceIdToInstanceData.put(key, instanceData);
    }
    switch (instanceType) {
      case K8S_NODE:
        return getDataForNodes(instanceIdToCostData, instanceIdToInstanceData, instanceIdWithCluster);
      case K8S_PV:
        return getDataForPV(instanceIdToCostData, instanceIdToInstanceData, instanceIdWithCluster);
      default:
        return getDataForPods(instanceIdToCostData, instanceIdToInstanceData, instanceIdWithCluster);
    }
  }

  private List<QLCEViewEntityStatsDataPoint> getDataForNodes(
      Map<String, QLCEViewEntityStatsDataPoint> instanceIdToCostData,
      Map<String, InstanceData> instanceIdToInstanceData, List<String> instanceIdsWithCluster) {
    List<QLCEViewEntityStatsDataPoint> dataPoints = new ArrayList<>();
    instanceIdsWithCluster.forEach(instanceIdWithCluster -> {
      InstanceData entry = instanceIdToInstanceData.getOrDefault(instanceIdWithCluster, DEFAULT_INSTANCE_DATA);
      QLCEViewEntityStatsDataPointBuilder nodeDataBuilder = QLCEViewEntityStatsDataPoint.builder();
      QLCEViewEntityStatsDataPoint costDataEntry = instanceIdToCostData.get(instanceIdWithCluster);
      InstanceDetailsBuilder builder = InstanceDetails.builder();
      builder.name(entry.getInstanceName())
          .id(costDataEntry.getId())
          .nodeId(costDataEntry.getClusterData().getInstanceId())
          .clusterName(costDataEntry.getClusterData().getClusterName())
          .clusterId(costDataEntry.getClusterData().getClusterId())
          .nodePoolName(entry.getMetaData().getOrDefault(NODE_POOL_NAME, DEFAULT_STRING_VALUE))
          .cloudProviderInstanceId(entry.getMetaData().getOrDefault(CLOUD_PROVIDER_INSTANCE_ID, DEFAULT_STRING_VALUE))
          .podCapacity(entry.getMetaData().getOrDefault(K8S_POD_CAPACITY, DEFAULT_STRING_VALUE))
          .totalCost(costDataEntry.getCost().doubleValue())
          .idleCost(
              costDataEntry.getClusterData().getIdleCost() != null ? costDataEntry.getClusterData().getIdleCost() : 0)
          .systemCost(costDataEntry.getClusterData().getSystemCost() != null
                  ? costDataEntry.getClusterData().getSystemCost()
                  : 0)
          .unallocatedCost(costDataEntry.getClusterData().getUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getUnallocatedCost()
                  : 0)
          .networkCost(costDataEntry.getClusterData().getNetworkCost() != null
                  ? costDataEntry.getClusterData().getNetworkCost()
                  : 0)
          .memoryBillingAmount(costDataEntry.getClusterData().getMemoryBillingAmount() != null
                  ? costDataEntry.getClusterData().getMemoryBillingAmount()
                  : 0)
          .cpuBillingAmount(costDataEntry.getClusterData().getCpuBillingAmount() != null
                  ? costDataEntry.getClusterData().getCpuBillingAmount()
                  : 0)
          .storageUnallocatedCost(costDataEntry.getClusterData().getStorageUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getStorageUnallocatedCost()
                  : 0)
          .memoryUnallocatedCost(costDataEntry.getClusterData().getMemoryUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getMemoryUnallocatedCost()
                  : 0)
          .cpuUnallocatedCost(costDataEntry.getClusterData().getCpuUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getCpuUnallocatedCost()
                  : 0)
          .memoryIdleCost(costDataEntry.getClusterData().getMemoryActualIdleCost() != null
                  ? costDataEntry.getClusterData().getMemoryActualIdleCost()
                  : 0)
          .cpuIdleCost(costDataEntry.getClusterData().getCpuActualIdleCost() != null
                  ? costDataEntry.getClusterData().getCpuActualIdleCost()
                  : 0)
          .cpuAllocatable(-1D)
          .memoryAllocatable(-1D)
          .machineType(entry.getMetaData().getOrDefault(OPERATING_SYSTEM, DEFAULT_STRING_VALUE))
          .instanceCategory(entry.getMetaData().getOrDefault(INSTANCE_CATEGORY, DEFAULT_STRING_VALUE));
      if (entry.getTotalResource() != null) {
        builder.cpuAllocatable(viewsQueryHelper.getRoundedDoubleValue(entry.getTotalResource().getCpuUnits() / 1024))
            .memoryAllocatable(viewsQueryHelper.getRoundedDoubleValue(entry.getTotalResource().getMemoryMb() / 1024));
      }
      if (entry.getUsageStopTime() != null) {
        builder.deleteTime(entry.getUsageStopTime().toEpochMilli());
      }
      if (entry.getUsageStartTime() != null) {
        builder.createTime(entry.getUsageStartTime().toEpochMilli());
      }
      nodeDataBuilder.cost(costDataEntry.getCost());
      nodeDataBuilder.costTrend(costDataEntry.getCostTrend());
      nodeDataBuilder.id(costDataEntry.getId());
      nodeDataBuilder.name(costDataEntry.getName());
      nodeDataBuilder.isClusterPerspective(costDataEntry.isClusterPerspective());
      nodeDataBuilder.instanceDetails(builder.build());
      dataPoints.add(nodeDataBuilder.build());
    });
    return dataPoints;
  }

  private List<QLCEViewEntityStatsDataPoint> getDataForPods(
      Map<String, QLCEViewEntityStatsDataPoint> instanceIdToCostData,
      Map<String, InstanceData> instanceIdToInstanceData, List<String> instanceIdsWithCluster) {
    List<QLCEViewEntityStatsDataPoint> dataPoints = new ArrayList<>();
    instanceIdsWithCluster.forEach(instanceIdWithCluster -> {
      InstanceData entry = instanceIdToInstanceData.getOrDefault(instanceIdWithCluster, DEFAULT_INSTANCE_DATA);
      QLCEViewEntityStatsDataPoint costDataEntry = instanceIdToCostData.get(instanceIdWithCluster);
      QLCEViewEntityStatsDataPointBuilder podDataBuilder = QLCEViewEntityStatsDataPoint.builder();
      InstanceDetailsBuilder builder = InstanceDetails.builder();
      builder.name(costDataEntry.getName())
          .id(costDataEntry.getId())
          .namespace(entry.getMetaData().getOrDefault(NAMESPACE, costDataEntry.getClusterData().getNamespace()))
          .workload(costDataEntry.getClusterData().getWorkloadName())
          .clusterName(costDataEntry.getClusterData().getClusterName())
          .clusterId(costDataEntry.getClusterData().getClusterId())
          .node(entry.getMetaData().getOrDefault(PARENT_RESOURCE_ID, DEFAULT_STRING_VALUE))
          .nodePoolName(entry.getMetaData().getOrDefault(NODE_POOL_NAME, DEFAULT_STRING_VALUE))
          .totalCost(costDataEntry.getCost().doubleValue())
          .idleCost(
              costDataEntry.getClusterData().getIdleCost() != null ? costDataEntry.getClusterData().getIdleCost() : 0)
          .systemCost(costDataEntry.getClusterData().getSystemCost() != null
                  ? costDataEntry.getClusterData().getSystemCost()
                  : 0)
          .unallocatedCost(costDataEntry.getClusterData().getUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getUnallocatedCost()
                  : 0)
          .memoryBillingAmount(costDataEntry.getClusterData().getMemoryBillingAmount() != null
                  ? costDataEntry.getClusterData().getMemoryBillingAmount()
                  : 0)
          .cpuBillingAmount(costDataEntry.getClusterData().getCpuBillingAmount() != null
                  ? costDataEntry.getClusterData().getCpuBillingAmount()
                  : 0)
          .storageUnallocatedCost(costDataEntry.getClusterData().getStorageUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getStorageUnallocatedCost()
                  : 0)
          .memoryUnallocatedCost(costDataEntry.getClusterData().getMemoryUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getMemoryUnallocatedCost()
                  : 0)
          .cpuUnallocatedCost(costDataEntry.getClusterData().getCpuUnallocatedCost() != null
                  ? costDataEntry.getClusterData().getCpuUnallocatedCost()
                  : 0)
          .memoryIdleCost(costDataEntry.getClusterData().getMemoryActualIdleCost() != null
                  ? costDataEntry.getClusterData().getMemoryActualIdleCost()
                  : 0)
          .cpuIdleCost(costDataEntry.getClusterData().getCpuActualIdleCost() != null
                  ? costDataEntry.getClusterData().getCpuActualIdleCost()
                  : 0)
          .networkCost(costDataEntry.getClusterData().getNetworkCost() != null
                  ? costDataEntry.getClusterData().getNetworkCost()
                  : 0)
          .storageCost(costDataEntry.getClusterData().getStorageCost() != null
                  ? viewsQueryHelper.getRoundedDoubleValue(costDataEntry.getClusterData().getStorageCost())
                  : 0)
          .storageActualIdleCost(costDataEntry.getClusterData().getStorageActualIdleCost() != null
                  ? viewsQueryHelper.getRoundedDoubleValue(costDataEntry.getClusterData().getStorageActualIdleCost())
                  : 0)
          .storageUtilizationValue(costDataEntry.getClusterData().getStorageUtilizationValue() != null
                  ? viewsQueryHelper.getRoundedDoubleValue(
                      costDataEntry.getClusterData().getStorageUtilizationValue() / 1024D)
                  : 0)
          .storageRequest(costDataEntry.getClusterData().getStorageRequest() != null
                  ? viewsQueryHelper.getRoundedDoubleValue(costDataEntry.getClusterData().getStorageRequest() / 1024D)
                  : 0)
          .cpuRequested(-1D)
          .memoryRequested(-1D);
      if (entry.getUsageStopTime() != null) {
        builder.deleteTime(entry.getUsageStopTime().toEpochMilli());
      }

      if (entry.getTotalResource() != null) {
        builder.cpuRequested(viewsQueryHelper.getRoundedDoubleValue(entry.getTotalResource().getCpuUnits() / 1024))
            .memoryRequested(viewsQueryHelper.getRoundedDoubleValue(entry.getTotalResource().getMemoryMb() / 1024));
      }

      if (entry.getUsageStartTime() != null) {
        builder.createTime(entry.getUsageStartTime().toEpochMilli());
      }

      podDataBuilder.cost(costDataEntry.getCost());
      podDataBuilder.costTrend(costDataEntry.getCostTrend());
      podDataBuilder.id(costDataEntry.getId());
      podDataBuilder.name(costDataEntry.getName());
      podDataBuilder.isClusterPerspective(costDataEntry.isClusterPerspective());
      podDataBuilder.instanceDetails(builder.build());
      dataPoints.add(podDataBuilder.build());
    });
    return dataPoints;
  }

  private List<QLCEViewEntityStatsDataPoint> getDataForPV(
      Map<String, QLCEViewEntityStatsDataPoint> instanceIdToPVCostData, Map<String, InstanceData> pvToInstanceDataMap,
      List<String> pvInstanceIdsWithCluster) {
    List<QLCEViewEntityStatsDataPoint> dataPoints = new ArrayList<>();

    for (String instanceIdWithCluster : pvInstanceIdsWithCluster) {
      QLCEViewEntityStatsDataPoint costData = instanceIdToPVCostData.get(instanceIdWithCluster);
      // Since instanceData can be purged, check for null for each access
      InstanceData instanceData = pvToInstanceDataMap.getOrDefault(instanceIdWithCluster, DEFAULT_INSTANCE_DATA);
      QLCEViewEntityStatsDataPointBuilder pvDataBuilder = QLCEViewEntityStatsDataPoint.builder();
      StorageDetailsBuilder builder = StorageDetails.builder();

      builder.storageCost(costData.getClusterData().getStorageCost())
          .storageActualIdleCost(costData.getClusterData().getStorageActualIdleCost())
          .storageUnallocatedCost(costData.getClusterData().getStorageUnallocatedCost())
          .storageUtilizationValue(costData.getClusterData().getStorageUtilizationValue())
          .storageRequest(costData.getClusterData().getStorageRequest())
          .instanceId(instanceData.getInstanceId())
          .id(costData.getId())
          .instanceName(costData.getName())
          .claimName(costData.getClusterData().getWorkloadName())
          .clusterName(costData.getClusterData().getClusterName())
          .clusterId(costData.getClusterData().getClusterId())
          .storageClass(DEFAULT_STRING_VALUE)
          .volumeType(DEFAULT_STRING_VALUE)
          .cloudProvider(costData.getClusterData().getCloudProvider())
          .claimNamespace(costData.getClusterData().getNamespace());

      if (instanceData.getUsageStopTime() != null) {
        builder.deleteTime(instanceData.getUsageStopTime().toEpochMilli());
      }
      if (instanceData.getUsageStartTime() != null) {
        builder.createTime(instanceData.getUsageStartTime().toEpochMilli());
      }
      if (instanceData.getStorageResource() != null) {
        builder.capacity(
            viewsQueryHelper.getRoundedDoubleValue(instanceData.getStorageResource().getCapacity() / 1024D));
      }
      if (instanceData.getMetaData().get(CLAIM_NAME) != null) {
        builder.claimName(instanceData.getMetaData().get(CLAIM_NAME));
      }
      if (instanceData.getMetaData().get(CLAIM_NAMESPACE) != null) {
        builder.claimNamespace(instanceData.getMetaData().get(CLAIM_NAMESPACE));
      }
      if (instanceData.getMetaData().get("region") != null) {
        builder.region(instanceData.getMetaData().get("region"));
      }
      if (instanceData.getMetaData().get("type") != null) {
        builder.storageClass(instanceData.getMetaData().get("type"));
      }
      if (instanceData.getMetaData().get("pv_type") != null) {
        builder.volumeType(instanceData.getMetaData().get("pv_type").substring(8));
      }
      // The storage values returned from the DB is in MB
      builder.storageUtilizationValue(
          viewsQueryHelper.getRoundedDoubleValue(costData.getClusterData().getStorageUtilizationValue() / 1024D));
      builder.storageCost(
          viewsQueryHelper.getRoundedDoubleValue(costData.getClusterData().getStorageRequest() / 1024D));
      pvDataBuilder.cost(costData.getCost());
      pvDataBuilder.costTrend(costData.getCostTrend());
      pvDataBuilder.id(costData.getId());
      pvDataBuilder.name(costData.getName());
      pvDataBuilder.isClusterPerspective(costData.isClusterPerspective());
      pvDataBuilder.storageDetails(builder.build());
      dataPoints.add(pvDataBuilder.build());
    }
    return dataPoints;
  }
}
