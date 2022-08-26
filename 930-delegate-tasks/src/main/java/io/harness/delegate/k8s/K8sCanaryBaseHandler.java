/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.k8s.manifest.ManifestHelper.getWorkloadsForCanaryAndBG;
import static io.harness.k8s.model.Release.Status.Failed;
import static io.harness.k8s.model.Release.Status.InProgress;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.k8s.releasehistory.K8sReleaseHistoryService;
import io.harness.delegate.k8s.releasehistory.K8sReleaseService;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Secret;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sCanaryBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sReleaseHistoryService releaseHistoryService;
  @Inject private K8sReleaseService releaseService;

  public boolean prepareForCanary(K8sCanaryHandlerConfig canaryHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, Boolean skipVersioning, LogCallback logCallback,
      boolean isErrorFrameworkEnabled, boolean cleanUpIncompleteCanaryDeployRelease) throws Exception {
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

    if (cleanUpIncompleteCanaryDeployRelease) {
      for (V1Secret release : canaryHandlerConfig.getReleaseHistory()) {
        if (InProgress.name().equals(releaseService.getReleaseLabelValue(release, RELEASE_STATUS_LABEL_KEY))) {
          releaseHistoryService.markStatusAndSaveRelease(
              release, Failed.name(), canaryHandlerConfig.getKubernetesConfig());
        }
      }
    }

    int currentReleaseNumber = releaseService.getCurrentReleaseNumber(canaryHandlerConfig.getReleaseHistory());
    V1Secret currentRelease = releaseHistoryService.createRelease(
        canaryHandlerConfig.getReleaseName(), currentReleaseNumber, Release.Status.InProgress.name());
    releaseService.setResourcesInRelease(currentRelease, canaryHandlerConfig.getResources());

    canaryHandlerConfig.setRelease(currentRelease);
    canaryHandlerConfig.setCurrentReleaseNumber(currentReleaseNumber);

    logCallback.saveExecutionLog("\nCurrent release number is: " + currentReleaseNumber);

    KubernetesResource canaryWorkload = workloads.get(0);
    canaryHandlerConfig.setCanaryWorkload(canaryWorkload);

    releaseHistoryService.cleanReleaseHistory(canaryHandlerConfig.getKubernetesConfig(),
        canaryHandlerConfig.getReleaseName(), currentReleaseNumber, canaryHandlerConfig.getReleaseHistory());
    return true;
  }

  public Integer getCurrentInstances(K8sCanaryHandlerConfig canaryHandlerConfig,
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback logCallback) throws Exception {
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    Integer currentInstances = k8sTaskHelperBase.getCurrentReplicas(
        canaryHandlerConfig.getClient(), canaryWorkload.getResourceId(), k8sDelegateTaskParams);
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

  public void updateTargetInstances(
      K8sCanaryHandlerConfig canaryHandlerConfig, Integer targetInstances, LogCallback logCallback) {
    canaryHandlerConfig.setTargetInstances(targetInstances);
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    canaryWorkload.appendSuffixInName(K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME);
    canaryWorkload.addLabelsInPodSpec(ImmutableMap.of(HarnessLabels.releaseName, canaryHandlerConfig.getReleaseName(),
        HarnessLabels.track, HarnessLabelValues.trackCanary));
    canaryWorkload.addLabelsInDeploymentSelector(ImmutableMap.of(HarnessLabels.track, HarnessLabelValues.trackCanary));
    canaryWorkload.setReplicaCount(canaryHandlerConfig.getTargetInstances());

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
}
