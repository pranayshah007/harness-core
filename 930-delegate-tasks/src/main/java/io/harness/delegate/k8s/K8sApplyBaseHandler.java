/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.k8s.manifest.ManifestHelper.getCustomResourceDefinitionWorkloads;
import static io.harness.k8s.manifest.ManifestHelper.getEligibleWorkloads;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class K8sApplyBaseHandler {
  @Inject private K8sTaskHelperBase k8sTaskHelperBase;

  public void wrapUp(K8sDelegateTaskParams k8sDelegateTaskParams, LogCallback executionLogCallback, Kubectl client)
      throws Exception {
    executionLogCallback.saveExecutionLog("Wrapping up..\n");

    k8sTaskHelperBase.describe(client, k8sDelegateTaskParams, executionLogCallback);

    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @VisibleForTesting
  public boolean prepare(
      LogCallback executionLogCallback, boolean skipSteadyStateCheck, K8sApplyHandlerConfig k8sApplyHandlerConfig) {
    return prepare(executionLogCallback, skipSteadyStateCheck, k8sApplyHandlerConfig, false);
  }

  @VisibleForTesting
  public boolean prepare(LogCallback executionLogCallback, boolean skipSteadyStateCheck,
      K8sApplyHandlerConfig k8sApplyHandlerConfig, boolean isErrorFrameworkSupported) {
    try {
      executionLogCallback.saveExecutionLog("Manifests processed. Found following resources: \n"
          + k8sTaskHelperBase.getResourcesInTableFormat(k8sApplyHandlerConfig.getResources()));

      k8sApplyHandlerConfig.setWorkloads(getEligibleWorkloads(k8sApplyHandlerConfig.getResources()));
      k8sApplyHandlerConfig.setCustomWorkloads(
          getCustomResourceDefinitionWorkloads(k8sApplyHandlerConfig.getResources()));
      if (isEmpty(k8sApplyHandlerConfig.getWorkloads()) && isEmpty(k8sApplyHandlerConfig.getCustomWorkloads())) {
        executionLogCallback.saveExecutionLog(color("\nNo Workload found.", Yellow, Bold));
      } else {
        executionLogCallback.saveExecutionLog("Found following Workloads\n"
            + k8sTaskHelperBase.getResourcesInTableFormat(
                ListUtils.union(k8sApplyHandlerConfig.getWorkloads(), k8sApplyHandlerConfig.getCustomWorkloads())));
        if (!skipSteadyStateCheck && !k8sApplyHandlerConfig.getCustomWorkloads().isEmpty()) {
          k8sTaskHelperBase.checkSteadyStateCondition(k8sApplyHandlerConfig.getCustomWorkloads());
        }
      }
    } catch (Exception e) {
      if (isErrorFrameworkSupported) {
        throw e;
      }
      log.error("Exception:", e);
      executionLogCallback.saveExecutionLog(ExceptionUtils.getMessage(e), ERROR, CommandExecutionStatus.FAILURE);
      return false;
    }
    executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
    return true;
  }

  public boolean steadyStateCheck(boolean skipSteadyStateCheck, String namespace,
      K8sDelegateTaskParams k8sDelegateTaskParams, long timeoutInMillis, LogCallback executionLogCallback,
      K8sApplyHandlerConfig k8sApplyHandlerConfig) throws Exception {
    return steadyStateCheck(skipSteadyStateCheck, namespace, k8sDelegateTaskParams, timeoutInMillis,
        executionLogCallback, k8sApplyHandlerConfig, false, false);
  }

  public boolean steadyStateCheck(boolean skipSteadyStateCheck, String namespace,
      K8sDelegateTaskParams k8sDelegateTaskParams, long timeoutInMillis, LogCallback executionLogCallback,
      K8sApplyHandlerConfig k8sApplyHandlerConfig, boolean isErrorFrameworkEnabled, boolean skipSteadyStateForWorkloads)
      throws Exception {
    if (isEmpty(k8sApplyHandlerConfig.getWorkloads()) && isEmpty(k8sApplyHandlerConfig.getCustomWorkloads())) {
      executionLogCallback.saveExecutionLog("Skipping Status Check since there is no Workload.", INFO, SUCCESS);
      return true;
    }

    if (skipSteadyStateCheck) {
      return true;
    }

    boolean success = true;
    if (!skipSteadyStateForWorkloads) {
      List<KubernetesResourceId> kubernetesResourceIds = k8sApplyHandlerConfig.getWorkloads()
                                                             .stream()
                                                             .map(KubernetesResource::getResourceId)
                                                             .collect(Collectors.toList());

      success = k8sTaskHelperBase.doStatusCheckForAllResources(k8sApplyHandlerConfig.getClient(), kubernetesResourceIds,
          k8sDelegateTaskParams, namespace, executionLogCallback, k8sApplyHandlerConfig.getCustomWorkloads().isEmpty(),
          isErrorFrameworkEnabled);
    }

    boolean customResourcesStatusSuccess = k8sTaskHelperBase.doStatusCheckForAllCustomResources(
        k8sApplyHandlerConfig.getClient(), k8sApplyHandlerConfig.getCustomWorkloads(), k8sDelegateTaskParams,
        executionLogCallback, true, timeoutInMillis, isErrorFrameworkEnabled);

    return success && customResourcesStatusSuccess;
  }
}
