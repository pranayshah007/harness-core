/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.logging.CommandExecutionStatus;

import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class K8sTaskHandler {
  @Inject protected DelegateLogService delegateLogService;
  @Inject private TimeLimiter timeLimiter;

  public K8sTaskExecutionResponse executeTask(
      K8sTaskParameters k8STaskParameters, K8sDelegateTaskParams k8SDelegateTaskParams) {
    K8sTaskExecutionResponse result;
    try {
      if (k8STaskParameters.isTimeoutSupported()) {
        result =
            HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(k8STaskParameters.getTimeoutIntervalInMin()),
                () -> executeTaskInternal(k8STaskParameters, k8SDelegateTaskParams));
      } else {
        result = executeTaskInternal(k8STaskParameters, k8SDelegateTaskParams);
      }
    } catch (IOException ex) {
      logError(k8STaskParameters, ex);
      result = K8sTaskExecutionResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .errorMessage("Could not complete k8s task due to IO exception")
                   .build();
    } catch (TimeoutException ex) {
      logError(k8STaskParameters, ex);
      result = K8sTaskExecutionResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .isTimeoutError(true)
                   .errorMessage("Timed out while waiting for k8s task to complete")
                   .build();
    } catch (InterruptedException ex) {
      logError(k8STaskParameters, ex);
      Thread.currentThread().interrupt();
      result = K8sTaskExecutionResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .errorMessage("Interrupted while waiting for k8s task to complete")
                   .build();
    } catch (WingsException ex) {
      logError(k8STaskParameters, ex);
      result = K8sTaskExecutionResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .errorMessage(ExceptionUtils.getMessage(ex))
                   .build();
    } catch (Exception ex) {
      logError(k8STaskParameters, ex);
      result = K8sTaskExecutionResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .errorMessage("Failed to complete k8s task")
                   .build();
    }
    return result;
  }

  protected abstract K8sTaskExecutionResponse executeTaskInternal(
      K8sTaskParameters k8STaskParameters, K8sDelegateTaskParams k8SDelegateTaskParams) throws Exception;

  private void logError(K8sTaskParameters taskParameters, Throwable ex) {
    log.error("Exception in processing K8s task [{}]",
        taskParameters.getCommandName() + ":" + taskParameters.getCommandType(), ex);
  }
}
