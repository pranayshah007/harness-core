package io.harness.perpetualtask.instancesyncv2.cg;

import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import java.util.List;

// instance sync v2
public interface InstanceDetailsFetcher {
  List<InstanceInfo> fetchRunningInstanceDetails(
      PerpetualTaskId taskId, K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails);
}
