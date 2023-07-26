/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.progresstaskstreaming;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.delegate.beans.taskprogress.TaskProgressCallback;

import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NGDelegateTaskProgressCallback implements TaskProgressCallback {
  private final ILogStreamingTaskClient iLogStreamingTaskClient;
  private final String taskId;

  public NGDelegateTaskProgressCallback(ILogStreamingTaskClient iLogStreamingTaskClient, String taskId) {
    this.iLogStreamingTaskClient = iLogStreamingTaskClient;
    this.taskId = taskId;
  }

  @Override
  public void sendTaskProgressUpdate(String event, DelegateProgressData delegateTaskProgressData) {
    if (this.iLogStreamingTaskClient == null) {
      return;
    }

    ITaskProgressClient taskProgressClient = iLogStreamingTaskClient.obtainTaskProgressClient();

    ExecutorService taskProgressExecutor = iLogStreamingTaskClient.obtainTaskProgressExecutor();
    taskProgressExecutor.submit(() -> sendTaskProgressUpdate(taskProgressClient, event, delegateTaskProgressData));
  }

  void sendTaskProgressUpdate(
      ITaskProgressClient taskProgressClient, String event, DelegateProgressData delegateTaskProgressData) {
    if (taskProgressClient != null) {
      try {
        log.info("Sending task progress event {} for task: {}", event, taskId);
        if (delegateTaskProgressData == null) {
          log.info("Progress data is empty(null)");
          return;
        } else {
          taskProgressClient.sendTaskProgressUpdate(delegateTaskProgressData);
        }
        log.info("Task progress event {} sent for task: {}", event, taskId);
      } catch (Exception exception) {
        log.error("Failed to send task progress event {} update for task {}", event, taskId, exception);
      }
    }
  }
}
