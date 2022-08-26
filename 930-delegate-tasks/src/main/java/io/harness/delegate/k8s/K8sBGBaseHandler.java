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
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.k8s.releasehistory.K8sReleaseHistoryService;
import io.harness.delegate.k8s.releasehistory.K8sReleaseService;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sBGBaseHandler {
  @Inject K8sTaskHelperBase k8sTaskHelperBase;
  @Inject KubernetesContainerService kubernetesContainerService;
  @Inject private K8sReleaseHistoryService releaseHistoryService;
  @Inject private K8sReleaseService releaseService;

  private String encodeColor(String color) {
    switch (color) {
      case HarnessLabelValues.colorBlue:
        return color(color, Blue, Bold);
      case HarnessLabelValues.colorGreen:
        return color(color, Green, Bold);
      default:
        unhandled(color);
    }
    return null;
  }

  public LogColor getLogColor(String color) {
    switch (color) {
      case HarnessLabelValues.colorBlue:
        return Blue;
      case HarnessLabelValues.colorGreen:
        return Green;
      default:
        unhandled(color);
    }
    return null;
  }

  public String getInverseColor(String color) {
    switch (color) {
      case HarnessLabelValues.colorBlue:
        return HarnessLabelValues.colorGreen;
      case HarnessLabelValues.colorGreen:
        return HarnessLabelValues.colorBlue;
      default:
        unhandled(color);
    }
    return null;
  }

  private String getColorFromService(V1Service service) {
    if (service.getSpec() == null || service.getSpec().getSelector() == null) {
      return null;
    }

    return service.getSpec().getSelector().get(HarnessLabels.color);
  }

  public void wrapUp(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, Kubectl client)
      throws Exception {
    executionLogCallback.saveExecutionLog("Wrapping up..\n");

    k8sTaskHelperBase.describe(client, k8sDelegateTaskParams, executionLogCallback);
  }

  public String getPrimaryColor(
      KubernetesResource primaryService, KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) {
    V1Service primaryServiceInCluster =
        kubernetesContainerService.getService(kubernetesConfig, primaryService.getResourceId().getName());
    if (primaryServiceInCluster == null) {
      executionLogCallback.saveExecutionLog(
          "Primary Service [" + primaryService.getResourceId().getName() + "] not found in cluster.");
    }

    return (primaryServiceInCluster != null) ? getColorFromService(primaryServiceInCluster)
                                             : HarnessLabelValues.colorDefault;
  }

  @VisibleForTesting
  public List<K8sPod> getAllPods(long timeoutInMillis, KubernetesConfig kubernetesConfig,
      KubernetesResource managedWorkload, String primaryColor, String stageColor, String releaseName) throws Exception {
    List<K8sPod> allPods = new ArrayList<>();
    String namespace = managedWorkload.getResourceId().getNamespace();
    final List<K8sPod> stagePods =
        k8sTaskHelperBase.getPodDetailsWithColor(kubernetesConfig, namespace, releaseName, stageColor, timeoutInMillis);
    final List<K8sPod> primaryPods = k8sTaskHelperBase.getPodDetailsWithColor(
        kubernetesConfig, namespace, releaseName, primaryColor, timeoutInMillis);
    stagePods.forEach(pod -> pod.setNewPod(true));
    allPods.addAll(stagePods);
    allPods.addAll(primaryPods);
    return allPods;
  }

  public PrePruningInfo cleanup(KubernetesConfig kubernetesConfig, K8sDelegateTaskParams k8sDelegateTaskParams,
      List<V1Secret> releaseList, LogCallback executionLogCallback, String primaryColor, String stageColor,
      Kubectl client, int currentReleaseNumber, String releaseName) throws Exception {
    if (StringUtils.equals(primaryColor, stageColor)) {
      return PrePruningInfo.builder()
          .deletedResourcesInStage(emptyList())
          .releaseHistoryBeforeCleanup(releaseList)
          .build();
    }

    executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
    executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));

    executionLogCallback.saveExecutionLog("\nCleaning up non primary releases");

    List<V1Secret> nonPrimaryReleases =
        releaseService.getReleasesMatchingColor(releaseList, stageColor, currentReleaseNumber);

    List<KubernetesResourceId> resourcesToDelete =
        nonPrimaryReleases.stream()
            .flatMap(release -> releaseService.getResourcesFromRelease(release).stream())
            .map(KubernetesResource::getResourceId)
            .filter(KubernetesResourceId::isVersioned)
            .collect(toList());

    Set<String> releaseNumbersToDelete = releaseService.getReleaseNumbers(nonPrimaryReleases);

    if (isNotEmpty(resourcesToDelete)) {
      k8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourcesToDelete, executionLogCallback, true);
    }

    if (isNotEmpty(releaseNumbersToDelete)) {
      releaseHistoryService.deleteReleases(kubernetesConfig, releaseName, releaseNumbersToDelete);
    }

    return PrePruningInfo.builder()
        .releaseHistoryBeforeCleanup(releaseList)
        .deletedResourcesInStage(resourcesToDelete)
        .build();
  }

  public List<KubernetesResourceId> pruneForBg(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, String primaryColor, String stageColor, PrePruningInfo prePruningInfo,
      V1Secret currentRelease, Kubectl client, int currentReleaseNumber) {
    try {
      if (StringUtils.equals(primaryColor, stageColor)) {
        executionLogCallback.saveExecutionLog("Primary and secondary service are at same color, No pruning required.");
        return emptyList();
      }
      List<V1Secret> oldReleaseHistory = prePruningInfo.getReleaseHistoryBeforeCleanup();

      if (oldReleaseHistory == null || isEmpty(oldReleaseHistory)) {
        executionLogCallback.saveExecutionLog(
            "No older releases are available in release history, No pruning Required.");
        return emptyList();
      }

      executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
      executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));
      executionLogCallback.saveExecutionLog("Pruning up resources in non primary releases");

      Set<KubernetesResourceId> resourcesUsedInPrimaryReleases =
          releaseService.getReleasesMatchingColor(oldReleaseHistory, primaryColor, currentReleaseNumber)
              .stream()
              .flatMap(release -> releaseService.getResourcesFromRelease(release).stream())
              .map(KubernetesResource::getResourceId)
              .collect(toSet());
      Set<KubernetesResourceId> resourcesInCurrentRelease = releaseService.getResourcesFromRelease(currentRelease)
                                                                .stream()
                                                                .map(KubernetesResource::getResourceId)
                                                                .collect(toSet());
      Set<KubernetesResourceId> alreadyDeletedResources = new HashSet<>(prePruningInfo.getDeletedResourcesInStage());
      List<KubernetesResourceId> resourcesPruned = new ArrayList<>();
      List<V1Secret> nonPrimaryReleases =
          releaseService.getReleasesMatchingColor(oldReleaseHistory, stageColor, currentReleaseNumber);

      for (V1Secret release : nonPrimaryReleases) {
        List<KubernetesResourceId> resourcesDeleted =
            pruneInternalForStageRelease(k8sDelegateTaskParams, executionLogCallback, client,
                resourcesUsedInPrimaryReleases, resourcesInCurrentRelease, alreadyDeletedResources, release);
        resourcesPruned.addAll(resourcesDeleted);
        // to handle the case where multiple stage releases have same undesired resources for current release
        alreadyDeletedResources.addAll(resourcesDeleted);
      }

      if (isEmpty(resourcesPruned)) {
        executionLogCallback.saveExecutionLog("No resources needed to be pruned", INFO, RUNNING);
      }
      executionLogCallback.saveExecutionLog("Pruning step completed", INFO, SUCCESS);
      return resourcesPruned;
    } catch (Exception ex) {
      executionLogCallback.saveExecutionLog("Failed to delete resources while pruning", WARN, RUNNING);
      executionLogCallback.saveExecutionLog(getMessage(ex), WARN, SUCCESS);
      return emptyList();
    }
  }

  private List<KubernetesResourceId> pruneInternalForStageRelease(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, Kubectl client, Set<KubernetesResourceId> resourcesUsedInPrimaryReleases,
      Set<KubernetesResourceId> resourcesInCurrentRelease, Set<KubernetesResourceId> alreadyDeletedResources,
      V1Secret release) throws Exception {
    List<KubernetesResource> resources = releaseService.getResourcesFromRelease(release);
    if (isEmpty(resources)) {
      executionLogCallback.saveExecutionLog(
          "Did not find any resources in the previous release, skipping pruning.", INFO, RUNNING);
      return emptyList();
    }
    List<KubernetesResourceId> resourcesToBePrunedInOrder = getResourcesToBePrunedInOrder(
        resourcesUsedInPrimaryReleases, resourcesInCurrentRelease, alreadyDeletedResources, resources);

    logIfPruningRequiredForRelease(executionLogCallback, release, resourcesToBePrunedInOrder);

    return k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourcesToBePrunedInOrder, executionLogCallback, false);
  }

  @NotNull
  private List<KubernetesResourceId> getResourcesToBePrunedInOrder(
      Set<KubernetesResourceId> resourcesUsedInPrimaryReleases, Set<KubernetesResourceId> resourcesInCurrentRelase,
      Set<KubernetesResourceId> alreadyDeletedResources, List<KubernetesResource> resources) {
    Set<KubernetesResourceId> resourcesToRetain = new HashSet<>();
    resourcesToRetain.addAll(alreadyDeletedResources);
    resourcesToRetain.addAll(resourcesUsedInPrimaryReleases);
    resourcesToRetain.addAll(resourcesInCurrentRelase);

    List<KubernetesResourceId> resourcesToBePruned =
        resources.stream()
            .filter(resource -> !resourcesToRetain.contains(resource.getResourceId()))
            .filter(resource -> !resource.isSkipPruning())
            .map(KubernetesResource::getResourceId)
            .collect(toList());
    return isNotEmpty(resourcesToBePruned) ? k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourcesToBePruned)
                                           : emptyList();
  }

  private void logIfPruningRequiredForRelease(
      LogCallback executionLogCallback, V1Secret release, List<KubernetesResourceId> resourceasToBePruned) {
    String releaseNumber = releaseService.getReleaseLabelValue(release, RELEASE_NUMBER_LABEL_KEY);
    if (isNotEmpty(resourceasToBePruned)) {
      executionLogCallback.saveExecutionLog(format("Pruning resources of release %s", releaseNumber));
    } else {
      executionLogCallback.saveExecutionLog(format("No resource to be pruned for release %s", releaseNumber));
    }
  }
}
