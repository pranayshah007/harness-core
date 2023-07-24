/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stresstesting.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import io.harness.testing.DelegateTaskStressTestStage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateTaskStressTestThread extends Thread {
  DelegateServiceBlockingStub delegateServiceBlockingStub;
  DelegateTaskStressTestStage stage;
  String stageId;

  public DelegateTaskStressTestThread(
      DelegateServiceBlockingStub delegateServiceBlockingStub, DelegateTaskStressTestStage stage, String stageId) {
    this.delegateServiceBlockingStub = delegateServiceBlockingStub;
    this.stage = stage;
    this.stageId = stageId;
  }

  @Override
  public void run() {
    try {
      int taskRequestCount = stage.getTaskRequestCount();

      for (int i = 0; i < stage.getIterations(); i++) {
        int item = (int) (Math.random() * taskRequestCount);
        String taskId = delegateServiceBlockingStub.submitTaskV2(stage.getTaskRequest(item)).getTaskId().getId();
        log.info("Firing iteration " + i + "on stage " + stageId + "task id " + taskId);
        Thread.sleep(1000 / stage.getQps());
      }
    } catch (Exception e) {
      log.warn("Caught exception: " + e);
    }
  }
}
