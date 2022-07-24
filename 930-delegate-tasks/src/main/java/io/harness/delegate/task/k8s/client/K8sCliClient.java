/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.client;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.watcher.event.K8sCliEventWatcher;
import io.harness.k8s.steadystate.watcher.workload.K8sWorkloadWatcherFactory;
import io.harness.k8s.steadystate.watcher.workload.WorkloadWatcher;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.StartedProcess;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class K8sCliClient implements K8sClient {
  @Inject private K8sClientHelper k8sClientHelper;
  @Inject private K8sCliEventWatcher k8sCliEventWatcher;
  @Inject private K8sWorkloadWatcherFactory workloadWatcherFactory;

  @Override
  public boolean performSteadyStateCheck(K8sSteadyStateDTO steadyStateDTO) throws Exception {
    List<KubernetesResourceId> workloads = steadyStateDTO.getResourceIds();
    if (EmptyPredicate.isEmpty(workloads)) {
      return true;
    }
    K8sDelegateTaskParams k8sDelegateTaskParams = steadyStateDTO.getK8sDelegateTaskParams();
    Kubectl client = k8sClientHelper.createKubernetesCliClient(k8sDelegateTaskParams);

    Set<String> namespaces = k8sClientHelper.getNamespacesToMonitor(workloads, steadyStateDTO.getNamespace());
    LogCallback executionLogCallback = steadyStateDTO.getExecutionLogCallback();

    K8sEventWatchDTO eventWatchDTO = k8sClientHelper.createEventWatchDTO(steadyStateDTO, client);
    K8sStatusWatchDTO rolloutStatusDTO = k8sClientHelper.createStatusWatchDTO(steadyStateDTO, client);

    List<StartedProcess> processRefs = new ArrayList<>();
    boolean success = false;

    try {
      k8sClientHelper.logSteadyStateInfo(workloads, namespaces, executionLogCallback);
      for (String ns : namespaces) {
        StartedProcess processRef = k8sCliEventWatcher.watchForEvents(ns, eventWatchDTO, executionLogCallback);
        processRefs.add(processRef);
      }

      for (KubernetesResourceId workload : workloads) {
        WorkloadWatcher workloadWatcher = workloadWatcherFactory.getWorkloadWatcher(workload.getKind(), false);
        success = workloadWatcher.watchRolloutStatus(rolloutStatusDTO, workload, executionLogCallback);
        if (!success) {
          break;
        }
      }

      return success;
    } catch (Exception e) {
      log.error("Exception while doing statusCheck", e);
      if (steadyStateDTO.isErrorFrameworkEnabled()) {
        throw e;
      }

      executionLogCallback.saveExecutionLog("\nFailed.", INFO, FAILURE);
      return false;
    } finally {
      k8sCliEventWatcher.destroyRunning(processRefs);
      if (success) {
        if (steadyStateDTO.isDenoteOverallSuccess()) {
          executionLogCallback.saveExecutionLog("\nDone.", INFO, CommandExecutionStatus.SUCCESS);
        }
      } else {
        executionLogCallback.saveExecutionLog(
            format("%nStatus check for resources in namespace [%s] failed.", steadyStateDTO.getNamespace()), INFO,
            FAILURE);
      }
    }
  }
}
