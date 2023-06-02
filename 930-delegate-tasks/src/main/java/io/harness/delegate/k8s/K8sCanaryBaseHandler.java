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
import static io.harness.k8s.manifest.ManifestHelper.getWorkloadsForCanaryAndBG;
import static io.harness.k8s.manifest.VersionUtils.addSuffixToConfigmapsAndSecrets;
import static io.harness.k8s.manifest.VersionUtils.markVersionedResources;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.InProgress;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.VersionUtils;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sRequestHandlerContext;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleaseHistoryCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sCanaryBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  public boolean prepareForCanary(K8sCanaryHandlerConfig canaryHandlerConfig,
      K8sRequestHandlerContext k8sRequestHandlerContext, K8sDelegateTaskParams k8sDelegateTaskParams,
      Boolean skipVersioning, LogCallback logCallback, boolean isErrorFrameworkEnabled) throws Exception {
    boolean useDeclarativeRollback = canaryHandlerConfig.isUseDeclarativeRollback();
    if (isNotTrue(skipVersioning) && !useDeclarativeRollback) {
      markVersionedResources(canaryHandlerConfig.getResources());
    }

    logCallback.saveExecutionLog("Manifests processed. Found following resources: \n"
        + k8sTaskHelperBase.getResourcesInTableFormat(canaryHandlerConfig.getResources()));

    List<KubernetesResource> workloads = getWorkloadsForCanaryAndBG(canaryHandlerConfig.getResources());

    if (workloads.size() != 1) {
      if (workloads.isEmpty()) {
        logCallback.saveExecutionLog("\n" + KubernetesExceptionExplanation.CANARY_NO_WORKLOADS_FOUND, ERROR, FAILURE);
        if (isErrorFrameworkEnabled) {
          throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.CANARY_NO_WORKLOADS_FOUND,
              KubernetesExceptionExplanation.CANARY_NO_WORKLOADS_FOUND,
              new KubernetesTaskException(KubernetesExceptionMessages.NO_WORKLOADS_FOUND));
        }
      } else {
        logCallback.saveExecutionLog(
            "\nMore than one workloads found in the Manifests. Canary deploy supports only one workload. Others should be marked with annotation "
                + HarnessAnnotations.directApply + ": true",
            ERROR, FAILURE);

        if (isErrorFrameworkEnabled) {
          String workloadsPrintableList = workloads.stream()
                                              .map(KubernetesResource::getResourceId)
                                              .map(KubernetesResourceId::kindNameRef)
                                              .collect(Collectors.joining(", "));
          throw NestedExceptionUtils.hintWithExplanationException(KubernetesExceptionHints.CANARY_MULTIPLE_WORKLOADS,
              format(
                  KubernetesExceptionExplanation.CANARY_MULTIPLE_WORKLOADS, workloads.size(), workloadsPrintableList),
              new KubernetesTaskException(KubernetesExceptionMessages.MULTIPLE_WORKLOADS));
        }
      }
      return false;
    }

    failInProgressReleases(canaryHandlerConfig);

    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    int currentReleaseNumber = canaryHandlerConfig.getCurrentReleaseNumber();
    logCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

    if (isNotTrue(skipVersioning) && !useDeclarativeRollback) {
      logCallback.saveExecutionLog("\nVersioning resources.");
      k8sTaskHelperBase.addRevisionNumber(k8sRequestHandlerContext, currentReleaseNumber);
    }

    if (useDeclarativeRollback) {
      logCallback.saveExecutionLog(format("Adding canary suffix [%s] to Configmap and Secret names.",
          K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR));
      addSuffixToConfigmapsAndSecrets(k8sRequestHandlerContext, K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME, logCallback);
    }

    KubernetesResource canaryWorkload = workloads.get(0);
    canaryHandlerConfig.setCanaryWorkload(canaryWorkload);

    K8sReleaseHistoryCleanupDTO releaseCleanupDTO = k8sTaskHelperBase.createReleaseHistoryCleanupRequest(
        canaryHandlerConfig.getReleaseName(), canaryHandlerConfig.getReleaseHistory(), canaryHandlerConfig.getClient(),
        canaryHandlerConfig.getKubernetesConfig(), logCallback, currentReleaseNumber, k8sDelegateTaskParams);
    releaseHandler.cleanReleaseHistory(releaseCleanupDTO);

    return true;
  }

  private void failInProgressReleases(K8sCanaryHandlerConfig canaryHandlerConfig) throws Exception {
    if (!canaryHandlerConfig.isUseDeclarativeRollback()) {
      failInProgressLegacyReleases((K8SLegacyReleaseHistory) canaryHandlerConfig.getReleaseHistory());
    } else {
      failInProgressReleases(
          (K8sReleaseHistory) canaryHandlerConfig.getReleaseHistory(), canaryHandlerConfig.getKubernetesConfig());
    }
  }

  private void failInProgressReleases(K8sReleaseHistory releaseHistory, KubernetesConfig kubernetesConfig)
      throws Exception {
    for (K8sRelease release : releaseHistory.getReleaseHistory()) {
      if (InProgress == release.getReleaseStatus()) {
        release.updateReleaseStatus(Failed);

        K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(true);
        K8sReleasePersistDTO persistDTO =
            K8sReleasePersistDTO.builder().kubernetesConfig(kubernetesConfig).release(release).build();
        releaseHandler.saveRelease(persistDTO);
      }
    }
  }

  private void failInProgressLegacyReleases(K8SLegacyReleaseHistory releaseHistory) {
    for (K8sLegacyRelease release : releaseHistory.getReleaseHistory().getReleases()) {
      if (InProgress == release.getReleaseStatus()) {
        release.setStatus(Failed);
      }
    }
  }

  public Integer getCurrentInstances(K8sCanaryHandlerConfig canaryHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback) throws Exception {
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    Integer currentInstances = k8sTaskHelperBase.getCurrentReplicas(
        canaryHandlerConfig.getClient(), canaryWorkload.getResourceId(), k8sDelegateTaskParams, logCallback);
    if (currentInstances != null) {
      logCallback.saveExecutionLog("\nCurrent replica count is " + currentInstances);
    }

    if (currentInstances == null) {
      currentInstances = canaryWorkload.getReplicaCount();
    }

    if (currentInstances == null) {
      currentInstances = 1;
    }

    return currentInstances;
  }

  public void updateTargetInstances(K8sCanaryHandlerConfig canaryHandlerConfig,
      K8sRequestHandlerContext k8sRequestHandlerContext, Integer targetInstances, LogCallback logCallback) {
    canaryHandlerConfig.setTargetInstances(targetInstances);
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    canaryWorkload.appendSuffixInName(
        K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR, k8sRequestHandlerContext);
    canaryWorkload.addLabelsInPodSpec(ImmutableMap.of(HarnessLabels.releaseName, canaryHandlerConfig.getReleaseName(),
        HarnessLabels.track, HarnessLabelValues.trackCanary));
    canaryWorkload.addLabelsInResourceSelector(
        ImmutableMap.of(HarnessLabels.track, HarnessLabelValues.trackCanary), k8sRequestHandlerContext);
    canaryWorkload.setReplicaCount(canaryHandlerConfig.getTargetInstances());

    // do the name update for all the resources (HPA and PDB)
    if (k8sRequestHandlerContext.isEnabledSupportHPAAndPDB()
        && EmptyPredicate.isNotEmpty(k8sRequestHandlerContext.getResourcesForNameUpdate())) {
      k8sRequestHandlerContext.getResourcesForNameUpdate().forEach(
          resource -> { resource.appendSuffixInName(K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR, null); });
    }

    logCallback.saveExecutionLog(
        "\nCanary Workload is: " + color(canaryWorkload.getResourceId().kindNameRef(), LogColor.Cyan, Bold));
    logCallback.saveExecutionLog("\nTarget replica count for Canary is " + canaryHandlerConfig.getTargetInstances());
  }

  public void updateDestinationRuleManifestFilesWithSubsets(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback logCallback) throws IOException {
    k8sTaskHelperBase.updateDestinationRuleManifestFilesWithSubsets(resources,
        asList(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable), kubernetesConfig, logCallback);
  }

  public void updateVirtualServiceManifestFilesWithRoutes(List<KubernetesResource> resources,
      KubernetesConfig kubernetesConfig, LogCallback logCallback) throws IOException {
    k8sTaskHelperBase.updateVirtualServiceManifestFilesWithRoutesForCanary(resources, kubernetesConfig, logCallback);
  }

  public List<K8sPod> getAllPods(K8sCanaryHandlerConfig canaryHandlerConfig, String releaseName, long timeoutInMillis)
      throws Exception {
    String namespace = canaryHandlerConfig.getCanaryWorkload().getResourceId().getNamespace();
    List<K8sPod> allPods = k8sTaskHelperBase.getPodDetails(
        canaryHandlerConfig.getKubernetesConfig(), namespace, releaseName, timeoutInMillis);
    List<K8sPod> canaryPods = k8sTaskHelperBase.getPodDetailsWithTrack(
        canaryHandlerConfig.getKubernetesConfig(), namespace, releaseName, "canary", timeoutInMillis);
    Set<String> canaryPodNames = canaryPods.stream().map(K8sPod::getName).collect(Collectors.toSet());
    allPods.forEach(pod -> {
      if (canaryPodNames.contains(pod.getName())) {
        pod.setNewPod(true);
      }
    });
    return allPods;
  }

  public void wrapUp(Kubectl client, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog("Wrapping up..\n");

    k8sTaskHelperBase.describe(client, k8sDelegateTaskParams, logCallback);
  }

  public void failAndSaveRelease(K8sCanaryHandlerConfig canaryHandlerConfig) throws Exception {
    canaryHandlerConfig.getCurrentRelease().updateReleaseStatus(Failed);

    k8sTaskHelperBase.saveRelease(canaryHandlerConfig.isUseDeclarativeRollback(), false,
        canaryHandlerConfig.getKubernetesConfig(), canaryHandlerConfig.getCurrentRelease(),
        canaryHandlerConfig.getReleaseHistory(), canaryHandlerConfig.getReleaseName());
  }

  public String appendSecretAndConfigMapNamesToCanaryWorkloads(
      String canaryWorkloadName, List<KubernetesResource> resources) {
    return appendResourceNamesToCanaryWorkloads(canaryWorkloadName, resources, VersionUtils::shouldVersion);
  }

  public String appendHPAAndPDBNamesToCanaryWorkloads(String canaryWorkloadName, List<KubernetesResource> resources) {
    return appendResourceNamesToCanaryWorkloads(canaryWorkloadName, resources,
        resource
        -> ImmutableSet.of(Kind.HorizontalPodAutoscaler.name(), Kind.PodDisruptionBudget.name())
               .contains(resource.getResourceId().getKind()));
  }

  public String appendResourceNamesToCanaryWorkloads(
      String canaryWorkloadName, List<KubernetesResource> resources, Predicate<KubernetesResource> filter) {
    if (isEmpty(resources)) {
      return canaryWorkloadName;
    }

    StringBuilder canaryWorkloadNameBuilder = new StringBuilder(canaryWorkloadName);

    String resourceNames = resources.stream()
                               .filter(filter)
                               .map(KubernetesResource::getResourceId)
                               .map(KubernetesResourceId::namespaceKindNameRef)
                               .collect(Collectors.joining(","));

    if (isNotEmpty(resourceNames)) {
      canaryWorkloadNameBuilder.append(',');
      canaryWorkloadNameBuilder.append(resourceNames);
    }
    return canaryWorkloadNameBuilder.toString();
  }
}
