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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
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
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Service;
import java.util.ArrayList;
import java.util.Collection;
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

  public PrePruningInfo cleanupForBlueGreen(K8sDelegateTaskParams k8sDelegateTaskParams, ReleaseHistory releaseHistory,
      LogCallback executionLogCallback, String primaryColor, String stageColor, Release currentRelease, Kubectl client)
      throws Exception {
    if (StringUtils.equals(primaryColor, stageColor)) {
      return PrePruningInfo.builder()
          .deletedResourcesInStage(emptyList())
          .releaseHistoryBeforeStageCleanUp(releaseHistory)
          .build();
    }

    executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
    executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));

    executionLogCallback.saveExecutionLog("\nCleaning up non primary releases");

    List<KubernetesResourceId> resourceDeleted = new ArrayList<>();
    for (int releaseIndex = releaseHistory.getReleases().size() - 1; releaseIndex >= 0; releaseIndex--) {
      Release release = releaseHistory.getReleases().get(releaseIndex);
      if (release.getNumber() != currentRelease.getNumber() && release.getManagedWorkload() != null
          && release.getManagedWorkload().getName().endsWith(stageColor)) {
        for (int resourceIndex = release.getResources().size() - 1; resourceIndex >= 0; resourceIndex--) {
          KubernetesResourceId resourceId = release.getResources().get(resourceIndex);
          if (resourceId.isVersioned()) {
            k8sTaskHelperBase.delete(client, k8sDelegateTaskParams, asList(resourceId), executionLogCallback, true);
            resourceDeleted.add(resourceId);
          }
        }
      }
    }

    PrePruningInfo prePruningInfo = PrePruningInfo.builder()
                                        .releaseHistoryBeforeStageCleanUp(releaseHistory.cloneInternal())
                                        .deletedResourcesInStage(resourceDeleted)
                                        .build();

    releaseHistory.getReleases().removeIf(release
        -> release.getNumber() != currentRelease.getNumber() && release.getManagedWorkload() != null
            && release.getManagedWorkload().getName().endsWith(stageColor));

    return prePruningInfo;
  }

  public List<KubernetesResourceId> pruneForBg(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, String primaryColor, String stageColor, PrePruningInfo prePruningInfo,
      Release currentRelease, Kubectl client) {
    // Todo: Investigate when this case is possible
    try {
      if (StringUtils.equals(primaryColor, stageColor)) {
        executionLogCallback.saveExecutionLog("Primary and secondary service are at same color, No pruning required.");
        return emptyList();
      }
      ReleaseHistory oldReleaseHistory = prePruningInfo.getReleaseHistoryBeforeStageCleanUp();

      if (oldReleaseHistory == null || isEmpty(oldReleaseHistory.getReleases())) {
        executionLogCallback.saveExecutionLog(
            "No older releases are available in release history, No pruning Required.");
        return emptyList();
      }

      executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
      executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));
      executionLogCallback.saveExecutionLog("Pruning up resources in non primary releases");

      Set<KubernetesResourceId> resourcesUsedInPrimaryReleases =
          getResourcesUsedInPrimaryReleases(oldReleaseHistory, currentRelease, primaryColor);
      Set<KubernetesResourceId> resourcesInCurrentRelease = new HashSet<>(currentRelease.getResources());
      Set<KubernetesResourceId> alreadyDeletedResources = new HashSet<>(prePruningInfo.getDeletedResourcesInStage());
      List<KubernetesResourceId> resourcesPruned = new ArrayList<>();

      for (int releaseIndex = oldReleaseHistory.getReleases().size() - 1; releaseIndex >= 0; releaseIndex--) {
        // deleting resources per release seems safer and more accountable
        Release release = oldReleaseHistory.getReleases().get(releaseIndex);
        if (isReleaseAssociatedWithStage(stageColor, currentRelease, release)) {
          List<KubernetesResourceId> resourcesDeleted =
              pruneInternalForStageRelease(k8sDelegateTaskParams, executionLogCallback, client,
                  resourcesUsedInPrimaryReleases, resourcesInCurrentRelease, alreadyDeletedResources, release);
          resourcesPruned.addAll(resourcesDeleted);
          // to handle the case where multiple stage releases have same undesired resources for current release
          alreadyDeletedResources.addAll(resourcesDeleted);
        }
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
      Release release) throws Exception {
    if (isEmpty(release.getResourcesWithSpec())) {
      executionLogCallback.saveExecutionLog(
          "Previous successful deployment executed with pruning disabled, Pruning can't be done", INFO, RUNNING);
      return emptyList();
    }
    List<KubernetesResourceId> resourcesToBePrunedInOrder = getResourcesToBePrunedInOrder(
        resourcesUsedInPrimaryReleases, resourcesInCurrentRelease, alreadyDeletedResources, release);

    logIfPruningRequiredForRelease(executionLogCallback, release, resourcesToBePrunedInOrder);

    return k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourcesToBePrunedInOrder, executionLogCallback, false);
  }

  @NotNull
  private List<KubernetesResourceId> getResourcesToBePrunedInOrder(
      Set<KubernetesResourceId> resourcesUsedInPrimaryReleases, Set<KubernetesResourceId> resourcesInCurrentRelase,
      Set<KubernetesResourceId> alreadyDeletedResources, Release release) {
    Set<KubernetesResourceId> resourcesToRetain = new HashSet<>();
    resourcesToRetain.addAll(alreadyDeletedResources);
    resourcesToRetain.addAll(resourcesUsedInPrimaryReleases);
    resourcesToRetain.addAll(resourcesInCurrentRelase);

    List<KubernetesResourceId> resourcesToBePruned =
        release.getResourcesWithSpec()
            .stream()
            .filter(resource -> !resourcesToRetain.contains(resource.getResourceId()))
            .filter(resource -> !resource.isSkipPruning())
            .map(KubernetesResource::getResourceId)
            .collect(toList());
    return isNotEmpty(resourcesToBePruned) ? k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourcesToBePruned)
                                           : emptyList();
  }

  private void logIfPruningRequiredForRelease(
      LogCallback executionLogCallback, Release release, List<KubernetesResourceId> resourceasToBePruned) {
    if (isNotEmpty(resourceasToBePruned)) {
      executionLogCallback.saveExecutionLog(format("Pruning resources of release %s", release.getNumber()));
    } else {
      executionLogCallback.saveExecutionLog(format("No resource to be pruned for release %s", release.getNumber()));
    }
  }

  @NotNull
  private Set<KubernetesResourceId> getResourcesUsedInPrimaryReleases(
      ReleaseHistory releaseHistory, Release currentRelease, String primaryColor) {
    return releaseHistory.getReleases()
        .stream()
        .filter(release -> isReleaseAssociatedWithPrimary(primaryColor, currentRelease, release))
        .map(Release::getResources)
        .flatMap(Collection::stream)
        .collect(toSet());
  }

  private boolean isReleaseAssociatedWithStage(String stageColor, Release currentRelease, Release release) {
    return release.getNumber() != currentRelease.getNumber() && release.getManagedWorkload() != null
        && release.getManagedWorkload().getName().endsWith(stageColor);
  }

  private boolean isReleaseAssociatedWithPrimary(String primaryColor, Release currentRelease, Release release) {
    return release.getNumber() != currentRelease.getNumber() && release.getManagedWorkload() != null
        && release.getManagedWorkload().getName().endsWith(primaryColor);
  }
}
