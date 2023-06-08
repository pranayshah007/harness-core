/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.Scale;
import static io.harness.k8s.model.HarnessLabelValues.bgStageEnv;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.BLUE_GREEN_COLORS;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sBlueGreenStageScaleDownRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sBlueGreenStageScaleDownRequestHandler extends K8sRequestHandler {
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private K8sBGBaseHandler k8sBGBaseHandler;
  private Kubectl client;
  private K8sReleaseHandler releaseHandler;
  private List<KubernetesResourceId> resourceIdsToScale;

  private static final Set<String> WORKLOAD_KINDS = ImmutableSet.of(
      "Deployment", "StatefulSet", "DaemonSet", "DeploymentConfig", "HorizontalPodAutoscaler", "PodDisruptionBudget");
  private static final Set<String> SCALE_DOWN_WORKLOAD_KINDS =
      ImmutableSet.of("Deployment", "StatefulSet", "DaemonSet", "DeploymentConfig");
  private static final Set<String> DELETE_WORKLOAD_KINDS =
      ImmutableSet.of("HorizontalPodAutoscaler", "PodDisruptionBudget");
  private static final int TARGET_REPLICA_COUNT = 0;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sBlueGreenStageScaleDownRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sDeployRequest", "Must be instance of K8sBlueGreenStageScaleDownRequest"));
    }
    K8sBlueGreenStageScaleDownRequest k8sBlueGreenStageScaleDownRequest =
        (K8sBlueGreenStageScaleDownRequest) k8sDeployRequest;
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(k8sBlueGreenStageScaleDownRequest.isUseDeclarativeRollback());
    LogCallback logCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress);

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        k8sBlueGreenStageScaleDownRequest.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(),
        logCallback);
    init(k8sBlueGreenStageScaleDownRequest, k8sDelegateTaskParams, kubernetesConfig, logCallback);

    LogCallback scaleLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Scale, true, commandUnitsProgress);
    if (isNotEmpty(resourceIdsToScale)) {
      stageScaleDown(k8sDelegateTaskParams, scaleLogCallback);
    } else {
      scaleLogCallback.saveExecutionLog("\nSkipping the Blue Green Stage Scale Down Step", INFO, SUCCESS);
    }
    return K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  @VisibleForTesting
  void init(K8sBlueGreenStageScaleDownRequest request, K8sDelegateTaskParams k8sDelegateTaskParams,
      KubernetesConfig kubernetesConfig, LogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");
    executionLogCallback.saveExecutionLog(
        color(String.format("Release Name: [%s]", request.getReleaseName()), Yellow, Bold));
    client = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
    IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, request.getReleaseName());

    resourceIdsToScale = getResourceIdsToScaleDownStageEnvironment(
        releaseHistory.getLatestSuccessfulBlueGreenRelease(), k8sDelegateTaskParams, executionLogCallback);
    if (isNotEmpty(resourceIdsToScale)) {
      executionLogCallback.saveExecutionLog(
          "Found following resources from stage release which are eligible for scale down: \n"
          + k8sTaskHelperBase.getResourcesIdsInTableFormat(resourceIdsToScale));
    }
    executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
  }

  void stageScaleDown(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    deleteStageEnvironmentResources(k8sDelegateTaskParams, executionLogCallback);
    scaleDownStageEnvironmentResources(k8sDelegateTaskParams, executionLogCallback);
    executionLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
  }

  private void deleteStageEnvironmentResources(
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    List<KubernetesResourceId> resourcesToDelete =
        resourceIdsToScale.stream()
            .filter(resourceId -> DELETE_WORKLOAD_KINDS.contains(resourceId.getKind()))
            .collect(Collectors.toList());
    List<KubernetesResourceId> deletedResources = k8sTaskHelperBase.executeDeleteHandlingPartialExecution(
        client, k8sDelegateTaskParams, resourcesToDelete, executionLogCallback, false);
    if (isNotEmpty(deletedResources)) {
      executionLogCallback.saveExecutionLog("Successfully deleted the following resources: \n"
          + k8sTaskHelperBase.getResourcesIdsInTableFormat(deletedResources));
    }
  }

  private void scaleDownStageEnvironmentResources(
      K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) throws Exception {
    for (KubernetesResourceId resourceId : resourceIdsToScale) {
      if (SCALE_DOWN_WORKLOAD_KINDS.contains(resourceId.getKind())) {
        k8sTaskHelperBase.scale(
            client, k8sDelegateTaskParams, resourceId, TARGET_REPLICA_COUNT, executionLogCallback, true);
      }
    }
  }

  private List<KubernetesResourceId> getResourceIdsToScaleDownStageEnvironment(
      IK8sRelease release, K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback) {
    if (release == null) {
      executionLogCallback.saveExecutionLog("\nNo Stage environment found to scale down", INFO);
      return Collections.emptyList();
    }
    if (bgStageEnv.equals(release.getBgEnvironment())) {
      executionLogCallback.saveExecutionLog(
          "\nSkipping scaling down the stage environment as no primary deployment found", INFO);
      return Collections.emptyList();
    }
    String stageColor = getStageColor(release);
    if (isEmpty(stageColor)) {
      executionLogCallback.saveExecutionLog(
          "\nSkipping scaling down the stage environment as the release has invalid BG color", INFO);
      return Collections.emptyList();
    }
    String primaryColor = k8sBGBaseHandler.getInverseColor(stageColor);
    String regex = primaryColor + "$";
    return release.getResourceIds()
        .stream()
        .filter(k8sResourceId
            -> WORKLOAD_KINDS.contains(k8sResourceId.getKind()) && k8sResourceId.getName().endsWith(primaryColor))
        .peek(k8sResourceId -> k8sResourceId.setName(k8sResourceId.getName().replaceAll(regex, stageColor)))
        .filter(k8sResourceId
            -> k8sTaskHelperBase.checkIfResourceContainsHarnessDirectApplyAnnotation(
                client, k8sDelegateTaskParams, k8sResourceId, executionLogCallback))
        .distinct()
        .collect(Collectors.toList());
  }

  private String getStageColor(IK8sRelease release) {
    if (release instanceof K8sRelease) {
      return k8sBGBaseHandler.getInverseColor(release.getReleaseColor());
    }
    if (release instanceof K8sLegacyRelease) {
      String managedWorkloadName = ((K8sLegacyRelease) release).getManagedWorkload().getName();
      String color = managedWorkloadName.substring(managedWorkloadName.lastIndexOf('-') + 1);
      if (isNotEmpty(color) && BLUE_GREEN_COLORS.contains(color)) {
        return k8sBGBaseHandler.getInverseColor(color);
      }
      return StringUtils.EMPTY;
    }
    throw new InvalidArgumentsException(String.format(
        "Invalid subclass %s provided for %s", release.getClass().getSimpleName(), IK8sRelease.class.getSimpleName()));
  }
}
