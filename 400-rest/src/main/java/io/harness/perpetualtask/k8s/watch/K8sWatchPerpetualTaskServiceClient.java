/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.delegate.task.k8s.K8sTaskType.APPLY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.delegate.beans.TaskData;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.TaskType;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class K8sWatchPerpetualTaskServiceClient implements PerpetualTaskServiceClient {
  @Inject private K8sClusterConfigFactory k8sClusterConfigFactory;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ClusterRecordService clusterRecordService;

  private static final String CLOUD_PROVIDER_ID = "cloudProviderId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String CLUSTER_NAME = "clusterName";

  @Override
  public K8sWatchTaskParams getTaskParams(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String cloudProviderId = clientParams.get(CLOUD_PROVIDER_ID);
    String clusterId = clientParams.get(CLUSTER_ID);
    String clusterName = clientParams.get(CLUSTER_NAME);
    ClusterRecord clusterRecord = clusterRecordService.get(clusterId);
    if (null != clusterRecord && null != clusterRecord.getCluster().getClusterName()) {
      clusterName = clusterRecord.getCluster().getClusterName();
    }
    K8sClusterConfig config = k8sClusterConfigFactory.getK8sClusterConfig(clusterId);
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(config));

    // TODO(Tang): throw exception upon validation failure

    return K8sWatchTaskParams.newBuilder()
        .setCloudProviderId(cloudProviderId)
        .setK8SClusterConfig(bytes)
        .setClusterId(clusterId)
        .setClusterName(clusterName)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    K8sWatchTaskParams params = getTaskParams(clientContext);

    K8sClusterConfig k8sClusterConfig =
        (K8sClusterConfig) kryoSerializer.asObject(params.getK8SClusterConfig().toByteArray());

    K8sTaskParameters k8sTaskParameters =
        new K8sTaskParameters("", "", "", "", k8sClusterConfig, "", "", 0, APPLY, null, null, false, false, true);

    return DelegateTask.builder()
        .accountId(accountId)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.K8S_WATCH_TASK.name())
                  .parameters(new Object[] {k8sTaskParameters})
                  .timeout(TimeUnit.MINUTES.toMillis(1))
                  .build())
        .build();
  }

  public static K8WatchPerpetualTaskClientParams from(Cluster cluster, String clusterId, String clusterName) {
    return new K8WatchPerpetualTaskClientParams(cluster.getCloudProviderId(), clusterId, clusterName);
  }
}
