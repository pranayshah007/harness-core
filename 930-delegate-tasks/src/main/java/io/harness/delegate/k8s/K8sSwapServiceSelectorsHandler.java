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
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
public class K8sSwapServiceSelectorsHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject private K8sSwapServiceSelectorsBaseHandler k8sSwapServiceSelectorsBaseHandler;

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
        k8sSwapServiceSelectorsRequest.getK8sInfraDelegateConfig(), logCallback);

    k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(kubernetesConfig,
        k8sSwapServiceSelectorsRequest.getService1(), k8sSwapServiceSelectorsRequest.getService2(), logCallback,
        isErrorFrameworkSupported());

    return K8sDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
  }
}
