/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.common;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.managerclient.DelegateAgentManagerClient;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class AcquireTaskHelper {
    private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
    @Inject private DelegateAgentManagerClient managerClient;

    public AcquireTasksResponse acquireTaskPayload(
        final String accountId,
        final String delegateId,
        final String delegateInstanceId,
        final String taskId,
        Consumer<Response<AcquireTasksResponse>> handler) throws IOException {

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
                acquireCall,
                String.format("Failed acquiring delegate task %s by delegate %s", taskId, delegateId),
                handler);
        } catch (IOException e) {
            throw e;
        } finally {
            currentlyAcquiringTasks.remove(taskId);
        }
    }

    public DelegateTaskPackage acquireKryo(
        final String accountId,
        final String delegateId,
        final String delegateInstanceId,
        final String taskId,
        Consumer<Response<DelegateTaskPackage>> handler) throws IOException {

        if (currentlyAcquiringTasks.contains(taskId)) {
            log.info("Task [DelegateTaskEvent: {}] currently acquiring. Don't acquire again", taskId);
            return null;
        }
        currentlyAcquiringTasks.add(taskId);

        try {
            log.debug("Try to acquire DelegateTask - accountId: {}", accountId);
            Call<DelegateTaskPackage> acquireCall =
                managerClient.acquireTask(delegateId, taskId, accountId, delegateInstanceId);

            return ManagerCallHelper.executeAcquireCallWithRetry(
                acquireCall,
                String.format("Failed acquiring delegate task %s by delegate %s", taskId, delegateId),
                handler);
        } catch (IOException e) {
            throw e;
        } finally {
            currentlyAcquiringTasks.remove(taskId);
        }
    }
}
