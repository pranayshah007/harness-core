/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.validation.Validator.notNullCheck;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidRequestException;
import io.harness.helm.HelmConstants;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.openapi.models.V1Pod;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgK8sInstancesDetailsFetcher implements InstanceDetailsFetcher {
  private final KubernetesContainerService kubernetesContainerService;
  private final ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  private final K8sTaskHelperBase k8sTaskHelperBase;

  @Override
  public List<InstanceInfo> fetchRunningInstanceDetails(
      PerpetualTaskId taskId, K8sClusterConfig config, DirectK8sInstanceSyncTaskDetails k8sInstanceSyncTaskDetails) {
    KubernetesConfig kubernetesConfig = containerDeploymentDelegateHelper.getKubernetesConfig(config, true);
    notNullCheck("KubernetesConfig", kubernetesConfig);
    try {
      final List<V1Pod> pods =
          kubernetesContainerService.getRunningPodsWithLabels(kubernetesConfig, config.getNamespace(),
              ImmutableMap.of(HelmConstants.HELM_RELEASE_LABEL, k8sInstanceSyncTaskDetails.getReleaseName()));
      return pods.stream()
          .map(pod
              -> KubernetesContainerInfo.builder()
                     .clusterName(config.getClusterName())
                     .podName(pod.getMetadata().getName())
                     .ip(pod.getStatus().getPodIP())
                     .namespace(k8sInstanceSyncTaskDetails.getNamespace())
                     .releaseName(k8sInstanceSyncTaskDetails.getReleaseName())
                     .build())
          .collect(toList());
    } catch (Exception exception) {
      throw new InvalidRequestException(String.format("Failed to fetch containers info for namespace: [%s] ",
                                            k8sInstanceSyncTaskDetails.getNamespace()),
          exception);
    }
  }
}
