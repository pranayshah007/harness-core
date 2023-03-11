/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus.NO_RESOURCE_CREATED;
import static io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus.RESOURCE_CREATION_FAILED;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.k8s.K8sCommandUnitConstants.DeleteFailedReleaseResources;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.RecreatePrunedResource;
import static io.harness.k8s.K8sCommandUnitConstants.Rollback;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRollbackResponse;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@NoArgsConstructor
@Slf4j
public class K8sRollingRollbackRequestHandler extends K8sRequestHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Inject K8sRollingRollbackBaseHandler rollbackBaseHandler;

  private final K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();

  @Override
  protected K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8sDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(k8sDeployRequest instanceof K8sRollingRollbackDeployRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sDeployRequest", "Must be instance of K8sRollingRollbackDeployRequest"));
    }

    K8sRollingRollbackDeployRequest k8sRollingRollbackDeployRequest =
        (K8sRollingRollbackDeployRequest) k8sDeployRequest;
    LogCallback initLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Init, true, commandUnitsProgress);
    Map<String, String> k8sCommandFlag = k8sRollingRollbackDeployRequest.getK8sCommandFlags();
    init(k8sRollingRollbackDeployRequest, k8sDelegateTaskParams, initLogCallback);

    ResourceRecreationStatus resourceRecreationStatus = NO_RESOURCE_CREATED;
    if (k8sRollingRollbackDeployRequest.isPruningEnabled()) {
      resourceRecreationStatus =
          recreatePrunedResources(rollbackHandlerConfig, rollbackHandlerConfig.getCurrentReleaseNumber(),
              k8sRollingRollbackDeployRequest.getPrunedResourceIds(), k8sDelegateTaskParams, logStreamingTaskClient,
              commandUnitsProgress, k8sRollingRollbackDeployRequest.isUseDeclarativeRollback(), k8sCommandFlag);

      LogCallback deleteResourcesLogCallback = k8sTaskHelperBase.getLogCallback(
          logStreamingTaskClient, DeleteFailedReleaseResources, true, commandUnitsProgress);
      rollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(rollbackHandlerConfig,
          rollbackHandlerConfig.getCurrentReleaseNumber(), deleteResourcesLogCallback, k8sDelegateTaskParams);
    }
    Set<KubernetesResourceId> recreatedResourceIds = rollbackBaseHandler.getResourcesRecreated(
        k8sRollingRollbackDeployRequest.getPrunedResourceIds(), resourceRecreationStatus);
    rollbackBaseHandler.rollback(rollbackHandlerConfig, k8sDelegateTaskParams,
        rollbackHandlerConfig.getCurrentReleaseNumber(),
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, Rollback, true, commandUnitsProgress),
        recreatedResourceIds, true, k8sCommandFlag);

    LogCallback waitForSteadyStateLogCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, WaitForSteadyState, true, commandUnitsProgress);

    rollbackBaseHandler.steadyStateCheck(rollbackHandlerConfig, k8sDelegateTaskParams,
        k8sRollingRollbackDeployRequest.getTimeoutIntervalInMin(), waitForSteadyStateLogCallback);
    rollbackBaseHandler.postProcess(rollbackHandlerConfig, k8sRollingRollbackDeployRequest.getReleaseName());
    K8sRollingDeployRollbackResponse response =
        K8sRollingDeployRollbackResponse.builder()
            .k8sPodList(rollbackBaseHandler.getPods(k8sRollingRollbackDeployRequest.getTimeoutIntervalInMin(),
                rollbackHandlerConfig, k8sRollingRollbackDeployRequest.getReleaseName()))
            .recreatedResourceIds(recreatedResourceIds)
            .build();

    waitForSteadyStateLogCallback.saveExecutionLog("\nDone.", INFO, SUCCESS);
    return K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).k8sNGTaskResponse(response).build();
  }

  @Override
  public boolean isErrorFrameworkSupported() {
    return true;
  }

  private void init(K8sRollingRollbackDeployRequest rollbackRequest, K8sDelegateTaskParams k8sDelegateTaskParams,
      LogCallback logCallback) throws Exception {
    logCallback.saveExecutionLog("Initializing..\n");
    logCallback.saveExecutionLog(
        color(String.format("Release Name: [%s]", rollbackRequest.getReleaseName()), Yellow, Bold));

    rollbackHandlerConfig.setKubernetesConfig(containerDeploymentDelegateBaseHelper.createKubernetesConfig(
        rollbackRequest.getK8sInfraDelegateConfig(), logCallback));
    rollbackHandlerConfig.setClient(
        Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath()));
    rollbackHandlerConfig.setUseDeclarativeRollback(rollbackRequest.isUseDeclarativeRollback());
    rollbackHandlerConfig.setCurrentReleaseNumber(rollbackRequest.getReleaseNumber());
    rollbackHandlerConfig.setK8sDelegateTaskParams(k8sDelegateTaskParams);

    rollbackBaseHandler.init(rollbackHandlerConfig, rollbackRequest.getReleaseName(), logCallback);
  }

  @VisibleForTesting
  K8sRollingRollbackHandlerConfig getRollbackHandlerConfig() {
    return rollbackHandlerConfig;
  }

  private ResourceRecreationStatus recreatePrunedResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      Integer releaseNumber, List<KubernetesResourceId> prunedResources, K8sDelegateTaskParams k8sDelegateTaskParams,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      boolean useDeclarativeRollback, Map<String, String> commandFlags) {
    LogCallback recreateResourcesCallback =
        k8sTaskHelperBase.getLogCallback(logStreamingTaskClient, RecreatePrunedResource, true, commandUnitsProgress);

    ResourceRecreationStatus resourceRecreationStatus = NO_RESOURCE_CREATED;

    if (useDeclarativeRollback) {
      recreateResourcesCallback.saveExecutionLog(
          "Declarative rollback is enabled. Pruned resources will be recreated when applying older manifests during rollback.",
          INFO, SUCCESS);
      return resourceRecreationStatus;
    }

    try {
      resourceRecreationStatus = rollbackBaseHandler.recreatePrunedResources(rollbackHandlerConfig, releaseNumber,
          prunedResources, recreateResourcesCallback, k8sDelegateTaskParams, commandFlags);
      rollbackBaseHandler.logResourceRecreationStatus(resourceRecreationStatus, recreateResourcesCallback);
    } catch (Exception e) {
      resourceRecreationStatus = RESOURCE_CREATION_FAILED;
      recreateResourcesCallback.saveExecutionLog("Failed to recreate pruned resources.", WARN, RUNNING);
      recreateResourcesCallback.saveExecutionLog(getMessage(e), WARN, SUCCESS);
    }
    return resourceRecreationStatus;
  }
}
