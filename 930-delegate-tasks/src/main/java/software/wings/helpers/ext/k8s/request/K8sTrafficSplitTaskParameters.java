/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.k8s.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.IstioDestinationWeight;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class K8sTrafficSplitTaskParameters extends K8sTaskParameters {
  private String virtualServiceName;
  private List<IstioDestinationWeight> istioDestinationWeights;

  @Builder
  public K8sTrafficSplitTaskParameters(String accountId, String appId, String commandName, String activityId,
      K8sTaskType k8sTaskType, K8sClusterConfig k8sClusterConfig, String workflowExecutionId, String releaseName,
      Integer timeoutIntervalInMin, String virtualServiceName, List<IstioDestinationWeight> istioDestinationWeights,
      HelmVersion helmVersion, Set<String> delegateSelectors, boolean useLatestChartMuseumVersion,
      boolean useLatestKustomizeVersion, boolean useNewKubectlVersion) {
    super(accountId, appId, commandName, activityId, k8sClusterConfig, workflowExecutionId, releaseName,
        timeoutIntervalInMin, k8sTaskType, helmVersion, delegateSelectors, useLatestChartMuseumVersion,
        useLatestKustomizeVersion, useNewKubectlVersion);

    this.virtualServiceName = virtualServiceName;
    this.istioDestinationWeights = istioDestinationWeights;
  }
}
