/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.DelegateTask;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.TaskType;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DelegateLogContextHelper {
  public AutoLogContext getLogContext(DelegateTask delegateTask) {
    // Log context
    String taskId = delegateTask.getUuid();
    if (delegateTask.getData() != null) {
      return new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
          TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR);
    } else if (delegateTask.getTaskDataV2() != null) {
      return new TaskLogContext(taskId, delegateTask.getTaskDataV2().getTaskType(),
          TaskType.valueOf(delegateTask.getTaskDataV2().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR);
    } else {
      return new ExecutionLogContext(taskId, delegateTask.getEventType(), OVERRIDE_ERROR);
    }
  }
}
