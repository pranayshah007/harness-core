/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.tasks.ResponseData;

import java.time.Duration;
import java.util.Map;

/**
 * The type Dummy task executor.
 * This is only to provide a Dummy Binding to Guice else it complains while running tests
 */

@OwnedBy(HarnessTeam.PIPELINE)
public class NoopTaskExecutor implements TaskExecutor {
  @Override
  public String queueTask(Map<String, String> setupAbstractions, TaskRequest task, Duration holdFor) {
    return null;
  }

  @Override
  public void expireTask(Map<String, String> setupAbstractions, String taskId) {
    // Just a placeholder
  }

  @Override
  public boolean abortTask(Map<String, String> setupAbstractions, String taskId) {
    return true;
  }

  @Override
  public <T extends ResponseData> T executeTask(Map<String, String> setupAbstractions, TaskRequest taskRequest)
      throws InterruptedException {
    return null;
  }
}
