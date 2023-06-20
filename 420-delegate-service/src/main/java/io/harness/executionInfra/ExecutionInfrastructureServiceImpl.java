/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionInfra;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.DelegateTask;
import io.harness.delegate.core.beans.ExecutionInfrastructureLocation;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutionInfrastructureServiceImpl implements ExecutionInfrastructureService {
  private final HPersistence persistence;

  @Override
  public String addExecutionInfrastructure(
      DelegateTask task, String delegateId, ExecutionInfrastructureLocation location) {
    try (AutoLogContext ignore =
             new ExecutionLogContext(task.getUuid(), task.getRequestUri(), task.getRequestMethod(), OVERRIDE_ERROR)) {
      log.info("Response received for task: {} from Delegate: {}", task.getUuid(), delegateId);
      ExecutionInfraLocation entity = ExecutionInfraLocation.builder()
                                          .delegateGroupName(location.getDelegateName())
                                          .runnerType(location.getRunnerType())
                                          .createdByDelegateId(delegateId)
                                          .uuid(task.getUuid())
                                          .build();
      return persistence.save(entity, false);
    }
  }

  @Override
  public ExecutionInfraLocation getExecutionInfrastructure(String id) {
    return null;
  }

  @Override
  public void deleteExecutionInfrastructure(String executionInfraUuid) {
    log.warn("not implemented");
  }
}
