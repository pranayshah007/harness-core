/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AWS_ECS;
import static io.harness.ccm.cluster.entities.ClusterType.AZURE_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.GCP_KUBERNETES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.AzureKubernetesCluster;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.ccm.cluster.entities.GcpKubernetesCluster;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskClientParams;
import io.harness.perpetualtask.k8s.watch.K8WatchPerpetualTaskClientParams;
import io.harness.perpetualtask.k8s.watch.K8sWatchPerpetualTaskServiceClient;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CE)
public class CEPerpetualTaskManager {
  private ClusterRecordService clusterRecordService;
  private PerpetualTaskService perpetualTaskService;

  private static final String CLOUD_PROVIDER_ID = "cloudProviderId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String CLUSTER_NAME = "clusterName";
  private static final String REGION = "region";
  private static final String SETTING_ID = "settingId";

  @Inject
  public CEPerpetualTaskManager(ClusterRecordService clusterRecordService, PerpetualTaskService perpetualTaskService) {
    this.clusterRecordService = clusterRecordService;
    this.perpetualTaskService = perpetualTaskService;
  }

  public String createK8WatchTask(String accountId, K8WatchPerpetualTaskClientParams clientParams) {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(CLOUD_PROVIDER_ID, clientParams.getCloudProviderId());
    clientParamMap.put(CLUSTER_ID, clientParams.getClusterId());
    clientParamMap.put(CLUSTER_NAME, clientParams.getClusterName());

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(2))
                                         .setTimeout(Durations.fromMinutes(4))
                                         .build();
    return perpetualTaskService.createTask(PerpetualTaskType.K8S_WATCH, accountId, clientContext, schedule, false, "");
  }

  public String createEcsTask(String accountId, EcsPerpetualTaskClientParams clientParams) {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(REGION, clientParams.getRegion());
    clientParamMap.put(SETTING_ID, clientParams.getSettingId());
    clientParamMap.put(CLUSTER_NAME, clientParams.getClusterName());
    clientParamMap.put(CLUSTER_ID, clientParams.getClusterId());

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromSeconds(600))
                                         .setTimeout(Durations.fromMillis(180000))
                                         .build();

    return perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, accountId, clientContext, schedule, false, "");
  }

  public boolean createPerpetualTasks(Account account, String clusterType) {
    List<ClusterRecord> clusterRecords = clusterRecordService.list(account.getUuid(), clusterType);
    clusterRecords.stream()
        .filter(clusterRecord -> isEmpty(clusterRecord.getPerpetualTaskIds()))
        .forEach(this::createPerpetualTasks);
    return true;
  }

  public boolean deletePerpetualTasks(Account account, String clusterType) {
    List<ClusterRecord> clusterRecords = clusterRecordService.list(account.getUuid(), clusterType);
    clusterRecords.stream()
        .filter(clusterRecord -> isNotEmpty(clusterRecord.getPerpetualTaskIds()))
        .forEach(this::deletePerpetualTasks);
    return true;
  }

  public boolean createPerpetualTasks(SettingAttribute cloudProvider) {
    List<ClusterRecord> clusterRecords = getClusterRecords(cloudProvider);
    if (!isNull(clusterRecords)) {
      clusterRecords.forEach(this::createPerpetualTasks);
    }
    return true;
  }

  public boolean resetPerpetualTasks(SettingAttribute cloudProvider) {
    List<ClusterRecord> clusterRecords = getClusterRecords(cloudProvider);
    if (!isNull(clusterRecords)) {
      clusterRecords.forEach(this::resetPerpetualTasks);
    }
    return true;
  }

  public boolean deletePerpetualTasks(SettingAttribute cloudProvider) {
    List<ClusterRecord> clusterRecords = getClusterRecords(cloudProvider);
    if (!isNull(clusterRecords)) {
      for (ClusterRecord clusterRecord : clusterRecords) {
        deletePerpetualTasks(clusterRecord);
      }
    }
    return true;
  }

  // find all the related Clusters
  private List<ClusterRecord> getClusterRecords(SettingAttribute cloudProvider) {
    return clusterRecordService.list(cloudProvider.getAccountId(), null, cloudProvider.getUuid())
        .stream()
        .filter(clusterRecord -> {
          String clusterType = clusterRecord.getCluster().getClusterType();
          return !clusterType.equals(GCP_KUBERNETES) && !clusterType.equals(AZURE_KUBERNETES);
        })
        .collect(Collectors.toList());
  }

  public boolean createPerpetualTasks(ClusterRecord clusterRecord) {
    Cluster cluster = clusterRecord.getCluster();
    switch (cluster.getClusterType()) {
      case DIRECT_KUBERNETES:
        String watcherTaskId = createK8WatchTask(clusterRecord.getAccountId(),
            K8sWatchPerpetualTaskServiceClient.from(
                cluster, clusterRecord.getUuid(), ((DirectKubernetesCluster) cluster).getClusterName()));
        clusterRecordService.attachPerpetualTaskId(clusterRecord, watcherTaskId);
        break;
      case AWS_ECS:
        EcsCluster ecsCluster = (EcsCluster) cluster;
        if (null != ecsCluster.getRegion() && null != ecsCluster.getClusterName()) {
          EcsPerpetualTaskClientParams ecsPerpetualTaskClientParams =
              new EcsPerpetualTaskClientParams(ecsCluster.getRegion(), ecsCluster.getCloudProviderId(),
                  ecsCluster.getClusterName(), clusterRecord.getUuid());
          String ecsTaskId = createEcsTask(clusterRecord.getAccountId(), ecsPerpetualTaskClientParams);
          clusterRecordService.attachPerpetualTaskId(clusterRecord, ecsTaskId);
        } else {
          log.info("Not creating perpetual task for cluster {}", clusterRecord.getUuid());
        }
        break;
      case GCP_KUBERNETES:
        GcpKubernetesCluster gcpKubernetesCluster = (GcpKubernetesCluster) cluster;
        String gcpK8STaskId = createK8WatchTask(clusterRecord.getAccountId(),
            K8sWatchPerpetualTaskServiceClient.from(
                cluster, clusterRecord.getUuid(), gcpKubernetesCluster.getClusterName()));
        clusterRecordService.attachPerpetualTaskId(clusterRecord, gcpK8STaskId);
        break;
      case AZURE_KUBERNETES:
        AzureKubernetesCluster azureKubernetesCluster = (AzureKubernetesCluster) cluster;
        String azureK8STaskId = createK8WatchTask(clusterRecord.getAccountId(),
            K8sWatchPerpetualTaskServiceClient.from(
                cluster, clusterRecord.getUuid(), azureKubernetesCluster.getClusterName()));
        clusterRecordService.attachPerpetualTaskId(clusterRecord, azureK8STaskId);
        break;
      default:
        break;
    }
    return true;
  }

  public void resetPerpetualTasks(ClusterRecord clusterRecord) {
    // find all the existing perpetual Tasks for these clusters
    List<String> taskIds = Arrays.asList(clusterRecord.getPerpetualTaskIds());
    // reset all the existing perpetual tasks
    taskIds.forEach(taskId -> perpetualTaskService.resetTask(clusterRecord.getAccountId(), taskId, null));
  }

  // delete all of the perpetual tasks associated with the Cluster
  public boolean deletePerpetualTasks(ClusterRecord clusterRecord) {
    List<String> taskIds =
        Arrays.asList(Optional.ofNullable(clusterRecord.getPerpetualTaskIds()).orElse(new String[0]));
    for (String taskId : taskIds) {
      perpetualTaskService.deleteTask(clusterRecord.getAccountId(), taskId);
      clusterRecordService.removePerpetualTaskId(clusterRecord, taskId);
    }
    return true;
  }
}
