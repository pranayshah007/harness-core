/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.Kind;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sWorkloadWatcherFactory {
  @Inject private DeploymentApiWatcher deploymentApiWatcher;
  @Inject private StatefulSetApiWatcher statefulSetApiWatcher;
  @Inject private DaemonSetApiWatcher daemonSetApiWatcher;
  @Inject private JobApiWatcher jobApiWatcher;
  @Inject private DeploymentConfigCliWatcher deploymentConfigCliWatcher;
  @Inject private JobCliWatcher jobCliWatcher;
  @Inject private K8sCliWatcher k8sCliWatcher;

  public WorkloadWatcher getWorkloadWatcher(String kind, boolean isApiEnabled) {
    Kind workloadKind = Kind.valueOf(kind);
    if (isApiEnabled) {
      EnumMap<Kind, WorkloadWatcher> apiWorkloadWatcherMap = getApiWorkloadWatcherMap();
      return apiWorkloadWatcherMap.get(workloadKind);
    }
    EnumMap<Kind, WorkloadWatcher> cliWorkloadWatcherMap = getCliWorkloadWatcherMap();
    return cliWorkloadWatcherMap.getOrDefault(workloadKind, k8sCliWatcher);
  }

  private EnumMap<Kind, WorkloadWatcher> getApiWorkloadWatcherMap() {
    return new EnumMap<>(
        Map.of(Kind.Deployment, deploymentApiWatcher, Kind.StatefulSet, statefulSetApiWatcher, Kind.DaemonSet,
            daemonSetApiWatcher, Kind.Job, jobApiWatcher, Kind.DeploymentConfig, deploymentConfigCliWatcher));
  }

  private EnumMap<Kind, WorkloadWatcher> getCliWorkloadWatcherMap() {
    return new EnumMap<>(Map.of(Kind.Job, jobCliWatcher, Kind.DeploymentConfig, deploymentConfigCliWatcher));
  }
}
