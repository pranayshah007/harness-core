/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.Release.KubernetesResourceIdRevision;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sRollingBaseHandler {
  public static final Map.Entry<String, String> HARNESS_TRACK_STABLE_SELECTOR =
      Maps.immutableEntry(HarnessLabels.track, HarnessLabelValues.trackStable);
  @Inject K8sTaskHelperBase k8sTaskHelperBase;

  @VisibleForTesting
  public void updateManagedWorkloadsRevision(
      K8sDelegateTaskParams k8sDelegateTaskParams, Release release, Kubectl client) throws Exception {
    List<KubernetesResourceIdRevision> workloads = release.getManagedWorkloads();

    for (KubernetesResourceIdRevision kubernetesResourceIdRevision : workloads) {
      String latestRevision = k8sTaskHelperBase.getLatestRevision(
          client, kubernetesResourceIdRevision.getWorkload(), k8sDelegateTaskParams);
      kubernetesResourceIdRevision.setRevision(latestRevision);
    }
  }

  public void setManagedWorkloadsInRelease(K8sDelegateTaskParams k8sDelegateTaskParams,
      List<KubernetesResource> managedWorkloads, Release release, Kubectl client) throws Exception {
    List<KubernetesResourceIdRevision> kubernetesResourceIdRevisions = new ArrayList<>();

    for (KubernetesResource kubernetesResource : managedWorkloads) {
      String latestRevision =
          k8sTaskHelperBase.getLatestRevision(client, kubernetesResource.getResourceId(), k8sDelegateTaskParams);

      kubernetesResourceIdRevisions.add(KubernetesResourceIdRevision.builder()
                                            .workload(kubernetesResource.getResourceId())
                                            .revision(latestRevision)
                                            .build());
    }

    release.setManagedWorkloads(kubernetesResourceIdRevisions);
  }

  public void setCustomWorkloadsInRelease(List<KubernetesResource> customWorkloads, Release release) {
    if (isNotEmpty(customWorkloads)) {
      release.setCustomWorkloads(customWorkloads);
    }
  }

  public void addLabelsInDeploymentSelectorForCanary(
      boolean inCanaryWorkflow, List<KubernetesResource> workloads, boolean skipAddingTrackSelectorToDeployment) {
    if (!inCanaryWorkflow && !skipAddingTrackSelectorToDeployment) {
      return;
    }
    addDeploymentSelector(workloads);
  }

  private void addDeploymentSelector(List<KubernetesResource> managedWorkloads) {
    if (isEmpty(managedWorkloads)) {
      return;
    }
    for (KubernetesResource kubernetesResource : managedWorkloads) {
      if (ImmutableSet.of(Kind.Deployment.name(), Kind.DeploymentConfig.name())
              .contains(kubernetesResource.getResourceId().getKind())) {
        kubernetesResource.addLabelsInDeploymentSelector(
            ImmutableMap.of(HarnessLabels.track, HarnessLabelValues.trackStable));
      }
    }
  }

  public void addLabelsInManagedWorkloadPodSpec(boolean inCanaryWorkflow, boolean skipAddingTrackSelectorToDeployment,
      List<KubernetesResource> managedWorkloads, List<KubernetesResource> deploymentContainingTrackStableSelector,
      String releaseName) {
    addReleaseNameInPodSpec(managedWorkloads, releaseName);

    if (inCanaryWorkflow || skipAddingTrackSelectorToDeployment) {
      List<KubernetesResource> workloadsToAddTrackLabel =
          inCanaryWorkflow ? managedWorkloads : deploymentContainingTrackStableSelector;
      addTrackLabelInPodSpec(workloadsToAddTrackLabel);
    }
  }

  private void addTrackLabelInPodSpec(List<KubernetesResource> workloads) {
    Map<String, String> releaseNameAndTrackPodLabels =
        ImmutableMap.of(HarnessLabels.track, HarnessLabelValues.trackStable);
    for (KubernetesResource kubernetesResource : workloads) {
      kubernetesResource.addLabelsInPodSpec(releaseNameAndTrackPodLabels);
    }
  }

  private void addReleaseNameInPodSpec(List<KubernetesResource> workloads, String releaseName) {
    Map<String, String> releaseNamePodLabels = ImmutableMap.of(HarnessLabels.releaseName, releaseName);
    for (KubernetesResource kubernetesResource : workloads) {
      kubernetesResource.addLabelsInPodSpec(releaseNamePodLabels);
    }
  }

  public void updateDestinationRuleWithSubsets(LogCallback executionLogCallback, List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig) throws IOException {
    k8sTaskHelperBase.updateDestinationRuleManifestFilesWithSubsets(resources,
        asList(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable), kubernetesConfig, executionLogCallback);
  }

  public void updateVirtualServiceWithRoutes(LogCallback executionLogCallback, List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig) throws IOException {
    k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(
        resources, kubernetesConfig, executionLogCallback);
  }

  public void wrapUp(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, Kubectl client)
      throws Exception {
    executionLogCallback.saveExecutionLog("Wrapping up..\n");

    k8sTaskHelperBase.describe(client, k8sDelegateTaskParams, executionLogCallback);
  }

  public List<K8sPod> getPods(long timeoutInMillis, List<KubernetesResource> managedWorkloads,
      KubernetesConfig kubernetesConfig, String releaseName) throws Exception {
    List<K8sPod> k8sPods = new ArrayList<>();

    if (isEmpty(managedWorkloads)) {
      return k8sPods;
    }

    final List<String> namespaces = managedWorkloads.stream()
                                        .map(KubernetesResource::getResourceId)
                                        .map(KubernetesResourceId::getNamespace)
                                        .distinct()
                                        .collect(Collectors.toList());
    for (String namespace : namespaces) {
      List<K8sPod> podDetails =
          k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis);

      if (isNotEmpty(podDetails)) {
        k8sPods.addAll(podDetails);
      }
    }

    return k8sPods;
  }

  public List<K8sPod> getExistingPods(long timeoutInMillis, List<KubernetesResource> managedWorkloads,
      KubernetesConfig kubernetesConfig, String releaseName, LogCallback logCallback) throws Exception {
    List<K8sPod> existingPodList;
    try {
      logCallback.saveExecutionLog("\nFetching existing pod list.");
      existingPodList = getPods(timeoutInMillis, managedWorkloads, kubernetesConfig, releaseName);
    } catch (Exception e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      throw e;
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    return existingPodList;
  }

  public void addLabelsInDeploymentSelectorForCanary(boolean inCanaryWorkflow,
      boolean skipAddingTrackSelectorToDeployment, List<KubernetesResource> managedWorkloads,
      List<KubernetesResource> deploymentContainingTrackStableSelector) {
    if (skipAddingTrackSelectorToDeployment) {
      addLabelsInDeploymentSelectorForCanary(inCanaryWorkflow, deploymentContainingTrackStableSelector, true);
    } else {
      addLabelsInDeploymentSelectorForCanary(inCanaryWorkflow, managedWorkloads, false);
    }
  }
}
