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
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getExecutionLogOutputStream;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getOcCommandPrefix;
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getTimeoutMillisFromMinutes;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.k8s.K8sConstants.ocRolloutUndoCommand;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Cyan;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.time.Duration.ofMinutes;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionExplanation;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionHints;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionMessages;
import io.harness.exception.ExplanationException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutUndoCommand;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.Release.KubernetesResourceIdRevision;
import io.harness.k8s.model.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sRollingRollbackBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  public void init(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName, LogCallback logCallback)
      throws IOException {
    String releaseHistoryData =
        k8sTaskHelperBase.getReleaseHistoryData(rollbackHandlerConfig.getKubernetesConfig(), releaseName);

    if (StringUtils.isEmpty(releaseHistoryData)) {
      rollbackHandlerConfig.setNoopRollBack(true);
      logCallback.saveExecutionLog("\nNo release history found for release " + releaseName);
    } else {
      rollbackHandlerConfig.setReleaseHistory(ReleaseHistory.createFromData(releaseHistoryData));
      try {
        rollbackHandlerConfig.setRelease(rollbackHandlerConfig.getReleaseHistory().getLatestRelease());
        printManagedWorkloads(rollbackHandlerConfig, logCallback);
      } catch (Exception e) {
        log.error("Failed to get latest release", e);
      }
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  public void steadyStateCheck(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer timeoutInMin, LogCallback logCallback) throws Exception {
    Release release = rollbackHandlerConfig.getRelease();
    Kubectl client = rollbackHandlerConfig.getClient();
    KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();
    List<KubernetesResourceIdRevision> previousManagedWorkloads = rollbackHandlerConfig.getPreviousManagedWorkloads();
    List<KubernetesResource> previousCustomManagedWorkloads = rollbackHandlerConfig.getPreviousCustomManagedWorkloads();
    if (isEmpty(previousManagedWorkloads) && isEmpty(previousCustomManagedWorkloads)) {
      logCallback.saveExecutionLog("Skipping Status Check since there is no previous eligible Managed Workload.", INFO);
    } else {
      long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(timeoutInMin);
      List<KubernetesResourceId> kubernetesResourceIds =
          previousManagedWorkloads.stream().map(KubernetesResourceIdRevision::getWorkload).collect(toList());
      k8sTaskHelperBase.doStatusCheckForAllResources(
          client, kubernetesResourceIds, k8sDelegateTaskParams, kubernetesConfig.getNamespace(), logCallback, false);

      if (isNotEmpty(previousCustomManagedWorkloads)) {
        k8sTaskHelperBase.checkSteadyStateCondition(previousCustomManagedWorkloads);
        k8sTaskHelperBase.doStatusCheckForAllCustomResources(client, previousCustomManagedWorkloads,
            k8sDelegateTaskParams, logCallback, false, steadyStateTimeoutInMillis);
      }
      release.setStatus(Release.Status.Failed);
      // update the revision on the previous release.
      updateManagedWorkloadRevisionsInRelease(rollbackHandlerConfig, k8sDelegateTaskParams);
    }
  }

  public void postProcess(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName) throws Exception {
    boolean isNoopRollBack = rollbackHandlerConfig.isNoopRollBack();
    KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();
    ReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
    List<KubernetesResource> previousCustomManagedWorkloads = rollbackHandlerConfig.getPreviousCustomManagedWorkloads();
    if (!isNoopRollBack) {
      k8sTaskHelperBase.saveReleaseHistory(
          kubernetesConfig, releaseName, releaseHistory.getAsYaml(), !previousCustomManagedWorkloads.isEmpty());
    }
  }

  // parameter resourcesRecreated must be empty if FF PRUNE_KUBERNETES_RESOURCES is disabled
  public boolean rollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer releaseNumber, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled) throws Exception {
    Release release = rollbackHandlerConfig.getRelease();
    ReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
    if (release == null) {
      logCallback.saveExecutionLog("No previous release found. Skipping rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    if (isEmpty(release.getManagedWorkloads()) && isEmpty(release.getCustomWorkloads())
        && release.getManagedWorkload() == null) {
      logCallback.saveExecutionLog("\nNo Managed Workload found. Skipping rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
    if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
      if (release.getStatus() == Release.Status.Succeeded) {
        logCallback.saveExecutionLog("No failed release found. Skipping rollback.");
        logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      } else {
        // set releaseNumber to max int so that rollback to current successful one goes through.
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    rollbackHandlerConfig.setPreviousRollbackEligibleRelease(
        releaseHistory.getPreviousRollbackEligibleRelease(rollbackReleaseNumber));
    Release previousRollbackEligibleRelease = rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
    if (previousRollbackEligibleRelease == null) {
      logCallback.saveExecutionLog("No previous eligible release found. Can't rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    logCallback.saveExecutionLog("Previous eligible Release is " + previousRollbackEligibleRelease.getNumber()
        + " with status " + previousRollbackEligibleRelease.getStatus());

    if (isEmpty(previousRollbackEligibleRelease.getManagedWorkloads())
        && previousRollbackEligibleRelease.getManagedWorkload() == null
        && isEmpty(previousRollbackEligibleRelease.getCustomWorkloads())) {
      logCallback.saveExecutionLog("No Managed Workload found in previous eligible release. Skipping rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    if (isNotEmpty(previousRollbackEligibleRelease.getCustomWorkloads())) {
      rollbackHandlerConfig.getPreviousCustomManagedWorkloads().addAll(
          previousRollbackEligibleRelease.getCustomWorkloads());
    }

    List<KubernetesResourceIdRevision> previousManagedWorkloads = new ArrayList<>();
    if (isNotEmpty(previousRollbackEligibleRelease.getManagedWorkloads())) {
      previousManagedWorkloads.addAll(previousRollbackEligibleRelease.getManagedWorkloads());
    } else if (previousRollbackEligibleRelease.getManagedWorkload() != null) {
      previousManagedWorkloads.add(KubernetesResourceIdRevision.builder()
                                       .workload(previousRollbackEligibleRelease.getManagedWorkload())
                                       .revision(previousRollbackEligibleRelease.getManagedWorkloadRevision())
                                       .build());
    }
    rollbackHandlerConfig.setPreviousManagedWorkloads(previousManagedWorkloads);

    boolean success = rollback(
        rollbackHandlerConfig, k8sDelegateTaskParams, logCallback, resourcesRecreated, isErrorFrameworkEnabled);
    if (!success) {
      logCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      return false;
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
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
    boolean success = true;
    Exception customResourcesApplyException = null;
    Release release = rollbackHandlerConfig.getRelease();
    Kubectl client = rollbackHandlerConfig.getClient();
    Release previousRollbackEligibleRelease = rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
    List<KubernetesResource> previousCustomManagedWorkloads =
        rollbackHandlerConfig.getPreviousCustomManagedWorkloads()
            .stream()
            .filter(resource -> !resourcesRecreated.contains(resource.getResourceId()))
            .collect(toList());
    List<KubernetesResourceIdRevision> previousManagedWorkloads =
        rollbackHandlerConfig.getPreviousManagedWorkloads()
            .stream()
            .filter(resourceIdRevision -> !resourcesRecreated.contains(resourceIdRevision.getWorkload()))
            .collect(toList());

    if (isNotEmpty(previousCustomManagedWorkloads)) {
      if (isNotEmpty(release.getCustomWorkloads())) {
        logCallback.saveExecutionLog("\nDeleting current custom resources "
            + k8sTaskHelperBase.getResourcesInTableFormat(release.getCustomWorkloads()));

        k8sTaskHelperBase.delete(client, k8sDelegateTaskParams,
            release.getCustomWorkloads().stream().map(KubernetesResource::getResourceId).collect(toList()), logCallback,
            false);
      }
      logCallback.saveExecutionLog("\nRolling back custom resource by applying previous release manifests "
          + k8sTaskHelperBase.getResourcesInTableFormat(previousCustomManagedWorkloads));
      try {
        success = k8sTaskHelperBase.applyManifests(
            client, previousCustomManagedWorkloads, k8sDelegateTaskParams, logCallback, false, isErrorFrameworkEnabled);
      } catch (Exception e) {
        customResourcesApplyException = e;
        success = false;
      }
    }

    logCallback.saveExecutionLog("\nRolling back to release " + previousRollbackEligibleRelease.getNumber());

    for (KubernetesResourceIdRevision kubernetesResourceIdRevision : previousManagedWorkloads) {
      logCallback.saveExecutionLog(format("%nRolling back resource %s in namespace %s to revision %s",
          kubernetesResourceIdRevision.getWorkload().kindNameRef(),
          kubernetesResourceIdRevision.getWorkload().getNamespace(), kubernetesResourceIdRevision.getRevision()));

      ProcessResult result;

      KubernetesResourceId resourceId = kubernetesResourceIdRevision.getWorkload();
      String printableCommand;
      if (Kind.DeploymentConfig.name().equals(resourceId.getKind())) {
        String rolloutUndoCommand = getRolloutUndoCommandForDeploymentConfig(k8sDelegateTaskParams,
            kubernetesResourceIdRevision.getWorkload(), kubernetesResourceIdRevision.getRevision());

        printableCommand = rolloutUndoCommand.substring(rolloutUndoCommand.indexOf("oc --kubeconfig"));
        logCallback.saveExecutionLog(printableCommand + "\n");

        try (LogOutputStream logOutputStream = getExecutionLogOutputStream(logCallback, INFO);
             LogOutputStream logErrorStream = getExecutionLogOutputStream(logCallback, ERROR)) {
          printableCommand = new StringBuilder().append("\n").append(printableCommand).append("\n\n").toString();
          logOutputStream.write(printableCommand.getBytes(StandardCharsets.UTF_8));
          result = executeScript(
              k8sDelegateTaskParams, rolloutUndoCommand, logOutputStream, logErrorStream, Maps.newHashMap());
        }
      } else {
        RolloutUndoCommand rolloutUndoCommand =
            client.rollout()
                .undo()
                .resource(kubernetesResourceIdRevision.getWorkload().kindNameRef())
                .namespace(kubernetesResourceIdRevision.getWorkload().getNamespace())
                .toRevision(kubernetesResourceIdRevision.getRevision());
        printableCommand = getPrintableCommand(rolloutUndoCommand.command());

        result = runK8sExecutable(k8sDelegateTaskParams, logCallback, rolloutUndoCommand);
      }

      if (result.getExitValue() != 0) {
        logCallback.saveExecutionLog(format("%nFailed to rollback resource %s in namespace %s to revision %s. Error %s",
            kubernetesResourceIdRevision.getWorkload().kindNameRef(),
            kubernetesResourceIdRevision.getWorkload().getNamespace(), kubernetesResourceIdRevision.getRevision(),
            result.getOutput()));

        if (isErrorFrameworkEnabled) {
          String explanation = result.hasOutput()
              ? format(KubernetesExceptionExplanation.ROLLBACK_CLI_FAILED_OUTPUT, printableCommand,
                  result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.ROLLBACK_CLI_FAILED, printableCommand, result.getExitValue());
          throw NestedExceptionUtils.hintWithExplanationException(
              format(KubernetesExceptionHints.ROLLBACK_CLI_FAILED,
                  kubernetesResourceIdRevision.getWorkload().kindNameRef()),
              explanation,
              new KubernetesTaskException(format(KubernetesExceptionMessages.ROLLBACK_CLI_FAILED,
                  kubernetesResourceIdRevision.getWorkload().kindNameRef(),
                  kubernetesResourceIdRevision.getWorkload().getNamespace(),
                  kubernetesResourceIdRevision.getRevision())));
        }

        return false;
      }
    }

    if (customResourcesApplyException != null) {
      throw new ExplanationException(
          KubernetesExceptionExplanation.ROLLBACK_CR_APPLY_FAILED, customResourcesApplyException);
    }

    return success;
  }

  private void printManagedWorkloads(K8sRollingRollbackHandlerConfig handlerConfig, LogCallback logCallback) {
    Release release = handlerConfig.getRelease();

    List<KubernetesResource> kubernetesResources = new ArrayList<>();

    if (isNotEmpty(release.getCustomWorkloads())) {
      kubernetesResources.addAll(release.getCustomWorkloads());
    }

    if (isNotEmpty(release.getManagedWorkloads())) {
      for (KubernetesResourceIdRevision kubernetesResourceIdRevision : release.getManagedWorkloads()) {
        kubernetesResources.add(
            KubernetesResource.builder().resourceId(kubernetesResourceIdRevision.getWorkload()).build());
      }
    } else if (release.getManagedWorkload() != null) {
      kubernetesResources.add(KubernetesResource.builder().resourceId(release.getManagedWorkload()).build());
    }

    if (isNotEmpty(kubernetesResources)) {
      logCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
          + k8sTaskHelperBase.getResourcesInTableFormat(kubernetesResources));
    }
  }

  private void updateManagedWorkloadRevisionsInRelease(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    Release previousRollbackEligibleRelease = rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
    if (isNotEmpty(previousRollbackEligibleRelease.getManagedWorkloads())) {
      for (KubernetesResourceIdRevision kubernetesResourceIdRevision :
          previousRollbackEligibleRelease.getManagedWorkloads()) {
        String latestRevision = k8sTaskHelperBase.getLatestRevision(
            rollbackHandlerConfig.getClient(), kubernetesResourceIdRevision.getWorkload(), k8sDelegateTaskParams);

        kubernetesResourceIdRevision.setRevision(latestRevision);
      }
    } else if (previousRollbackEligibleRelease.getManagedWorkload() != null) {
      previousRollbackEligibleRelease.setManagedWorkloadRevision(
          k8sTaskHelperBase.getLatestRevision(rollbackHandlerConfig.getClient(),
              previousRollbackEligibleRelease.getManagedWorkload(), k8sDelegateTaskParams));
    }
  }

  private String getRolloutUndoCommandForDeploymentConfig(
      K8sDelegateTaskParams k8sDelegateTaskParams, KubernetesResourceId resourceId, String revision) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    String evaluatedRevision = "";
    if (StringUtils.isNotBlank(revision)) {
      evaluatedRevision = "--to-revision=" + revision;
    }

    return ocRolloutUndoCommand.replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(k8sDelegateTaskParams))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace)
        .replace("{REVISION}", evaluatedRevision)
        .trim();
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
    ReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
    if (releaseHistory == null) {
      pruneLogCallback.saveExecutionLog(
          "No release history found, No need to recreate pruned resources", INFO, SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
    // check if its even possible case?
    if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
      if (rollbackHandlerConfig.getRelease().getStatus() == Release.Status.Succeeded) {
        pruneLogCallback.saveExecutionLog(
            "No failed release found. No need to recreate pruned resources.", INFO, RUNNING);
        pruneLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        return ResourceRecreationStatus.NO_RESOURCE_CREATED;
      } else {
        // set releaseNumber to max int so that rollback to current successful one goes through.
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    Release previousRollbackEligibleRelease = releaseHistory.getPreviousRollbackEligibleRelease(rollbackReleaseNumber);
    if (previousRollbackEligibleRelease == null) {
      pruneLogCallback.saveExecutionLog("No previous eligible release found. Can't recreate pruned resources.");
      pruneLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    Map<KubernetesResourceId, KubernetesResource> previousKubernetesResourceMap =
        previousRollbackEligibleRelease.getResourcesWithSpec().stream().collect(
            toMap(KubernetesResource::getResourceId, Function.identity()));

    List<KubernetesResource> prunedResourcesToBeRecreated = prunedResources.stream()
                                                                .filter(previousKubernetesResourceMap::containsKey)
                                                                .map(previousKubernetesResourceMap::get)
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
      ReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
      if (releaseHistory == null) {
        deleteLogCallback.saveExecutionLog(
            "No release history available, No successful release available to compute newly created resources", INFO,
            SUCCESS);
        return;
      }

      int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
      if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
        if (rollbackHandlerConfig.getRelease().getStatus() == Release.Status.Succeeded) {
          deleteLogCallback.saveExecutionLog("No failed release found. No need to delete resources.", INFO, RUNNING);
          deleteLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
          return;
        } else {
          // set releaseNumber to max int so that rollback to current successful one goes through.
          rollbackReleaseNumber = Integer.MAX_VALUE;
        }
      }

      Release previousSuccessfulEligibleRelease =
          releaseHistory.getPreviousRollbackEligibleRelease(rollbackReleaseNumber);
      if (previousSuccessfulEligibleRelease == null) {
        deleteLogCallback.saveExecutionLog(
            "No successful previous release available to compute newly created resources", INFO, SUCCESS);
        return;
      }

      Release release = rollbackHandlerConfig.getRelease();
      List<KubernetesResourceId> resourceToBeDeleted =
          getResourcesTobeDeletedInOrder(previousSuccessfulEligibleRelease, release);

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
      Release previousSuccessfulEligibleRelease, Release release) {
    List<KubernetesResourceId> resourceToBeDeleted =
        release.getResourcesWithSpec()
            .stream()
            .filter(resource -> !resource.isSkipPruning())
            .map(KubernetesResource::getResourceId)
            .filter(resource -> !previousSuccessfulEligibleRelease.getResources().contains(resource))
            .filter(resource -> !resource.isVersioned())
            .collect(toList());
    return k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourceToBeDeleted);
  }

  public enum ResourceRecreationStatus { NO_RESOURCE_CREATED, RESOURCE_CREATION_FAILED, RESOURCE_CREATION_SUCCESSFUL }
}
