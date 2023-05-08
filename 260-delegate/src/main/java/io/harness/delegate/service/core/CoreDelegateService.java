/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core;

import static software.wings.beans.TaskType.CI_CLEANUP;
import static software.wings.beans.TaskType.CI_EXECUTE_STEP;
import static software.wings.beans.TaskType.INITIALIZATION_PHASE;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.core.beans.ExecutionInfrastructure;
import io.harness.delegate.core.beans.ExecutionMode;
import io.harness.delegate.core.beans.ExecutionPriority;
import io.harness.delegate.core.beans.TaskDescriptor;
import io.harness.delegate.service.common.SimpleDelegateAgent;
import io.harness.delegate.service.core.runner.TaskRunner;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateService extends SimpleDelegateAgent {
  private final TaskRunner taskRunner;

  @Override
  protected void abortTask(final DelegateTaskAbortEvent taskEvent) {
    throw new UnsupportedOperationException("Operation Not supported yet");
  }

  @Override
  protected void executeTask(
      final String groupId, final List<TaskDescriptor> tasks, final ExecutionInfrastructure infra) {
    tasks.forEach(this::validatePluginData);

    // FixMe: Hack so we don't need to make changes to CI & NG manager for now. Normally it would just invoke a single
    // runner stage
    if (hasTaskType(tasks, INITIALIZATION_PHASE)) {
      taskRunner.init(groupId, tasks, infra);
    } else if (hasTaskType(tasks, CI_EXECUTE_STEP)) {
      taskRunner.execute(groupId, tasks);
    } else if (hasTaskType(tasks, CI_CLEANUP)) {
      taskRunner.cleanup(groupId);
    } else { // Task which doesn't have separate infra step (e.g. CD)
      taskRunner.init(groupId, tasks, infra);
      taskRunner.execute(groupId, tasks);
      taskRunner.cleanup(groupId);
    }
  }

  private boolean hasTaskType(final List<TaskDescriptor> tasks, final TaskType taskType) {
    return tasks.stream()
        .filter(TaskDescriptor::hasRuntime)
        .anyMatch(task -> taskType.name().equals(task.getRuntime().getType()));
  }

  private void validatePluginData(final @NonNull TaskDescriptor task) {
    if (task.getPriority() == ExecutionPriority.PRIORITY_UNKNOWN) {
      throw new IllegalArgumentException("Task Priority must be specified");
    }
    if (task.getMode() == ExecutionMode.MODE_UNKNOWN) {
      throw new IllegalArgumentException("Task Mode must be specified");
    }
  }

  @Override
  protected List<String> getCurrentlyExecutingTaskIds() {
    return List.of("");
  }

  @Override
  protected List<TaskType> getSupportedTasks() {
    return Arrays.stream(TaskType.values()).collect(toUnmodifiableList());
  }
}
