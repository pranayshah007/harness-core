/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.mock;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.harness.delegate.DelegateAgentCommonVariables;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.service.common.SimpleDelegateAgent;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockDelegateAgentService extends SimpleDelegateAgent<DelegateTaskPackage> {
  @Override
  protected void abortTask(final DelegateTaskAbortEvent taskEvent) {
    throw new UnsupportedOperationException("Operation Not supported yet");
  }

  @Override
  protected DelegateTaskPackage acquireTask(final String taskId) throws IOException {
    final var response = executeRestCall(getManagerClient().acquireTask(DelegateAgentCommonVariables.getDelegateId(),
        taskId, getDelegateConfiguration().getAccountId(), DELEGATE_INSTANCE_ID));

    log.info("Delegate {} received task {} for delegateInstance {}", DelegateAgentCommonVariables.getDelegateId(),
        response.getDelegateTaskId(), DELEGATE_INSTANCE_ID);
    return response;
  }

  @Override
  protected void executeTask(final DelegateTaskPackage delegateTaskPackage) {
    throw new UnsupportedOperationException("Operation Not supported yet");
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
