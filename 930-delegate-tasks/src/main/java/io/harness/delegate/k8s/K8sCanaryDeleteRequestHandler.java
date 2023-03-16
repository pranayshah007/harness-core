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
import static io.harness.delegate.task.k8s.K8sTaskHelperBase.getResourcesInStringFormat;
import static io.harness.k8s.K8sCommandUnitConstants.Delete;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.InProgress;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Gray;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sCanaryDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class K8sCanaryDeleteRequestHandler extends K8sRequestHandler {
  @Inject K8sTaskHelperBase k8sTaskHelperBase;
  @Inject K8sDeleteBaseHandler k8sDeleteBaseHandler;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private Kubectl client;
  private KubernetesConfig kubernetesConfig;
  private List<KubernetesResourceId> resourceIdsToDelete;

  private K8sReleaseHandler releaseHandler;

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8SDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("k8sDeployRequest", "Must be instance of K8sCanaryDeleteRequest"));
    }

    K8sCanaryDeleteRequest canaryDeleteRequest = (K8sCanaryDeleteRequest) k8sDeployRequest;
    releaseHandler = k8sTaskHelperBase.getReleaseHandler(canaryDeleteRequest.isUseDeclarativeRollback());
    LogCallback initLogCallBack =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress);
    init(canaryDeleteRequest, k8SDelegateTaskParams, initLogCallBack);

    LogCallback deleteLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Delete, true, commandUnitsProgress);
    if (isEmpty(resourceIdsToDelete)) {
      deleteLogCallback.saveExecutionLog(color("No canary workloads to be deleted. Skipping.\n", White, Bold));
      deleteLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
      return K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).build();
    }

    deleteLogCallback.saveExecutionLog("Deleting canary workloads.\n");
    k8sTaskHelperBase.delete(client, k8SDelegateTaskParams, resourceIdsToDelete, deleteLogCallback, true);

    return K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).build();
  }

  private void init(K8sCanaryDeleteRequest request, K8sDelegateTaskParams delegateParams, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog("Initializing...\n");

    client = Kubectl.client(delegateParams.getKubectlPath(), delegateParams.getKubeconfigPath());
    kubernetesConfig =
        containerDeploymentDelegateBaseHelper.createKubernetesConfig(request.getK8sInfraDelegateConfig(), logCallback);

    if (isEmpty(request.getCanaryWorkloads())) {
      resourceIdsToDelete = getCanaryResourceIdsFromReleaseHistory(request.getReleaseName(), logCallback);
    } else {
      resourceIdsToDelete = k8sDeleteBaseHandler.getResourceNameResourceIdsToDelete(request.getCanaryWorkloads());
    }

    if (isNotEmpty(resourceIdsToDelete)) {
      logCallback.saveExecutionLog(color("\nResources to delete are: ", White, Bold)
          + color(getResourcesInStringFormat(resourceIdsToDelete), Gray));
    }

    logCallback.saveExecutionLog("\n\nDone.", INFO, SUCCESS);
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  private List<KubernetesResourceId> getCanaryResourceIdsFromReleaseHistory(String releaseName, LogCallback logCallback)
      throws Exception {
    logCallback.saveExecutionLog(format("Getting canary workloads from release %s\n", releaseName));
    IK8sReleaseHistory releaseHistory = releaseHandler.getReleaseHistory(kubernetesConfig, releaseName);

    if (isEmpty(releaseHistory)) {
      logCallback.saveExecutionLog(format("Empty or missing release history for release %s", releaseName), WARN);
      return Collections.emptyList();
    }

    // In canary deployment we're leaving current release as in progress (it will be completed by rolling deployment).
    // Since we may catch some interrupted exceptions during task abortions it may happen that we will fail the canary
    // release. To ensure that the latest release is actually a canary release we have a more deep logic
    // in K8s Canary Delete Step (we will rely on release history only when we queued K8s Canary Task and step expire)
    IK8sRelease release = releaseHistory.getLatestRelease();
    if (InProgress != release.getReleaseStatus() && Failed != release.getReleaseStatus()) {
      logCallback.saveExecutionLog(
          format("Unable to identify any canary deployments for release %s.", releaseName), WARN);
      return Collections.emptyList();
    }

    return release.getResourceIds()
        .stream()
        .filter(resource -> resource.getName().endsWith(K8sConstants.CANARY_WORKLOAD_SUFFIX_NAME_WITH_SEPARATOR))
        .collect(Collectors.toList());
  }
}
