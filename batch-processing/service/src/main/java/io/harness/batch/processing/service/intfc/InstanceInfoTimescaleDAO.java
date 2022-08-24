/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.intfc;

import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.event.payloads.Lifecycle;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec;

import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;

public interface InstanceInfoTimescaleDAO {
  void insertIntoNodeInfo(@NotNull InstanceInfo instanceInfo);

  void insertIntoNodeInfo(@NotNull List<InstanceInfo> instanceInfoList);

  void insertIntoWorkloadInfo(@NotNull String accountId, @NotNull K8sWorkloadSpec workloadSpec);

  void insertIntoPodInfo(@NotNull List<InstanceInfo> instanceInfoList);

  void insertIntoPodInfo(@NotNull InstanceInfo instanceInfo);

  void updatePodStopEvent(@NotNull List<InstanceEvent> instanceEventList);

  void updatePodLifecycleEvent(@NotNull String accountId, @NotNull List<Lifecycle> lifecycleList);

  void updateNodeStopEvent(@NotNull List<InstanceEvent> instanceEventList);

  void updateNodeLifecycleEvent(@NotNull String accountId, @NotNull List<Lifecycle> lifecycleList);

  void stopInactiveNodesAtTime(@NotNull JobConstants jobConstants, @NotNull String clusterId,
      @NotNull Instant syncEventTimestamp, @NotNull List<String> activeNodeUidsList);

  void stopInactivePodsAtTime(@NotNull String accountId, @NotNull String clusterId, @NotNull Instant syncEventTimestamp,
      @NotNull List<String> activePodUidsList);
}
