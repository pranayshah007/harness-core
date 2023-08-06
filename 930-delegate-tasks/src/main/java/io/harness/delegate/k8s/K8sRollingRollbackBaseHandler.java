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
import static io.harness.k8s.manifest.ManifestHelper.getCustomResourceDefinitionWorkloads;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloads;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExplanationException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sCliCommandType;
import io.harness.k8s.K8sCommandFlagsUtils;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutUndoCommand;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sLegacyRelease.KubernetesResourceIdRevision;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sRollingRollbackBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sRollingBaseHandler rollingBaseHandler;

  private void initUsingLegacyReleaseHistory(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      IK8sReleaseHistory releaseHistory, String releaseName, LogCallback logCallback) {
    if (isEmpty(releaseHistory)) {
      rollbackHandlerConfig.setNoopRollBack(true);
      logCallback.saveExecutionLog("\nNo release history found for release " + releaseName);
    } else {
      rollbackHandlerConfig.setUseDeclarativeRollback(false);
      rollbackHandlerConfig.setReleaseHistory(releaseHistory);
      try {
        rollbackHandlerConfig.setRelease(releaseHistory.getLatestRelease());
        printManagedWorkloads(rollbackHandlerConfig, logCallback);
      } catch (Exception e) {
        log.error("Failed to get latest release", e);
      }
    }
  }

  public void init(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName, LogCallback logCallback)
      throws Exception {
    boolean useDeclarativeRollback = rollbackHandlerConfig.isUseDeclarativeRollback();
    IK8sReleaseHistory releaseHistory =
        getReleaseHistory(rollbackHandlerConfig.getKubernetesConfig(), releaseName, useDeclarativeRollback);

    if (!useDeclarativeRollback) {
      // FF off
      initUsingLegacyReleaseHistory(rollbackHandlerConfig, releaseHistory, releaseName, logCallback);
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return;
    }

    initWithDeclarativeRollbackEnabled(releaseHistory, rollbackHandlerConfig, releaseName, logCallback);
    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  private void initWithDeclarativeRollbackEnabled(IK8sReleaseHistory releaseHistory,
      K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName, LogCallback logCallback)
      throws Exception {
    if (isEmpty(releaseHistory)) {
      // No declarative release history found

      if (rollbackHandlerConfig.getCurrentReleaseNumber() != null) {
        // post-prod rollback case
        // Using existing latest release as baseline, rollback to previous eligible release if possible
        log.info("Rolling back existing latest imperative release for release {}", releaseName);
        K8SLegacyReleaseHistory oldReleaseHistory = (K8SLegacyReleaseHistory) getReleaseHistory(
            rollbackHandlerConfig.getKubernetesConfig(), releaseName, false);
        initUsingLegacyReleaseHistory(rollbackHandlerConfig, oldReleaseHistory, releaseName, logCallback);
        return;
      }

      // declarative rollback FF turned on + no declarative release entries
      log.info("Skipping rollback since no declarative release was created for release {}", releaseName);
      rollbackHandlerConfig.setNoopRollBack(true);
      return;
    }

    configureHandlerUsingLastSuccessfulRelease(releaseHistory, rollbackHandlerConfig, releaseName, logCallback);
  }

  private void configureHandlerUsingLastSuccessfulRelease(IK8sReleaseHistory releaseHistory,
      K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName, LogCallback logCallback)
      throws Exception {
    IK8sRelease latestRelease = releaseHistory.getLatestRelease();
    int rollbackReleaseNumber =
        rollbackHandlerConfig.getCurrentReleaseNumber() != null ? rollbackHandlerConfig.getCurrentReleaseNumber() : 0;
    if (rollbackReleaseNumber == 0) { // if Rolling step was aborted
      if (Succeeded == latestRelease.getReleaseStatus()) {
        logCallback.saveExecutionLog("Latest release was successful. Skipping rollback.");
        rollbackHandlerConfig.setNoopRollBack(true);
        return;
      } else {
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    IK8sRelease lastSuccessfulRelease = releaseHistory.getLastSuccessfulRelease(rollbackReleaseNumber);
    if (lastSuccessfulRelease == null) {
      rollbackHandlerConfig.setRelease(latestRelease);
      rollbackHandlerConfig.setReleaseHistory(releaseHistory);
      configureRollbackReleaseFromLegacyHistory(rollbackHandlerConfig, releaseName, latestRelease);
      return;
    }

    // continue using new release history. no need for legacy release history.
    rollbackHandlerConfig.setRelease(latestRelease);
    rollbackHandlerConfig.setReleaseHistory(releaseHistory);
  }

  private void configureRollbackReleaseFromLegacyHistory(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      String releaseName, IK8sRelease latestRelease) throws Exception {
    K8SLegacyReleaseHistory oldReleaseHistory =
        (K8SLegacyReleaseHistory) getReleaseHistory(rollbackHandlerConfig.getKubernetesConfig(), releaseName, false);

    if (checkIfAtLeastOneSuccessfulReleaseExists(oldReleaseHistory)) {
      K8sLegacyRelease latestReleaseLegacyFormat = convertReleaseToLegacyFormat((K8sRelease) latestRelease,
          rollbackHandlerConfig.getK8sDelegateTaskParams(), rollbackHandlerConfig.getClient());

      rollbackHandlerConfig.setUseDeclarativeRollback(false);
      rollbackHandlerConfig.setSwitchToLegacyReleaseHistory(true);
      rollbackHandlerConfig.setLatestDeclarativeRelease(latestRelease);
      rollbackHandlerConfig.setRelease(latestReleaseLegacyFormat);
      rollbackHandlerConfig.setCurrentReleaseNumber(Integer.MAX_VALUE);
      rollbackHandlerConfig.setReleaseHistory(oldReleaseHistory);
      log.info("Configuring rollback using imperative release history.");
    } else {
      log.info("Did not find an eligible release for rollback in imperative release history.");
    }
  }

  private K8sLegacyRelease convertReleaseToLegacyFormat(
      K8sRelease release, K8sDelegateTaskParams k8sDelegateTaskParams, Kubectl client) throws Exception {
    K8sLegacyRelease legacyRelease =
        K8sLegacyRelease.builder().number(release.getReleaseNumber()).status(release.getReleaseStatus()).build();
    List<KubernetesResource> resources = release.getResourcesWithSpecs();
    List<KubernetesResource> managedWorkloads = getWorkloads(resources);
    List<KubernetesResource> customWorkloads = getCustomResourceDefinitionWorkloads(resources);

    rollingBaseHandler.setManagedWorkloadsInRelease(k8sDelegateTaskParams, managedWorkloads, legacyRelease, client);
    rollingBaseHandler.setCustomWorkloadsInRelease(customWorkloads, legacyRelease);
    legacyRelease.setResources(resources.stream().map(KubernetesResource::getResourceId).collect(toList()));
    return legacyRelease;
  }

  private IK8sReleaseHistory getReleaseHistory(
      KubernetesConfig kubernetesConfig, String releaseName, boolean useDeclarativeRollback) throws Exception {
    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    return releaseHandler.getReleaseHistory(kubernetesConfig, releaseName);
  }

  private boolean checkIfAtLeastOneSuccessfulReleaseExists(IK8sReleaseHistory releaseHistory) {
    return isNotEmpty(releaseHistory) && releaseHistory.getLastSuccessfulRelease(Integer.MAX_VALUE) != null;
  }

  public void steadyStateCheck(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer timeoutInMin, LogCallback logCallback) throws Exception {
    if (!rollbackHandlerConfig.isUseDeclarativeRollback()) {
      legacySteadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams, logCallback, timeoutInMin);
    } else {
      steadyStateCheckForWorkloads(rollbackHandlerConfig, k8sDelegateTaskParams, logCallback, timeoutInMin);
    }
  }

  private void legacySteadyStateCheck(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback, Integer timeoutInMin) throws Exception {
    IK8sRelease release = rollbackHandlerConfig.getRelease();
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

      if (rollbackHandlerConfig.isSwitchToLegacyReleaseHistory()) {
        IK8sRelease latestDeclarativeRelease = rollbackHandlerConfig.getLatestDeclarativeRelease();
        if (latestDeclarativeRelease != null) {
          latestDeclarativeRelease.updateReleaseStatus(Failed);
          saveDeclarativeRelease(latestDeclarativeRelease, kubernetesConfig);
        }
      } else {
        release.updateReleaseStatus(Failed);
      }
      // update the revision on the previous release.
      updateManagedWorkloadRevisionsInRelease(rollbackHandlerConfig, k8sDelegateTaskParams);
    }
  }

  private void saveDeclarativeRelease(IK8sRelease latestDeclarativeRelease, KubernetesConfig kubernetesConfig)
      throws Exception {
    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(true);
    releaseHandler.saveRelease(
        K8sReleasePersistDTO.builder().kubernetesConfig(kubernetesConfig).release(latestDeclarativeRelease).build());
  }

  private void steadyStateCheckForWorkloads(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback, Integer timeoutInMin) throws Exception {
    IK8sRelease release = rollbackHandlerConfig.getRelease();
    Kubectl client = rollbackHandlerConfig.getClient();
    KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();
    List<KubernetesResource> managedWorkloads = getWorkloads(rollbackHandlerConfig.getPreviousResources());
    List<KubernetesResource> customWorkloads =
        getCustomResourceDefinitionWorkloads(rollbackHandlerConfig.getPreviousResources());

    if (isEmpty(managedWorkloads) && isEmpty(customWorkloads)) {
      logCallback.saveExecutionLog("Skipping Status Check since there is no previous eligible Managed Workload.", INFO);
    } else {
      long steadyStateTimeoutInMillis = getTimeoutMillisFromMinutes(timeoutInMin);
      List<KubernetesResourceId> managedResourceIds =
          managedWorkloads.stream().map(KubernetesResource::getResourceId).collect(toList());

      k8sTaskHelperBase.doStatusCheckForAllResources(
          client, managedResourceIds, k8sDelegateTaskParams, kubernetesConfig.getNamespace(), logCallback, false);

      if (isNotEmpty(customWorkloads)) {
        k8sTaskHelperBase.checkSteadyStateCondition(customWorkloads);
        k8sTaskHelperBase.doStatusCheckForAllCustomResources(
            client, customWorkloads, k8sDelegateTaskParams, logCallback, false, steadyStateTimeoutInMillis);
      }
      release.updateReleaseStatus(Failed);
    }
  }

  public void postProcess(K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName) throws Exception {
    if (!rollbackHandlerConfig.isNoopRollBack()) {
      KubernetesConfig kubernetesConfig = rollbackHandlerConfig.getKubernetesConfig();
      IK8sReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
      List<KubernetesResource> previousCustomWorkloads = rollbackHandlerConfig.getPreviousCustomManagedWorkloads();

      if (!rollbackHandlerConfig.isSwitchToLegacyReleaseHistory() && rollbackHandlerConfig.getRelease() != null) {
        // if switched to legacy history, latest declarative release is already set to failed and saved during steady
        // check
        rollbackHandlerConfig.getRelease().updateReleaseStatus(Failed);
      }

      k8sTaskHelperBase.saveRelease(rollbackHandlerConfig.isUseDeclarativeRollback(),
          isNotEmpty(previousCustomWorkloads), kubernetesConfig, rollbackHandlerConfig.getRelease(), releaseHistory,
          releaseName);
    }
  }

  // different implementations for rollback for legacy/current release history
  public boolean rollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer releaseNumber, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled, Map<String, String> commandFlags)
      throws Exception {
    if (!rollbackHandlerConfig.isUseDeclarativeRollback()) {
      return legacyRollback(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback,
          resourcesRecreated, isErrorFrameworkEnabled, commandFlags);
    }
    return rollbackRelease(rollbackHandlerConfig, k8sDelegateTaskParams, releaseNumber, logCallback, resourcesRecreated,
        isErrorFrameworkEnabled, commandFlags);
  }

  private boolean rollbackRelease(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer releaseNumber, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled, Map<String, String> commandFlags)
      throws Exception {
    K8sRelease release = (K8sRelease) rollbackHandlerConfig.getRelease();
    K8sReleaseHistory releaseHistory = (K8sReleaseHistory) rollbackHandlerConfig.getReleaseHistory();

    if (release == null || isEmpty(releaseHistory)) {
      logCallback.saveExecutionLog("No release data found. Skipping rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
    if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
      if (Succeeded == release.getReleaseStatus()) {
        rollbackHandlerConfig.setNoopRollBack(true);
        logCallback.saveExecutionLog("No failed release found. Skipping rollback.");
        logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      } else {
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    K8sRelease lastSuccessfulRelease = (K8sRelease) releaseHistory.getLastSuccessfulRelease(rollbackReleaseNumber);
    if (lastSuccessfulRelease == null) {
      logCallback.saveExecutionLog("No previous eligible release found. Can't rollback.");
      logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return true;
    }

    logCallback.saveExecutionLog(String.format("Previous eligible Release is %s with status %s.",
        lastSuccessfulRelease.getReleaseNumber(), lastSuccessfulRelease.getReleaseStatus().name()));

    List<KubernetesResource> resourcesInPrevRelease = lastSuccessfulRelease.getResourcesWithSpecs();
    setNamespaceInResources(resourcesInPrevRelease, rollbackHandlerConfig.getKubernetesConfig().getNamespace());
    rollbackHandlerConfig.setPreviousResources(resourcesInPrevRelease);

    boolean success = rollbackReleaseResources(rollbackHandlerConfig, k8sDelegateTaskParams, logCallback,
        resourcesRecreated, isErrorFrameworkEnabled, commandFlags);
    if (!success) {
      logCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      return false;
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  private boolean rollbackReleaseResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled, Map<String, String> commandFlags)
      throws Exception {
    Kubectl client = rollbackHandlerConfig.getClient();
    List<KubernetesResource> previousResources = rollbackHandlerConfig.getPreviousResources();
    List<KubernetesResource> currentResources = rollbackHandlerConfig.getRelease().getResourcesWithSpecs();
    List<KubernetesResource> currentCustomWorkloads = getCustomResourceDefinitionWorkloads(currentResources);

    try {
      if (isNotEmpty(currentCustomWorkloads)) {
        logCallback.saveExecutionLog("\nDeleting current custom resources "
            + k8sTaskHelperBase.getResourcesInTableFormat(currentCustomWorkloads));

        k8sTaskHelperBase.delete(client, k8sDelegateTaskParams,
            currentCustomWorkloads.stream().map(KubernetesResource::getResourceId).collect(toList()), logCallback,
            false);
      }

      List<KubernetesResource> previousResourcesToBeCreated =
          previousResources.stream()
              .filter(resource -> !resourcesRecreated.contains(resource.getResourceId()))
              .collect(toList());
      if (isNotEmpty(previousResourcesToBeCreated)) {
        logCallback.saveExecutionLog("\nRolling back resources by applying previous release manifests "
            + k8sTaskHelperBase.getResourcesInTableFormat(previousResourcesToBeCreated));
        String commandFlagsString =
            K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), commandFlags);
        k8sTaskHelperBase.applyManifests(client, previousResourcesToBeCreated, k8sDelegateTaskParams, logCallback, true,
            isErrorFrameworkEnabled, commandFlagsString);
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

  // parameter resourcesRecreated must be empty if FF PRUNE_KUBERNETES_RESOURCES is disabled
  public boolean legacyRollback(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Integer releaseNumber, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled, Map<String, String> commandFlags)
      throws Exception {
    K8sLegacyRelease release = (K8sLegacyRelease) rollbackHandlerConfig.getRelease();
    K8SLegacyReleaseHistory releaseHistory = (K8SLegacyReleaseHistory) rollbackHandlerConfig.getReleaseHistory();
    if (release == null || isEmpty(releaseHistory)) {
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
      if (release.getStatus() == Succeeded) {
        rollbackHandlerConfig.setNoopRollBack(true);
        logCallback.saveExecutionLog("No failed release found. Skipping rollback.");
        logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        return true;
      } else {
        // set releaseNumber to max int so that rollback to current successful one goes through.
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    rollbackHandlerConfig.setPreviousRollbackEligibleRelease(
        releaseHistory.getReleaseHistory().getPreviousRollbackEligibleRelease(rollbackReleaseNumber));
    K8sLegacyRelease previousRollbackEligibleRelease =
        (K8sLegacyRelease) rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
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

    boolean success = legacyRollbackResources(rollbackHandlerConfig, k8sDelegateTaskParams, logCallback,
        resourcesRecreated, isErrorFrameworkEnabled, commandFlags);
    if (!success) {
      logCallback.saveExecutionLog("\nFailed.", INFO, CommandExecutionStatus.FAILURE);
      return false;
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  public List<K8sPod> getPods(
      int timeoutMins, K8sRollingRollbackHandlerConfig rollbackHandlerConfig, String releaseName) throws Exception {
    List<K8sPod> k8sPods = new ArrayList<>();
    final List<String> namespaces = getNamespacesFromWorkloads(rollbackHandlerConfig);
    for (String namespace : namespaces) {
      List<K8sPod> podDetails = k8sTaskHelperBase.getPodDetails(
          rollbackHandlerConfig.getKubernetesConfig(), namespace, releaseName, ofMinutes(timeoutMins).toMillis());

      if (isNotEmpty(podDetails)) {
        k8sPods.addAll(podDetails);
      }
    }

    return k8sPods;
  }

  public List<K8sPod> getExistingPods(int timeoutMins, K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      String releaseName, LogCallback logCallback) throws Exception {
    List<K8sPod> existingPodList;
    String namespace = rollbackHandlerConfig.getKubernetesConfig().getNamespace();
    try {
      logCallback.saveExecutionLog("\nFetching existing pod list.");
      existingPodList = k8sTaskHelperBase.getPodDetails(
          rollbackHandlerConfig.getKubernetesConfig(), namespace, releaseName, ofMinutes(timeoutMins).toMillis());
    } catch (Exception e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      return Collections.emptyList();
    }

    logCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);

    return existingPodList;
  }

  private List<String> getNamespacesFromWorkloads(K8sRollingRollbackHandlerConfig rollbackHandlerConfig) {
    boolean useDeclarativeRollback = rollbackHandlerConfig.isUseDeclarativeRollback();

    Set<String> namespaces = new HashSet<>();
    namespaces.add(rollbackHandlerConfig.getKubernetesConfig().getNamespace());

    if (!useDeclarativeRollback) {
      List<KubernetesResourceIdRevision> managedWorkloadIds = rollbackHandlerConfig.getPreviousManagedWorkloads();
      List<KubernetesResource> customWorkloads = rollbackHandlerConfig.getPreviousCustomManagedWorkloads();
      if (isEmpty(managedWorkloadIds) && isEmpty(customWorkloads)) {
        return Collections.emptyList();
      }

      Stream<KubernetesResourceId> managedWorkloadStream =
          managedWorkloadIds.stream().map(KubernetesResourceIdRevision::getWorkload);
      Stream<KubernetesResourceId> customWorkloadStream =
          customWorkloads.stream().map(KubernetesResource::getResourceId);
      List<String> namespacesFromWorkloads = Stream.concat(managedWorkloadStream, customWorkloadStream)
                                                 .map(KubernetesResourceId::getNamespace)
                                                 .filter(EmptyPredicate::isNotEmpty)
                                                 .collect(Collectors.toList());
      namespaces.addAll(namespacesFromWorkloads);
      return new ArrayList<>(namespaces);
    }

    if (isEmpty(rollbackHandlerConfig.getPreviousResources())) {
      return Collections.emptyList();
    }

    List<String> namespacesFromWorkloads = rollbackHandlerConfig.getPreviousResources()
                                               .stream()
                                               .map(KubernetesResource::getResourceId)
                                               .map(KubernetesResourceId::getNamespace)
                                               .filter(EmptyPredicate::isNotEmpty)
                                               .collect(Collectors.toList());
    namespaces.addAll(namespacesFromWorkloads);
    return new ArrayList<>(namespaces);
  }

  private boolean legacyRollbackResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback,
      Set<KubernetesResourceId> resourcesRecreated, boolean isErrorFrameworkEnabled, Map<String, String> k8sCommandFlag)
      throws Exception {
    boolean success = true;
    Exception customResourcesApplyException = null;
    K8sLegacyRelease release = (K8sLegacyRelease) rollbackHandlerConfig.getRelease();
    Kubectl client = rollbackHandlerConfig.getClient();
    K8sLegacyRelease previousRollbackEligibleRelease =
        (K8sLegacyRelease) rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
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
    String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);
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
        success = k8sTaskHelperBase.applyManifests(client, previousCustomManagedWorkloads, k8sDelegateTaskParams,
            logCallback, false, isErrorFrameworkEnabled, commandFlags);
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
        logCallback.saveExecutionLog(format("%nFailed to rollback resource %s in namespace %s to revision %s. %n%s",
            kubernetesResourceIdRevision.getWorkload().kindNameRef(),
            kubernetesResourceIdRevision.getWorkload().getNamespace(), kubernetesResourceIdRevision.getRevision(),
            result.hasOutput() ? result.outputUTF8() : ""));

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
    IK8sRelease release = handlerConfig.getRelease();
    List<KubernetesResource> kubernetesResources =
        getResourcesToPrint(release, handlerConfig.isUseDeclarativeRollback());

    if (isNotEmpty(kubernetesResources)) {
      logCallback.saveExecutionLog(color("\nFound following Managed Workloads: \n", Cyan, Bold)
          + k8sTaskHelperBase.getResourcesInTableFormat(kubernetesResources));
    }
  }

  private List<KubernetesResource> getResourcesToPrint(IK8sRelease release, boolean useDeclarativeRollback) {
    if (useDeclarativeRollback) {
      return release.getResourcesWithSpecs();
    }

    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sLegacyRelease legacyRelease = (K8sLegacyRelease) release;
    if (isNotEmpty(legacyRelease.getCustomWorkloads())) {
      kubernetesResources.addAll(legacyRelease.getCustomWorkloads());
    }

    if (isNotEmpty(legacyRelease.getManagedWorkloads())) {
      for (KubernetesResourceIdRevision kubernetesResourceIdRevision : legacyRelease.getManagedWorkloads()) {
        kubernetesResources.add(
            KubernetesResource.builder().resourceId(kubernetesResourceIdRevision.getWorkload()).build());
      }
    } else if (legacyRelease.getManagedWorkload() != null) {
      kubernetesResources.add(KubernetesResource.builder().resourceId(legacyRelease.getManagedWorkload()).build());
    }

    return kubernetesResources;
  }

  private void updateManagedWorkloadRevisionsInRelease(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    K8sLegacyRelease previousRollbackEligibleRelease =
        (K8sLegacyRelease) rollbackHandlerConfig.getPreviousRollbackEligibleRelease();
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
    return K8sTaskHelperBase.executeCommand(rolloutUndoCommand, k8sDelegateTaskParams, logCallback, ERROR)
        .getProcessResult();
  }

  public ResourceRecreationStatus recreatePrunedResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      Integer releaseNumber, List<KubernetesResourceId> prunedResources, LogCallback pruneLogCallback,
      K8sDelegateTaskParams k8sDelegateTaskParams, Map<String, String> k8sCommandFlag) throws Exception {
    if (EmptyPredicate.isEmpty(prunedResources)) {
      pruneLogCallback.saveExecutionLog("No resource got pruned, No need to recreate pruned resources", INFO, SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }
    IK8sReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
    if (isEmpty(releaseHistory)) {
      pruneLogCallback.saveExecutionLog(
          "No release history found, No need to recreate pruned resources", INFO, SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
    // check if its even possible case?
    if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
      if (Succeeded == rollbackHandlerConfig.getRelease().getReleaseStatus()) {
        pruneLogCallback.saveExecutionLog(
            "No failed release found. No need to recreate pruned resources.", INFO, RUNNING);
        pruneLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
        return ResourceRecreationStatus.NO_RESOURCE_CREATED;
      } else {
        // set releaseNumber to max int so that rollback to current successful one goes through.
        rollbackReleaseNumber = Integer.MAX_VALUE;
      }
    }

    IK8sRelease previousRollbackEligibleRelease = releaseHistory.getLastSuccessfulRelease(rollbackReleaseNumber);
    if (previousRollbackEligibleRelease == null) {
      pruneLogCallback.saveExecutionLog("No previous eligible release found. Can't recreate pruned resources.");
      pruneLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }

    List<KubernetesResource> previousResources = previousRollbackEligibleRelease.getResourcesWithSpecs();
    setNamespaceInResources(previousResources, rollbackHandlerConfig.getKubernetesConfig().getNamespace());

    Map<KubernetesResourceId, KubernetesResource> previousKubernetesResourceMap =
        previousResources.stream().collect(toMap(KubernetesResource::getResourceId, Function.identity()));

    List<KubernetesResource> prunedResourcesToBeRecreated = prunedResources.stream()
                                                                .filter(previousKubernetesResourceMap::containsKey)
                                                                .map(previousKubernetesResourceMap::get)
                                                                .collect(toList());

    if (isEmpty(prunedResourcesToBeRecreated)) {
      pruneLogCallback.saveExecutionLog("No resources are required to be recreated.");
      pruneLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return ResourceRecreationStatus.NO_RESOURCE_CREATED;
    }
    String commandFlags = K8sCommandFlagsUtils.getK8sCommandFlags(K8sCliCommandType.Apply.name(), k8sCommandFlag);
    return k8sTaskHelperBase.applyManifests(rollbackHandlerConfig.getClient(), prunedResourcesToBeRecreated,
               k8sDelegateTaskParams, pruneLogCallback, false, commandFlags)
        ? ResourceRecreationStatus.RESOURCE_CREATION_SUCCESSFUL
        : ResourceRecreationStatus.RESOURCE_CREATION_FAILED;
  }

  public void deleteNewResourcesForCurrentFailedRelease(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      Integer releaseNumber, LogCallback deleteLogCallback, K8sDelegateTaskParams k8sDelegateTaskParams) {
    try {
      IK8sReleaseHistory releaseHistory = rollbackHandlerConfig.getReleaseHistory();
      if (isEmpty(releaseHistory)) {
        deleteLogCallback.saveExecutionLog(
            "No release history available, No successful release available to compute newly created resources", INFO,
            SUCCESS);
        return;
      }

      int rollbackReleaseNumber = releaseNumber != null ? releaseNumber : 0;
      if (rollbackReleaseNumber == 0) { // RollingDeploy was aborted
        if (Succeeded == rollbackHandlerConfig.getRelease().getReleaseStatus()) {
          deleteLogCallback.saveExecutionLog("No failed release found. No need to delete resources.", INFO, RUNNING);
          deleteLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
          return;
        } else {
          // set releaseNumber to max int so that rollback to current successful one goes through.
          rollbackReleaseNumber = Integer.MAX_VALUE;
        }
      }

      IK8sRelease previousSuccessfulEligibleRelease = releaseHistory.getLastSuccessfulRelease(rollbackReleaseNumber);
      if (previousSuccessfulEligibleRelease == null) {
        deleteLogCallback.saveExecutionLog(
            "No successful previous release available to compute newly created resources", INFO, SUCCESS);
        return;
      }

      IK8sRelease release = rollbackHandlerConfig.getRelease();
      List<KubernetesResourceId> resourceToBeDeleted = getResourcesTobeDeletedInOrder(
          previousSuccessfulEligibleRelease, release, rollbackHandlerConfig.getKubernetesConfig().getNamespace());

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
      IK8sRelease previousSuccessfulEligibleRelease, IK8sRelease release, String namespace) {
    List<KubernetesResource> currentResources = release.getResourcesWithSpecs();
    List<KubernetesResource> previousResources = previousSuccessfulEligibleRelease.getResourcesWithSpecs();
    setNamespaceInResources(currentResources, namespace);
    setNamespaceInResources(previousResources, namespace);

    List<KubernetesResourceId> previousResourceIds =
        previousResources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());

    List<KubernetesResourceId> resourceToBeDeleted = currentResources.stream()
                                                         .filter(resource -> !resource.isSkipPruning())
                                                         .map(KubernetesResource::getResourceId)
                                                         .filter(resource -> !previousResourceIds.contains(resource))
                                                         .filter(resource -> !resource.isVersioned())
                                                         .collect(toList());
    return k8sTaskHelperBase.arrangeResourceIdsInDeletionOrder(resourceToBeDeleted);
  }

  private void setNamespaceInResources(List<KubernetesResource> resources, String namespace) {
    k8sTaskHelperBase.setNamespaceToKubernetesResourcesIfRequired(resources, namespace);
  }

  public enum ResourceRecreationStatus { NO_RESOURCE_CREATED, RESOURCE_CREATION_FAILED, RESOURCE_CREATION_SUCCESSFUL }
}
