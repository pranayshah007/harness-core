/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.k8s.ManifestType.KUSTOMIZE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sNGTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public abstract class K8sRequestHandler {
  public K8sDeployResponse executeTask(K8sDeployRequest k8sDeployRequest, K8sDelegateTaskParams k8SDelegateTaskParams,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (isErrorFrameworkSupported()) {
      return executeTaskInternal(k8sDeployRequest, k8SDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    } else {
      return executeTaskWithExceptionHandling(
          k8sDeployRequest, k8SDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    }
  }

  protected abstract K8sDeployResponse executeTaskInternal(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8SDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception;

  protected K8sDeployResponse getGenericFailureResponse(K8sNGTaskResponse taskResponse) {
    return K8sDeployResponse.builder()
        .commandExecutionStatus(FAILURE)
        .k8sNGTaskResponse(taskResponse)
        .errorMessage("Failed to complete K8s task. Please check execution logs.")
        .build();
  }

  public boolean isErrorFrameworkSupported() {
    return true;
  }

  public final void onTaskFailed(K8sDeployRequest request, Exception exception,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, exception);
    handleTaskFailure(request, exception);
  }

  protected void handleTaskFailure(K8sDeployRequest request, Exception exception) throws Exception {}

  protected K8sNGTaskResponse getTaskResponseOnFailure() {
    return null;
  }

  protected List<String> getManifestOverrideFlies(K8sDeployRequest request) {
    if (KUSTOMIZE.equals(request.getManifestDelegateConfig().getManifestType())) {
      return request.getKustomizePatchesList();
    } else {
      return isEmpty(request.getValuesYamlList()) ? request.getOpenshiftParamList() : request.getValuesYamlList();
    }
  }

  private K8sDeployResponse executeTaskWithExceptionHandling(K8sDeployRequest k8sDeployRequest,
      K8sDelegateTaskParams k8SDelegateTaskParams, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) {
    K8sDeployResponse result;
    try {
      result =
          executeTaskInternal(k8sDeployRequest, k8SDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    } catch (IOException ex) {
      logError(k8sDeployRequest, ex);
      closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, ex);
      result = K8sDeployResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .k8sNGTaskResponse(getTaskResponseOnFailure())
                   .errorMessage("Could not complete k8s task due to IO exception")
                   .build();
    } catch (TimeoutException ex) {
      logError(k8sDeployRequest, ex);
      closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, ex);
      result = K8sDeployResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .k8sNGTaskResponse(getTaskResponseOnFailure())
                   .errorMessage("Timed out while waiting for k8s task to complete")
                   .build();
    } catch (InterruptedException ex) {
      logError(k8sDeployRequest, ex);
      closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, ex);
      Thread.currentThread().interrupt();
      result = K8sDeployResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .k8sNGTaskResponse(getTaskResponseOnFailure())
                   .errorMessage("Interrupted while waiting for k8s task to complete")
                   .build();
    } catch (WingsException ex) {
      logError(k8sDeployRequest, ex);
      closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, ex);
      result = K8sDeployResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .k8sNGTaskResponse(getTaskResponseOnFailure())
                   .errorMessage(ExceptionUtils.getMessage(ex))
                   .build();
    } catch (Exception ex) {
      logError(k8sDeployRequest, ex);
      closeOpenCommandUnits(commandUnitsProgress, logStreamingTaskClient, ex);
      result = K8sDeployResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                   .k8sNGTaskResponse(getTaskResponseOnFailure())
                   .errorMessage("Failed to complete K8s task. Please check execution logs.")
                   .build();
    }
    return result;
  }

  private void closeOpenCommandUnits(
      CommandUnitsProgress commandUnitsProgress, ILogStreamingTaskClient logStreamingTaskClient, Throwable throwable) {
    try {
      LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap =
          commandUnitsProgress.getCommandUnitProgressMap();
      if (EmptyPredicate.isNotEmpty(commandUnitProgressMap)) {
        for (Map.Entry<String, CommandUnitProgress> entry : commandUnitProgressMap.entrySet()) {
          String commandUnitName = entry.getKey();
          CommandUnitProgress progress = entry.getValue();
          if (CommandExecutionStatus.RUNNING == progress.getStatus()) {
            LogCallback logCallback =
                new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, false, commandUnitsProgress);
            logCallback.saveExecutionLog(
                String.format(
                    "Failed: [%s].", ExceptionUtils.getMessage(getFirstNonHintOrExplanationThrowable(throwable))),
                LogLevel.ERROR, FAILURE);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Exception while closing command units", ex);
    }
  }

  private void logError(K8sDeployRequest k8sDeployRequest, Throwable ex) {
    log.error("Exception in processing K8s task [{}]",
        k8sDeployRequest.getCommandName() + ":" + k8sDeployRequest.getTaskType(), ex);
  }

  private Throwable getFirstNonHintOrExplanationThrowable(Throwable throwable) {
    if (throwable.getCause() == null) {
      return throwable;
    }

    if (!(throwable instanceof HintException || throwable instanceof ExplanationException)) {
      return throwable;
    }

    return getFirstNonHintOrExplanationThrowable(throwable.getCause());
  }
}
