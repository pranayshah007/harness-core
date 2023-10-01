/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.managerclient.DelegateAgentManagerClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Helper class to acquire "AcquireTasksResponse". This class is only used for runner's task workflow.
 * Compared to old workflow, the new workflow has no validation task. The currentlyAcquiringTasks is used to record
 * tasks being acquired, while in old workflow, the currentlyAcquiringTasks contains tasks being executed as well.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AcquireTaskHelper {
  private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
  private final DelegateAgentManagerClient managerClient;

  public AcquireTasksResponse acquireTaskPayload(final String accountId, final String delegateId,
      final String delegateInstanceId, final String taskId, Consumer<Response<AcquireTasksResponse>> handler)
      throws IOException {
    if (currentlyAcquiringTasks.contains(taskId)) {
      log.info("Task [Delegate state machine entity: {}] currently acquiring. Don't acquire again", taskId);
      return null;
    }
    currentlyAcquiringTasks.add(taskId);

    try {
      log.debug("Try to acquire DelegateTask - accountId: {}", accountId);
      Call<AcquireTasksResponse> acquireCall =
          managerClient.acquireTaskPayload(taskId, delegateId, accountId, delegateInstanceId);

      return ManagerCallHelper.executeAcquireCallWithRetry(
          acquireCall, String.format("Failed acquiring delegate task %s by delegate %s", taskId, delegateId), handler);
    } finally {
      currentlyAcquiringTasks.remove(taskId);
    }
  }
}
