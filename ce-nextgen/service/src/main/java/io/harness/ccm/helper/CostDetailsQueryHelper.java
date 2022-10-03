/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.helper;

import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;

import io.harness.ccm.commons.entities.CCMEcsEntity;
import io.harness.ccm.commons.entities.CCMK8sEntity;
import io.harness.ccm.commons.entities.ClusterCostDetails;
import io.harness.ccm.commons.entities.K8sLabel;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveEntityStatsData;
import io.harness.ccm.views.entities.ClusterData;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.ViewsQueryHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CostDetailsQueryHelper {
  @Inject ViewsQueryHelper queryHelper;

  private static final String DEFAULT_SELECTED_LABEL = "-";
  public Set<String> getWorkloadsFromCostDetailsResponse(
      PerspectiveEntityStatsData perspectiveGridData, List<QLCEViewGroupBy> groupBy) {
    Set<String> workloads = new HashSet<>();
    if (queryHelper.isGroupByFieldIdPresent(groupBy, WORKLOAD_NAME_FIELD_ID) && perspectiveGridData != null) {
      perspectiveGridData.getData().forEach(dataPoint -> workloads.add(dataPoint.getName()));
    }
    return workloads;
  }

  public List<ClusterCostDetails> getClusterCostDataFromGridResponse(PerspectiveEntityStatsData perspectiveGridData) {
    List<ClusterCostDetails> clusterCostDetails = new ArrayList<>();
    if (perspectiveGridData != null) {
      List<QLCEViewEntityStatsDataPoint> data = perspectiveGridData.getData();
      List<ClusterData> clusterData = new ArrayList<>();
      if (data != null) {
        clusterData = data.stream().map(QLCEViewEntityStatsDataPoint::getClusterData).collect(Collectors.toList());
      }
      clusterData.forEach(dataPoint
          -> clusterCostDetails.add(ClusterCostDetails.builder()
                                        .totalCost(dataPoint.getTotalCost())
                                        .idleCost(dataPoint.getIdleCost())
                                        .unallocatedCost(dataPoint.getUnallocatedCost())
                                        .cluster(dataPoint.getClusterName())
                                        .clusterId(dataPoint.getClusterId())
                                        .clusterType(dataPoint.getClusterType())
                                        .k8s(CCMK8sEntity.builder()
                                                 .namespace(dataPoint.getNamespace())
                                                 .workload(dataPoint.getWorkloadName())
                                                 .build())
                                        .ecs(CCMEcsEntity.builder()
                                                 .launchType(dataPoint.getLaunchType())
                                                 .service(dataPoint.getCloudServiceName())
                                                 .taskId(dataPoint.getTaskId())
                                                 .build())
                                        .build()));
    }
    return clusterCostDetails;
  }

  public List<ClusterCostDetails> updateGridResponseWithSelectedLabels(List<ClusterCostDetails> clusterCostDetails,
      List<String> selectedLabels, Map<String, Map<String, String>> workloadLabels) {
    List<ClusterCostDetails> updatedClusterCostDetails = new ArrayList<>();
    for (ClusterCostDetails dataPoint : clusterCostDetails) {
      Map<String, String> valuesForSelectedLabels = new HashMap<>();
      Map<String, String> labelsForWorkload = workloadLabels.get(dataPoint.getK8s().getWorkload());
      selectedLabels.forEach(label -> valuesForSelectedLabels.put(label, DEFAULT_SELECTED_LABEL));
      if (labelsForWorkload != null) {
        selectedLabels.forEach(
            label -> valuesForSelectedLabels.put(label, labelsForWorkload.getOrDefault(label, DEFAULT_SELECTED_LABEL)));
      }
      updatedClusterCostDetails.add(addSelectedLabelsToClusterData(dataPoint, valuesForSelectedLabels));
    }
    return updatedClusterCostDetails;
  }

  private ClusterCostDetails addSelectedLabelsToClusterData(
      ClusterCostDetails clusterCostDetails, Map<String, String> valuesForSelectedLabels) {
    if (clusterCostDetails == null) {
      return null;
    }
    List<K8sLabel> selectedLabels = new ArrayList<>();
    valuesForSelectedLabels.forEach(
        (labelKey, labelValue) -> selectedLabels.add(K8sLabel.builder().name(labelKey).value(labelValue).build()));
    String workload = clusterCostDetails.getK8s().getWorkload();

    return ClusterCostDetails.builder()
        .totalCost(clusterCostDetails.getTotalCost())
        .idleCost(clusterCostDetails.getIdleCost())
        .unallocatedCost(clusterCostDetails.getUnallocatedCost())
        .cluster(clusterCostDetails.getCluster())
        .clusterId(clusterCostDetails.getClusterId())
        .clusterType(clusterCostDetails.getClusterType())
        .k8s(CCMK8sEntity.builder()
                 .namespace(clusterCostDetails.getK8s().getNamespace())
                 .workload(workload)
                 .selectedLabels(selectedLabels)
                 .build())
        .ecs(clusterCostDetails.getEcs())
        .build();
  }
}
