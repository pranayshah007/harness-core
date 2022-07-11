/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.RolloutStatusCommand;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sCliWatcher implements WorkloadWatcher {
  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId resourceId,
      LogCallback executionLogCallback) throws Exception {
    String statusFormat = k8SStatusWatchDTO.getStatusFormat();
    Kubectl client = k8SStatusWatchDTO.getClient();
    Preconditions.checkNotNull(client, "K8s CLI Client cannot be null.");
    K8sDelegateTaskParams k8sDelegateTaskParams = k8SStatusWatchDTO.getK8sDelegateTaskParams();

    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream();
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @SneakyThrows
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
                 errorCaptureStream.write(line.getBytes(UTF_8));
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             }) {
      ProcessResult result;
      String printableExecutedCommand;

      RolloutStatusCommand rolloutStatusCommand =
          client.rollout().status().resource(resourceId.kindNameRef()).namespace(resourceId.getNamespace()).watch(true);

      printableExecutedCommand = getPrintableCommand(rolloutStatusCommand.command());
      executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");

      result = rolloutStatusCommand.execute(
          k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false);
      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
          ProcessResponse processResponse =
              ProcessResponse.builder()
                  .errorMessage(ExceptionMessageSanitizer.sanitizeMessage(errorCaptureStream.toString()))
                  .processResult(result)
                  .printableCommand(printableExecutedCommand)
                  .kubectlPath(k8sDelegateTaskParams.getKubectlPath())
                  .build();
          throw new KubernetesCliTaskRuntimeException(processResponse, KubernetesCliCommandType.STEADY_STATE_CHECK);
        }
      }

      return success;
    }
  }
}
