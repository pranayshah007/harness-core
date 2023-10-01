/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskclient;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.DelegateTask;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.AutoLogContext;

import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class TaskClientImpl implements TaskClient {
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  private final ScheduleTaskBroadcastHelper broadcastHelper;
  private final DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Override
  public void sendTask(DelegateTask task) {
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }
    try (AutoLogContext ignore1 = new ExecutionLogContext(task.getUuid(), task.getEventType(), OVERRIDE_ERROR)) {
      log.info("Going to broadcast task");
      // Handles routing the reqest to the right delegate instance
      delegateTaskServiceClassic.processScheduleTaskRequest(task, QUEUED);
      // Send out request via websocket
      broadcastHelper.broadcastRequestEvent(task);
    }
  }
}
