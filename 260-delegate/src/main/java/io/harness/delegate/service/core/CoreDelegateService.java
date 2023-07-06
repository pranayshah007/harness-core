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

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.ExecutionStatus;
import io.harness.delegate.core.beans.ExecutionStatusResponse;
import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.StatusCode;
import io.harness.delegate.service.common.SimpleDelegateAgent;
import io.harness.delegate.service.core.runner.TaskRunner;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateService extends SimpleDelegateAgent<AcquireTasksResponse, ExecutionStatusResponse> {
  private final TaskRunner taskRunner;

  @Override
  protected void abortTask(final DelegateTaskAbortEvent taskEvent) {
    throw new UnsupportedOperationException("Operation Not supported yet");
  }

  @Override
  protected AcquireTasksResponse acquireTask(final String taskId) throws IOException {
    final var response =
        executeRestCall(getManagerClient().acquireProtoTask(DelegateAgentCommonVariables.getDelegateId(), taskId,
            getDelegateConfiguration().getAccountId(), DELEGATE_INSTANCE_ID));

    log.info("Delegate {} received {} tasks for delegateInstance {}", DelegateAgentCommonVariables.getDelegateId(),
        response.getTaskList().size(), DELEGATE_INSTANCE_ID);
    return response;
  }

  @Override
  protected ExecutionStatusResponse executeTask(final AcquireTasksResponse acquireResponse) {
    final var task = acquireResponse.getTask(0);
    final var groupId = task.getExecutionInfraId();
    // FixMe: Hack so we don't need to make changes to CI & NG manager for now. Normally it would just invoke a single
    // runner stage
    if (hasTaskType(task.getTaskData(), INITIALIZATION_PHASE)) {
      taskRunner.init(groupId, task.getInfraData());
    } else if (hasTaskType(task.getTaskData(), CI_EXECUTE_STEP)) {
      taskRunner.execute(groupId, task.getTaskData());
    } else if (hasTaskType(task.getTaskData(), CI_CLEANUP)) {
      taskRunner.cleanup(groupId);
    } else { // Task which doesn't have separate infra step (e.g. CD)
      taskRunner.init(groupId, task.getInfraData());
      taskRunner.execute(groupId, task.getTaskData());
      taskRunner.cleanup(groupId);
    }
    return ExecutionStatusResponse.newBuilder()
        .setStatus(ExecutionStatus.newBuilder().setCode(StatusCode.CODE_SUCCESS).build())
        .build();
  }

  private boolean hasTaskType(final InputData tasks, final TaskType taskType) {
    return taskType != INITIALIZATION_PHASE && taskType != CI_EXECUTE_STEP && taskType != CI_CLEANUP;
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
