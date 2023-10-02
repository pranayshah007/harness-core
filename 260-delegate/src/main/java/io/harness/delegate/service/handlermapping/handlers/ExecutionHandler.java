/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.handlers;

import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.runners.RunnersFactory;
import io.harness.delegate.service.runners.itfc.Runner;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutionHandler implements Handler {
  private final RunnersFactory runnersFactory;

  @Override
  public void handle(String runnerType, TaskPayload taskPayload, Context context) {
    Runner runner = runnersFactory.get(runnerType);
    runner.execute(taskPayload.getExecutionInfraId(), taskPayload.getTaskData(), context);
  }
}
