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
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.k8s.manifest.ManifestHelper.getCustomResourceDefinitionWorkloads;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloads;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.time.Duration.ofMinutes;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.delegate.k8s.releasehistory.K8sReleaseHistoryService;
import io.harness.delegate.k8s.releasehistory.K8sReleaseService;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutUndoCommand;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.Release.KubernetesResourceIdRevision;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sRollingRollbackBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sReleaseHistoryService releaseHistoryService;
  @Inject private K8sReleaseService releaseService;

  public void init(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName, LogCallback logCallback)
      throws IOException {
    List<V1Secret> releases =
        releaseHistoryService.getReleaseHistory(rollbackHandlerConfig.getKubernetesConfig(), releaseName);
    if (isEmpty(releases)) {
      rollbackHandlerConfig.setNoopRollBack(true);
      logCallback.saveExecutionLog("\nNo release history found for release " + releaseName);
    } else {
      rollbackHandlerConfig.setReleaseHistory(releases);

      V1Secret latestRelease = releaseService.getLatestRelease(releases);
      rollbackHandlerConfig.setRelease(latestRelease);
      List<KubernetesResource> resourcesInRelease = releaseService.getResourcesFromRelease(latestRelease);
      List<KubernetesResource> managedWorkloads = getWorkloads(resourcesInRelease);
      if (isNotEmpty(managedWorkloads)) {
        logCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
            + k8sTaskHelperBase.getResourcesInTableFormat(managedWorkloads));
      }
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  public void steadyStateCheck(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer timeoutInMin, LogCallback logCallback) throws Exception {
    V1Secret currentRelease = rollbackHandlerConfig.getRelease();
    Kubectl client = rollbackHandlerConfig.getClient();
    KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();

    List<KubernetesResource> previousResources = rollbackHandlerConfig.getPreviousResources();
    List<KubernetesResource> customResources = getCustomResourceDefinitionWorkloads(previousResources);
    List<KubernetesResourceId> managedWorkloads =
        getWorkloads(previousResources).stream().map(KubernetesResource::getResourceId).collect(toList());

    if (isEmpty(managedWorkloads) && isEmpty(customResources)) {
      logCallback.saveExecutionLog("Skipping Status Check since there is no previous eligible Managed Workload.", INFO);
    } else {
      long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(timeoutInMin);
      k8sTaskHelperBase.doStatusCheckForAllResources(
          client, managedWorkloads, k8sDelegateTaskParams, kubernetesConfig.getNamespace(), logCallback, false);

      if (isNotEmpty(customResources)) {
        k8sTaskHelperBase.checkSteadyStateCondition(customResources);
        k8sTaskHelperBase.doStatusCheckForAllCustomResources(
            client, customResources, k8sDelegateTaskParams, logCallback, false, steadyStateTimeoutInMillis);
      }
      releaseService.updateReleaseStatus(currentRelease, Release.Status.Failed.name());
    }
  }

  public void postProcess(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName) throws Exception {
    boolean isNoopRollBack = rollbackHandlerConfig.isNoopRollBack();
    KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();
    if (!isNoopRollBack) {
      releaseHistoryService.updateStatusAndSaveRelease(
          rollbackHandlerConfig.getRelease(), Release.Status.Succeeded.name(), kubernetesConfig);
    }
  }

  // parameter resourcesRecreated must be empty if FF PRUNE_KUBERNETES_RESOURCES is disabled
  public boolean rollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer releaseNumber, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled) throws Exception {
    V1Secret release = rollbackHandlerConfig.getRelease();
    List<V1Secret> releases = rollbackHandlerConfig.getReleaseHistory();

    if (release == null) {
      logCallback.saveExecutionLog("No previous release found. Skipping rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    // TODO - handle this case better
    if (releaseNumber == null) { // RollingDeploy was aborted
      String releaseStatus = releaseService.getReleaseLabelValue(release, RELEASE_STATUS_LABEL_KEY);
      if (Release.Status.Succeeded.name().equals(releaseStatus)) {
        logCallback.saveExecutionLog("No failed release found. Skipping rollback.");
        logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      }
    }

    V1Secret previousSuccessfulRelease = releaseService.getLastSuccessfulRelease(releases, releaseNumber);
    if (previousSuccessfulRelease == null) {
      logCallback.saveExecutionLog("No previous eligible release found. Can't rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    logCallback.saveExecutionLog(String.format("Previous eligible Release is %s with status %s.",
        releaseService.getReleaseLabelValue(previousSuccessfulRelease, RELEASE_NUMBER_LABEL_KEY),
        releaseService.getReleaseLabelValue(previousSuccessfulRelease, RELEASE_STATUS_LABEL_KEY)));

    List<KubernetesResource> resourcesInPrevRelease = releaseService.getResourcesFromRelease(previousSuccessfulRelease);
    rollbackHandlerConfig.setPreviousResources(resourcesInPrevRelease);

    return rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, logCallback, resourcesRecreated, isErrorFrameworkEnabled);
  }

  public List<K8sPod> getPods(int timeoutMins, List<KubernetesResourceIdRevision> managedWorkloadIds,
      List<KubernetesResource> customWorkloads, KubernetesConfig kubernetesConfig, String releaseName)
      throws Exception {
    if (isEmpty(managedWorkloadIds) && isEmpty(customWorkloads)) {
      return new ArrayList<>();
    }
    final Stream<KubernetesResourceId> managedWorkloadStream =
        managedWorkloadIds.stream().map(KubernetesResourceIdRevision::getWorkload);
    final Stream<KubernetesResourceId> customWorkloadStream =
        customWorkloads.stream().map(KubernetesResource::getResourceId);

    List<K8sPod> k8sPods = new ArrayList<>();
    final List<String> namespaces = Stream.concat(managedWorkloadStream, customWorkloadStream)
                                        .map(KubernetesResourceId::getNamespace)
                                        .distinct()
                                        .collect(Collectors.toList());
    for (String namespace : namespaces) {
      List<K8sPod> podDetails =
          k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, ofMinutes(timeoutMins).toMillis());

      if (isNotEmpty(podDetails)) {
        k8sPods.addAll(podDetails);
      }
    }

    return k8sPods;
  }

  private boolean rollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled) throws Exception {
    Kubectl client = rollbackHandlerConfig.getClient();
    List<KubernetesResource> previousResources = rollbackHandlerConfig.getPreviousResources();
    try {
      List<KubernetesResource> previousFilterResources =
          previousResources.stream()
              .filter(resource -> !resourcesRecreated.contains(resource.getResourceId()))
              .collect(toList());
      if (isNotEmpty(previousFilterResources)) {
        logCallback.saveExecutionLog("\nRolling back resources by applying previous release manifests "
            + k8sTaskHelperBase.getResourcesInTableFormat(previousFilterResources));
        k8sTaskHelperBase.applyManifests(
            client, previousFilterResources, k8sDelegateTaskParams, logCallback, true, isErrorFrameworkEnabled);
      }
    } catch (Exception ex) {
      String errorMessage = ExceptionMessageSanitizer.sanitizeException(ex).getMessage();
      log.error("Failed to apply previous successful release's manifest: {}", errorMessage);
      if (isErrorFrameworkEnabled) {
        throw ex;
      }
      return false;
    }
    return true;
  }

  @VisibleForTesting
  ProcessResult executeScript(K8sDelegateTaskParams k8sDelegateTaskParams, String rolloutUndoCommand,
      LogOutputStream logOutputStream, LogOutputStream logErrorStream, Map<String, String> environment)
      throws Exception {
    return Utils.executeScript(
        k8sDelegateTaskParams.getWorkingDirectory(), rolloutUndoCommand, logOutputStream, logErrorStream, environment);
  }

  @VisibleForTesting
  ProcessResult runK8sExecutable(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback,
      RolloutUndoCommand rolloutUndoCommand) throws Exception {
    return K8sTaskHelperBase.executeCommand(rolloutUndoCommand, k8sDelegateTaskParams, logCallback).getProcessResult();
  }

  public ResourceRecreationStatus recreatePrunedResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      Integer releaseNumber, List<KubernetesResourceId> prunedResources, LogCallback pruneLogCallback,
      K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (EmptyPredicate.isEmpty(prunedResources)) {
      pruneLogCallback.saveExecutionLog("No resource got pruned, No need to recreate pruned resources", INFO, SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    List<V1Secret> releases = rollbackHandlerConfig.getReleaseHistory();
    if (isEmpty(releases)) {
      pruneLogCallback.saveExecutionLog(
          "No release history found, No need to recreate pruned resources", INFO, SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    if (releaseNumber == null) { // RollingDeploy was aborted
      V1Secret release = rollbackHandlerConfig.getRelease();
      String releaseStatus = releaseService.getReleaseLabelValue(release, RELEASE_STATUS_LABEL_KEY);
      if (Release.Status.Succeeded.name().equals(releaseStatus)) {
        pruneLogCallback.saveExecutionLog(
            "No failed release found. No need to recreate pruned resources.", INFO, RUNNING);
        pruneLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        return ResourceRecreationStatus.NO_RESOURCE_CREATED;
      }
    }

    V1Secret previousSuccessfulRelease = releaseService.getLastSuccessfulRelease(releases, releaseNumber);
    if (previousSuccessfulRelease == null) {
      pruneLogCallback.saveExecutionLog("No previous eligible release found. Can't recreate pruned resources.");
      pruneLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    List<KubernetesResource> resourcesInPreviousRelease =
        releaseService.getResourcesFromRelease(previousSuccessfulRelease);

    List<KubernetesResource> prunedResourcesToBeRecreated =
        resourcesInPreviousRelease.stream()
            .filter(resource -> prunedResources.contains(resource.getResourceId()))
            .collect(toList());

    if (isEmpty(prunedResourcesToBeRecreated)) {
      pruneLogCallback.saveExecutionLog("No resources are required to be recreated.");
      pruneLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    return k8sTaskHelperBase.applyManifests(rollbackHandlerConfig.getClient(), prunedResourcesToBeRecreated,
               k8sDelegateTaskParams, pruneLogCallback, false)
        ? ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL
        : ResourceRecreationStatus.RESOURCE_CREATION_FAILED;
  }

  public void deleteNewResourcesForCurrentFailedRelease(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      Integer releaseNumber, LogCallback deleteLogCallback, K8sDelegateTaskParams k8sDelegateTaskParams) {
    try {
      List<V1Secret> releases = rollbackHandlerConfig.getReleaseHistory();
      if (isEmpty(releases)) {
        deleteLogCallback.saveExecutionLog(
            "No release history available, No successful release available to compute newly created resources", INFO,
            SUCCESS);
        return;
      }

      // TODO - handle this case better
      if (releaseNumber == null) { // RollingDeploy was aborted
        V1Secret release = rollbackHandlerConfig.getRelease();
        String releaseStatus = releaseService.getReleaseLabelValue(release, RELEASE_STATUS_LABEL_KEY);
        if (Release.Status.Succeeded.name().equals(releaseStatus)) {
          deleteLogCallback.saveExecutionLog("No failed release found. No need to delete resources.", INFO, RUNNING);
          deleteLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
          return;
        }
      }

      V1Secret previousSuccessfulRelease = releaseService.getLastSuccessfulRelease(releases, releaseNumber);
      if (previousSuccessfulRelease == null) {
        deleteLogCallback.saveExecutionLog(
            "No successful previous release available to compute newly created resources", INFO, SUCCESS);
        return;
      }

      V1Secret release = rollbackHandlerConfig.getRelease();
      List<KubernetesResourceId> resourceToBeDeleted =
          getResourcesTobeDeletedInOrder(previousSuccessfulRelease, release);

      if (isEmpty(resourceToBeDeleted)) {
        deleteLogCallback.saveExecutionLog("No new resource identified in current release", INFO, SUCCESS);
        return;
      }

      k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
          rollbackHandlerConfig.getClient(), k8sDelegateTaskParams, resourceToBeDeleted, deleteLogCallback, false);
      deleteLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);

    } catch (Exception ex) {
      deleteLogCallback.saveExecutionLog(
          "Failed in  deleting newly created resources of current failed  release.", WARN, RUNNING);
      deleteLogCallback.saveExecutionLog(getMessage(ex), WARN, SUCCESS);
    }
  }

  public void logResourceRecreationStatus(
      ResourceRecreationStatus resourceRecreationStatus, LogCallback pruneLogCallback) {
    if (resourceRecreationStatus == ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL) {
      pruneLogCallback.saveExecutionLog("Successfully recreated pruned resources.", INFO, SUCCESS);
    } else if (resourceRecreationStatus == ResourceRecreationStatus.NO_RESOURCE_CREATED) {
      pruneLogCallback.saveExecutionLog("No resource recreated.", INFO, SUCCESS);
    }
  }

  @NotNull
  public Set<KubernetesResourceId> getResourcesRecreated(
      List<KubernetesResourceId> prunedResourceIds, ResourceRecreationStatus resourceRecreationStatus) {
    return resourceRecreationStatus.equals(ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL)
        ? new HashSet<>(prunedResourceIds)
        : Collections.emptySet();
  }

  private List<KubernetesResourceId> getResourcesTobeDeletedInOrder(
      V1Secret previousSuccessfulEligibleRelease, V1Secret release) throws IOException {
    List<KubernetesResourceId> previousResources =
        releaseService.getResourcesFromRelease(previousSuccessfulEligibleRelease)
            .stream()
            .map(KubernetesResource::getResourceId)
            .collect(toList());
    List<KubernetesResource> currentResources = releaseService.getResourcesFromRelease(release);

    List<KubernetesResourceId> resourcesToBeDeleted = currentResources.stream()
                                                          .filter(resource -> !resource.isSkipPruning())
                                                          .map(KubernetesResource::getResourceId)
                                                          .filter(resourceId -> !previousResources.contains(resourceId))
                                                          .filter(resourceId -> !resourceId.isVersioned())
                                                          .collect(toList());

    return k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourcesToBeDeleted);
  }

  public List<K8sPod> getPods(int timeoutMins, List<KubernetesResource> previousResources,
      KubernetesConfig kubernetesConfig, String releaseName) throws Exception {
    List<KubernetesResource> managedWorkloads = getWorkloads(previousResources);
    List<KubernetesResource> customWorkloads = getCustomResourceDefinitionWorkloads(previousResources);
    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      return new ArrayList<>();
    }

    final Stream<KubernetesResourceId> managedWorkloadStream =
        managedWorkloads.stream().map(KubernetesResource::getResourceId);
    final Stream<KubernetesResourceId> customWorkloadStream =
        customWorkloads.stream().map(KubernetesResource::getResourceId);

    List<K8sPod> k8sPods = new ArrayList<>();
    final List<String> namespaces = Stream.concat(managedWorkloadStream, customWorkloadStream)
                                        .map(KubernetesResourceId::getNamespace)
                                        .distinct()
                                        .collect(Collectors.toList());
    for (String namespace : namespaces) {
      List<K8sPod> podDetails =
          k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, ofMinutes(timeoutMins).toMillis());

      if (isNotEmpty(podDetails)) {
        k8sPods.addAll(podDetails);
      }
    }

    return k8sPods;
  }

  public enum ResourceRecreationStatus { NO_RESOURCE_CREATED, RESOURCE_CREATION_FAILED, RESOURCE_CREATION_SUCCESSFUL }
}
