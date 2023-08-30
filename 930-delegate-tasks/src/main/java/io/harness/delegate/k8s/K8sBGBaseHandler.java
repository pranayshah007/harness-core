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
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Blue;
import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.VersionUtils;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sBGReleaseHistoryCleanupDTO;
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
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
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

  public List<K8sPod> getExistingPods(
      long timeoutInMillis, KubernetesConfig kubernetesConfig, String releaseName, LogCallback logCallback) {
    String namespace = kubernetesConfig.getNamespace();
    try {
      logCallback.saveExecutionLog("\nFetching existing pod list.");
      return k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis);
    } catch (Exception e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
    }
    return emptyList();
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
  public List<K8sPod> getAllPodsNG(long timeoutInMillis, KubernetesConfig kubernetesConfig,
      KubernetesResource managedWorkload, String primaryColor, String stageColor, String releaseName,
      List<K8sPod> existingPodList) throws Exception {
    List<K8sPod> allPods = new ArrayList<>();
    String namespace = managedWorkload.getResourceId().getNamespace();
    final List<K8sPod> stagePods =
        k8sTaskHelperBase.getPodDetailsWithColor(kubernetesConfig, namespace, releaseName, stageColor, timeoutInMillis);
    final List<K8sPod> primaryPods = k8sTaskHelperBase.getPodDetailsWithColor(
        kubernetesConfig, namespace, releaseName, primaryColor, timeoutInMillis);
    Set<String> olderPodNames = existingPodList.stream().map(K8sPod::getName).collect(Collectors.toSet());
    stagePods.forEach(pod -> {
      if (!olderPodNames.contains(pod.getName())) {
        pod.setNewPod(true);
      }
    });
    allPods.addAll(stagePods);
    allPods.addAll(primaryPods);
    return allPods;
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

  public PrePruningInfo cleanupForBlueGreen(K8sDelegateTaskParams k8sDelegateTaskParams,
      IK8sReleaseHistory releaseHistory, LogCallback executionLogCallback, String primaryColor, String stageColor,
      Integer currentReleaseNumber, Kubectl client, KubernetesConfig kubernetesConfig, String releaseName,
      boolean useDeclarativeRollback) throws Exception {
    if (StringUtils.equals(primaryColor, stageColor)) {
      return PrePruningInfo.builder()
          .deletedResourcesInStage(emptyList())
          .releaseHistoryBeforeStageCleanUp(releaseHistory)
          .build();
    }

    IK8sReleaseHistory releaseHistoryBeforeStageCleanup = releaseHistory.cloneInternal();
    executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
    executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));

    executionLogCallback.saveExecutionLog("\nCleaning up non primary releases");

    List<IK8sRelease> nonPrimaryReleases = releaseHistory.getReleasesMatchingColor(stageColor, currentReleaseNumber);

    List<KubernetesResourceId> resourcesToDelete = new ArrayList<>();
    if (!useDeclarativeRollback) {
      // legacy implementation uses (VersionUtils::shouldVersion) to set `versioned` prop
      resourcesToDelete.addAll(nonPrimaryReleases.stream()
                                   .flatMap(release -> release.getResourceIds().stream())
                                   .filter(KubernetesResourceId::isVersioned)
                                   .collect(toList()));
    } else {
      // not using `versioned` prop since we're not versioning resources with new release history implementation anymore
      resourcesToDelete.addAll(nonPrimaryReleases.stream()
                                   .flatMap(release -> release.getResourcesWithSpecs().stream())
                                   .filter(VersionUtils::shouldVersion)
                                   .map(KubernetesResource::getResourceId)
                                   .collect(toList()));
    }

    if (isNotEmpty(resourcesToDelete)) {
      executionLogCallback.saveExecutionLog(String.format("Deleting the following old versioned resources: %s",
          resourcesToDelete.stream().map(KubernetesResourceId::namespaceKindNameRef).collect(Collectors.joining(","))));
      k8sTaskHelperBase.delete(client, k8sDelegateTaskParams, resourcesToDelete, executionLogCallback, false);
    }

    K8sBGReleaseHistoryCleanupDTO cleanupDTO = K8sBGReleaseHistoryCleanupDTO.builder()
                                                   .releasesToClean(nonPrimaryReleases)
                                                   .kubernetesConfig(kubernetesConfig)
                                                   .releaseName(releaseName)
                                                   .logCallback(executionLogCallback)
                                                   .releaseHistory(releaseHistory)
                                                   .currentReleaseNumber(currentReleaseNumber)
                                                   .color(stageColor)
                                                   .build();
    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    releaseHandler.cleanReleaseHistoryBG(cleanupDTO);

    return PrePruningInfo.builder()
        .releaseHistoryBeforeStageCleanUp(releaseHistoryBeforeStageCleanup)
        .deletedResourcesInStage(resourcesToDelete)
        .build();
  }

  public List<KubernetesResourceId> pruneForBg(K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback executionLogCallback, String primaryColor, String stageColor, PrePruningInfo prePruningInfo,
      IK8sRelease currentRelease, Kubectl client) {
    // Todo: Investigate when this case is possible
    try {
      if (StringUtils.equals(primaryColor, stageColor)) {
        executionLogCallback.saveExecutionLog(
            "Primary and secondary service are at same color, No pruning required.", INFO, SUCCESS);
        return emptyList();
      }
      IK8sReleaseHistory oldReleaseHistory = prePruningInfo.getReleaseHistoryBeforeStageCleanUp();

      if (isEmpty(oldReleaseHistory)) {
        executionLogCallback.saveExecutionLog(
            "No older releases are available in release history, No pruning Required.", INFO, SUCCESS);
        return emptyList();
      }

      executionLogCallback.saveExecutionLog("Primary Service is at color: " + encodeColor(primaryColor));
      executionLogCallback.saveExecutionLog("Stage Service is at color: " + encodeColor(stageColor));
      executionLogCallback.saveExecutionLog("Pruning up resources in non primary releases");

      Set<KubernetesResourceId> resourcesUsedInPrimaryReleases =
          getResourcesUsedInPrimaryReleases(oldReleaseHistory, currentRelease, primaryColor);
      Set<KubernetesResourceId> resourcesInCurrentRelease = new HashSet<>(currentRelease.getResourceIds());
      Set<KubernetesResourceId> alreadyDeletedResources = new HashSet<>(prePruningInfo.getDeletedResourcesInStage());
      List<KubernetesResourceId> resourcesPruned = new ArrayList<>();

      List<IK8sRelease> nonPrimaryReleases =
          oldReleaseHistory.getReleasesMatchingColor(stageColor, currentRelease.getReleaseNumber());

      for (IK8sRelease release : nonPrimaryReleases) {
        List<KubernetesResourceId> resourcesDeleted =
            pruneInternalForStageRelease(k8sDelegateTaskParams, executionLogCallback, client,
                resourcesUsedInPrimaryReleases, resourcesInCurrentRelease, alreadyDeletedResources, release);
        resourcesPruned.addAll(resourcesDeleted);
        alreadyDeletedResources.addAll(resourcesDeleted);
      }

      if (isEmpty(resourcesPruned)) {
        executionLogCallback.saveExecutionLog("No resources needed to be pruned");
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
      IK8sRelease release) throws Exception {
    if (isEmpty(release.getResourcesWithSpecs())) {
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
      Set<KubernetesResourceId> alreadyDeletedResources, IK8sRelease release) {
    Set<KubernetesResourceId> resourcesToRetain = new HashSet<>();
    resourcesToRetain.addAll(alreadyDeletedResources);
    resourcesToRetain.addAll(resourcesUsedInPrimaryReleases);
    resourcesToRetain.addAll(resourcesInCurrentRelase);

    List<KubernetesResourceId> resourcesToBePruned =
        release.getResourcesWithSpecs()
            .stream()
            .filter(resource -> !resourcesToRetain.contains(resource.getResourceId()))
            .filter(resource -> !resource.isSkipPruning())
            .map(KubernetesResource::getResourceId)
            .collect(toList());
    return isNotEmpty(resourcesToBePruned) ? k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourcesToBePruned)
                                           : emptyList();
  }

  private void logIfPruningRequiredForRelease(
      LogCallback executionLogCallback, IK8sRelease release, List<KubernetesResourceId> resourceasToBePruned) {
    if (isNotEmpty(resourceasToBePruned)) {
      executionLogCallback.saveExecutionLog(format("Pruning resources of release %s", release.getReleaseNumber()));
    } else {
      executionLogCallback.saveExecutionLog(
          format("No resource to be pruned for release %s", release.getReleaseNumber()));
    }
  }

  @NotNull
  private Set<KubernetesResourceId> getResourcesUsedInPrimaryReleases(
      IK8sReleaseHistory releaseHistory, IK8sRelease currentRelease, String primaryColor) {
    return releaseHistory.getReleasesMatchingColor(primaryColor, currentRelease.getReleaseNumber())
        .stream()
        .map(IK8sRelease::getResourceIds)
        .flatMap(Collection::stream)
        .collect(Collectors.toSet());
  }
}
