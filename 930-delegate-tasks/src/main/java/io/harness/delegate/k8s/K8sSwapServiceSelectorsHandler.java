/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sCommandUnitConstants.SwapServiceSelectors;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sSwapServiceSelectorsRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
public class K8sSwapServiceSelectorsHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sSwapServiceSelectorsBaseHandler k8sSwapServiceSelectorsBaseHandler;
  @Inject private K8sBGBaseHandler k8sBGBaseHandler;
  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sSwapServiceSelectorsRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sSwapServiceSelectorsRequest", "Must be instance of K8sSwapServiceSelectorsRequest"));
    }

    K8sSwapServiceSelectorsRequest k8sSwapServiceSelectorsRequest = (K8sSwapServiceSelectorsRequest) k8sDeployRequest;
    LogCallback logCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, SwapServiceSelectors, true, commandUnitsProgress);

    KubernetesConfig kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        k8sSwapServiceSelectorsRequest.getK8sInfraDelegateConfig(), k8sDelegateTaskParams.getWorkingDirectory(),
        logCallback);

    boolean useDeclarativeRollback = k8sSwapServiceSelectorsRequest.isUseDeclarativeRollback();
    K8sReleaseHandler releaseHandler = k8sTaskHelperBase.getReleaseHandler(useDeclarativeRollback);
    IK8sReleaseHistory releaseHistory =
        releaseHandler.getReleaseHistory(kubernetesConfig, k8sSwapServiceSelectorsRequest.getReleaseName());
    String primaryColor = k8sSwapServiceSelectorsBaseHandler.getColorOfService(
        kubernetesConfig, k8sSwapServiceSelectorsRequest.getService1());
    k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(kubernetesConfig,
        k8sSwapServiceSelectorsRequest.getService1(), k8sSwapServiceSelectorsRequest.getService2(), logCallback,
        isErrorFrameworkSupported());
    String stageColor = k8sBGBaseHandler.getInverseColor(primaryColor);
    IK8sRelease primaryRelease = releaseHistory.getLatestSuccessfulReleaseMatchingColor(primaryColor);
    IK8sRelease stageRelease = releaseHistory.getLatestSuccessfulReleaseMatchingColor(stageColor);
    k8sSwapServiceSelectorsBaseHandler.updateReleaseHistory(primaryRelease, stageRelease);
    IK8sRelease release = (stageRelease != null) ? stageRelease : primaryRelease;
    if (!useDeclarativeRollback) {
      k8sTaskHelperBase.saveRelease(
          false, false, kubernetesConfig, release, releaseHistory, k8sSwapServiceSelectorsRequest.getReleaseName());
    } else {
      if (stageRelease != null) {
        k8sTaskHelperBase.saveRelease(true, false, kubernetesConfig, stageRelease, releaseHistory,
            k8sSwapServiceSelectorsRequest.getReleaseName());
      }
      if (primaryRelease != null) {
        k8sTaskHelperBase.saveRelease(true, false, kubernetesConfig, primaryRelease, releaseHistory,
            k8sSwapServiceSelectorsRequest.getReleaseName());
      }
    }
    return K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }
}
