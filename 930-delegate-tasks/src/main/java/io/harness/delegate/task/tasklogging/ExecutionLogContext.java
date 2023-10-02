/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.tasklogging;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

@TargetModule(HarnessModule._920_DELEGATE_AGENT_BEANS)
public class ExecutionLogContext extends AutoLogContext {
  // keys
  private static final String TASK_ID = "taskId";
  private static final String RUNNER_TYPE = "runnerType";

  public ExecutionLogContext(String taskId, OverrideBehavior behavior) {
    super(TASK_ID, taskId, behavior);
  }

  public ExecutionLogContext(String taskId, String runnerType, OverrideBehavior behavior) {
    super(ImmutableMap.of(TASK_ID, taskId, RUNNER_TYPE, runnerType), behavior);
  }
}
