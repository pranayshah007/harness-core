/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.kubectl.AbstractExecutable.getPrintableCommand;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.GetJobCommand;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class JobCliWatcher implements WorkloadWatcher {
  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId resourceId,
      LogCallback executionLogCallback) throws Exception {
    String statusFormat = k8SStatusWatchDTO.getStatusFormat();
    Kubectl client = k8SStatusWatchDTO.getClient();
    Preconditions.checkNotNull(client, "K8s CLI Client cannot be null.");
    K8sDelegateTaskParams k8sDelegateTaskParams = k8SStatusWatchDTO.getK8sDelegateTaskParams();

    try (LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             };
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
               }
             }) {
      GetJobCommand jobCompleteCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                             .output("jsonpath='{.status.conditions[?(@.type==\"Complete\")].status}'");
      GetJobCommand jobFailedCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                           .output("jsonpath='{.status.conditions[?(@.type==\"Failed\")].status}'");
      GetJobCommand jobStatusCommand =
          client.getJobCommand(resourceId.getName(), resourceId.getNamespace()).output("jsonpath='{.status}'");
      GetJobCommand jobCompletionTimeCommand = client.getJobCommand(resourceId.getName(), resourceId.getNamespace())
                                                   .output("jsonpath='{.status.completionTime}'");

      executionLogCallback.saveExecutionLog(getPrintableCommand(jobStatusCommand.command()) + "\n");

      return getJobStatus(k8sDelegateTaskParams, statusInfoStream, statusErrorStream, jobCompleteCommand,
          jobFailedCommand, jobStatusCommand, jobCompletionTimeCommand, k8SStatusWatchDTO.isErrorFrameworkEnabled());
    }
  }

  public boolean getJobStatus(K8sDelegateTaskParams k8sDelegateTaskParams, LogOutputStream statusInfoStream,
      LogOutputStream statusErrorStream, GetJobCommand jobCompleteCommand, GetJobCommand jobFailedCommand,
      GetJobCommand jobStatusCommand, GetJobCommand jobCompletionTimeCommand, boolean isErrorFrameworkEnabled)
      throws Exception {
    while (true) {
      jobStatusCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), statusInfoStream, statusErrorStream, false);

      ProcessResult result = jobCompleteCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (isErrorFrameworkEnabled) {
          String explanation = isNotEmpty(result.outputUTF8())
              ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                  getPrintableCommand(jobCompleteCommand.command()), result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                  getPrintableCommand(jobCompleteCommand.command()), result.getExitValue());
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }

        return false;
      }

      // cli command outputs with single quotes
      String jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        result = jobCompletionTimeCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);
        success = 0 == result.getExitValue();
        if (!success) {
          log.warn(result.outputUTF8());
          if (isErrorFrameworkEnabled) {
            String explanation = isNotEmpty(result.outputUTF8())
                ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                    getPrintableCommand(jobCompletionTimeCommand.command()), result.getExitValue(), result.outputUTF8())
                : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                    getPrintableCommand(jobCompletionTimeCommand.command()), result.getExitValue());
            throw NestedExceptionUtils.hintWithExplanationException(
                KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
                new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
          }

          return false;
        }

        String completionTime = result.outputUTF8().replace("'", "");
        if (isNotBlank(completionTime)) {
          return true;
        }
      }

      result = jobFailedCommand.execute(k8sDelegateTaskParams.getWorkingDirectory(), null, null, false);

      success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());

        if (isErrorFrameworkEnabled) {
          String explanation = isNotEmpty(result.outputUTF8())
              ? format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED_OUTPUT,
                  getPrintableCommand(jobFailedCommand.command()), result.getExitValue(), result.outputUTF8())
              : format(KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
                  getPrintableCommand(jobFailedCommand.command()), result.getExitValue());
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_CLI_FAILED, explanation,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }
        return false;
      }

      jobStatus = result.outputUTF8().replace("'", "");
      if ("True".equals(jobStatus)) {
        if (isErrorFrameworkEnabled) {
          throw NestedExceptionUtils.hintWithExplanationException(
              KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_JOB_FAILED,
              KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_JOB_FAILED,
              new KubernetesTaskException(KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED));
        }
        return false;
      }

      sleep(ofSeconds(5));
    }
  }
}
