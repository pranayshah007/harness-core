/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.tasklogging;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@TargetModule(HarnessModule._920_DELEGATE_AGENT_BEANS)
public class TaskLogContext extends AutoLogContext {
  // keys
  private static final String TASK_ID = "taskId";
  private static final String TASK_TYPE = "taskType";
  private static final String RANK = "rank";
  private static final String TASK_GROUP = "taskGroup";
  private static final String CAPABILITY_DETAILS = "capabilityDetails";

  public TaskLogContext(String taskId, OverrideBehavior behavior) {
    super(TASK_ID, taskId, behavior);
  }

  public TaskLogContext(String taskId, String taskType, String taskGroup, OverrideBehavior behavior) {
    super(ImmutableMap.of(TASK_ID, taskId, TASK_TYPE, taskType, TASK_GROUP, taskGroup), behavior);
  }

  public TaskLogContext(
      String taskId, String taskType, String taskGroup, DelegateTaskRank rank, OverrideBehavior behavior) {
    super(ImmutableMap.of(TASK_ID, taskId, TASK_TYPE, taskType, TASK_GROUP, taskGroup, RANK,
              rank == null ? DelegateTaskRank.CRITICAL.name() : rank.name()),
        behavior);
  }
}
