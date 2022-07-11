/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.kubectl.GetCommand;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.logging.LogCallback;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sCliEventWatcher {
  public StartedProcess watchForEvents(
      String namespace, K8sEventWatchDTO k8sEventWatchDTO, LogCallback executionLogCallback) throws Exception {
    List<KubernetesResourceId> resourceIds = k8sEventWatchDTO.getResourceIds();
    String eventInfoFormat = k8sEventWatchDTO.getEventInfoFormat();
    String eventErrorFormat = k8sEventWatchDTO.getEventErrorFormat();
    GetCommand eventGetCommand = k8sEventWatchDTO.getClient()
                                     .get()
                                     .resources("events")
                                     .namespace(namespace)
                                     .output(K8sConstants.eventWithNamespaceOutputFormat)
                                     .watchOnly(true);
    executionLogCallback.saveExecutionLog(getPrintableCommand(eventGetCommand.command()) + "\n");

    LogOutputStream watchInfoStream = new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        Optional<KubernetesResourceId> filteredResourceId =
            resourceIds.parallelStream()
                .filter(kubernetesResourceId
                    -> line.contains(isNotBlank(kubernetesResourceId.getNamespace())
                               ? kubernetesResourceId.getNamespace()
                               : namespace)
                        && line.contains(kubernetesResourceId.getName()))
                .findFirst();

        filteredResourceId.ifPresent(kubernetesResourceId
            -> executionLogCallback.saveExecutionLog(
                format(eventInfoFormat, "Event", kubernetesResourceId.getName(), line), INFO));
      }
    };
    LogOutputStream watchErrorStream = new LogOutputStream() {
      @Override
      protected void processLine(String line) {
        executionLogCallback.saveExecutionLog(format(eventErrorFormat, "Event", line), ERROR);
      }
    };

    return eventGetCommand.executeInBackground(
        k8sEventWatchDTO.getWorkingDirectory(), watchInfoStream, watchErrorStream);
  }

  public void destroyRunning(List<StartedProcess> processes) throws InterruptedException {
    for (StartedProcess eventWatchProcess : processes) {
      eventWatchProcess.getProcess().destroyForcibly().waitFor();
    }
  }
}
