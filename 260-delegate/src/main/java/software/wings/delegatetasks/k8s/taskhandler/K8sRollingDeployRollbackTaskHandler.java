/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.k8s.K8sCommandUnitConstants.DeleteFailedReleaseResources;
import static io.harness.k8s.K8sCommandUnitConstants.Init;
import static io.harness.k8s.K8sCommandUnitConstants.RecreatePrunedResource;
import static io.harness.k8s.K8sCommandUnitConstants.Rollback;
import static io.harness.k8s.K8sCommandUnitConstants.WaitForSteadyState;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static com.google.common.base.MoreObjects.firstNonNull;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.k8s.K8sRollingRollbackBaseHandler;
import io.harness.delegate.k8s.K8sRollingRollbackBaseHandler.ResourceRecreationStatus;
import io.harness.delegate.k8s.beans.K8sRollingRollbackHandlerConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployRollbackTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployRollbackResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sRollingDeployRollbackTaskHandler extends K8sTaskHandler {
  @Inject private transient ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private transient K8sTaskHelper k8sTaskHelper;
  @Inject private transient K8sRollingRollbackBaseHandler k8sRollingRollbackBaseHandler;

  private final K8sRollingRollbackHandlerConfig rollbackHandlerConfig = new K8sRollingRollbackHandlerConfig();
  private static final Integer DEFAULT_TIMEOUT_MINS = 10;

  @Override
  public K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8sTaskParameters, K8sDelegateTaskParams k8sDelegateTaskParams) throws Exception {
    if (!(k8sTaskParameters instanceof K8sRollingDeployRollbackTaskParameters)) {
      throw new InvalidArgumentsException(
          Pair.of("k8sTaskParameters", "Must be instance of K8sRollingDeployRollbackTaskParameters"));
    }

    K8sRollingDeployRollbackTaskParameters request = (K8sRollingDeployRollbackTaskParameters) k8sTaskParameters;
    Integer timeoutInMin = firstNonNull(request.getTimeoutIntervalInMin(), DEFAULT_TIMEOUT_MINS);
    ExecutionLogCallback initLogCallback = k8sTaskHelper.getExecutionLogCallback(request, Init);
    try {
      init(request, k8sDelegateTaskParams, initLogCallback);
    } catch (Exception e) {
      initLogCallback.saveExecutionLog(getMessage(e), ERROR, FAILURE);
      throw e;
    }

    ResourceRecreationStatus resourceRecreationStatus = ResourceRecreationStatus.NO_RESOURCE_CREATED;

    if (((K8sRollingDeployRollbackTaskParameters) k8sTaskParameters).isPruningEnabled()) {
      ExecutionLogCallback pruneLogCallback = k8sTaskHelper.getExecutionLogCallback(request, RecreatePrunedResource);
      resourceRecreationStatus = recreatePrunedResources(
          rollbackHandlerConfig, request.getPrunedResourcesIds(), pruneLogCallback, k8sDelegateTaskParams);
    }

    if (((K8sRollingDeployRollbackTaskParameters) k8sTaskParameters).isPruningEnabled()) {
      ExecutionLogCallback deleteLogCallback =
          k8sTaskHelper.getExecutionLogCallback(request, DeleteFailedReleaseResources);
      k8sRollingRollbackBaseHandler.deleteNewResourcesForCurrentFailedRelease(rollbackHandlerConfig,
          rollbackHandlerConfig.getCurrentReleaseNumber(), deleteLogCallback, k8sDelegateTaskParams);
    }

    boolean success = k8sRollingRollbackBaseHandler.rollback(rollbackHandlerConfig, k8sDelegateTaskParams,
        rollbackHandlerConfig.getCurrentReleaseNumber(), k8sTaskHelper.getExecutionLogCallback(request, Rollback),
        k8sRollingRollbackBaseHandler.getResourcesRecreated(request.getPrunedResourcesIds(), resourceRecreationStatus),
        false, null);
    if (!success) {
      return K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
    }

    ExecutionLogCallback waitForSteadyStateLogCallback =
        k8sTaskHelper.getExecutionLogCallback(request, WaitForSteadyState);
    try {
      k8sRollingRollbackBaseHandler.steadyStateCheck(
          rollbackHandlerConfig, k8sDelegateTaskParams, timeoutInMin, waitForSteadyStateLogCallback);

      List<K8sPod> pods =
          k8sRollingRollbackBaseHandler.getPods(timeoutInMin, rollbackHandlerConfig, request.getReleaseName());

      K8sRollingDeployRollbackResponse rollbackResponse =
          K8sRollingDeployRollbackResponse.builder().k8sPodList(pods).build();
      k8sRollingRollbackBaseHandler.postProcess(rollbackHandlerConfig, request.getReleaseName());
      waitForSteadyStateLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
      return K8sTaskExecutionResponse.builder()
          .k8sTaskResponse(rollbackResponse)
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      waitForSteadyStateLogCallback.saveExecutionLog(getMessage(e), ERROR, FAILURE);
      throw e;
    }
  }

  private void init(K8sRollingDeployRollbackTaskParameters k8sRollingDeployRollbackTaskParameters,
      K8sDelegateTaskParams k8sDelegateTaskParams, ExecutionLogCallback executionLogCallback) throws Exception {
    executionLogCallback.saveExecutionLog("Initializing..\n");

    rollbackHandlerConfig.setKubernetesConfig(containerDeploymentDelegateHelper.getKubernetesConfig(
        k8sRollingDeployRollbackTaskParameters.getK8sClusterConfig(), false));
    rollbackHandlerConfig.setClient(KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory()));
    rollbackHandlerConfig.setUseDeclarativeRollback(k8sRollingDeployRollbackTaskParameters.isUseDeclarativeRollback());
    rollbackHandlerConfig.setK8sDelegateTaskParams(k8sDelegateTaskParams);
    rollbackHandlerConfig.setCurrentReleaseNumber(k8sRollingDeployRollbackTaskParameters.getReleaseNumber());

    k8sRollingRollbackBaseHandler.init(
        rollbackHandlerConfig, k8sRollingDeployRollbackTaskParameters.getReleaseName(), executionLogCallback);
  }

  private ResourceRecreationStatus recreatePrunedResources(K8sRollingRollbackHandlerConfig rollbackHandlerConfig,
      List<KubernetesResourceId> prunedResourcesIds, ExecutionLogCallback pruneLogCallback,
      K8sDelegateTaskParams k8sDelegateTaskParams) {
    ResourceRecreationStatus resourceRecreationStatus = ResourceRecreationStatus.NO_RESOURCE_CREATED;
    try {
      if (rollbackHandlerConfig.isUseDeclarativeRollback()) {
        pruneLogCallback.saveExecutionLog(
            "Declarative rollback is enabled. Pruned resources will be recreated when applying older manifests during rollback.",
            INFO, SUCCESS);
        return resourceRecreationStatus;
      }
      resourceRecreationStatus = k8sRollingRollbackBaseHandler.recreatePrunedResources(rollbackHandlerConfig,
          rollbackHandlerConfig.getCurrentReleaseNumber(), prunedResourcesIds, pruneLogCallback, k8sDelegateTaskParams,
          null);
      k8sRollingRollbackBaseHandler.logResourceRecreationStatus(resourceRecreationStatus, pruneLogCallback);
    } catch (Exception ex) {
      resourceRecreationStatus = ResourceRecreationStatus.RESOURCE_CREATION_FAILED;
      pruneLogCallback.saveExecutionLog("Failed to recreate pruned resources.", WARN, RUNNING);
      pruneLogCallback.saveExecutionLog(getMessage(ex), WARN, SUCCESS);
    }

    return resourceRecreationStatus;
  }

  @VisibleForTesting
  K8sRollingRollbackHandlerConfig getRollbackHandlerConfig() {
    return rollbackHandlerConfig;
  }
}
