/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.tasks.ResponseData;

import java.time.Duration;
import java.util.Map;

public interface TaskExecutor {
  String queueTask(Map<String, String> setupAbstractions, TaskRequest taskRequest, Duration holdFor);

  void expireTask(Map<String, String> setupAbstractions, String taskId);

  boolean abortTask(Map<String, String> setupAbstractions, String taskId);

  <T extends ResponseData> T executeTask(Map<String, String> setupAbstractions, TaskRequest taskRequest)
      throws InterruptedException;
}
