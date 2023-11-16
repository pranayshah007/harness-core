/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.execution.async.AsyncProgressData;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;

import java.time.Duration;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class AsyncWaitEngineImpl implements AsyncWaitEngine {
  private final WaitNotifyEngine waitNotifyEngine;
  private final String publisherName;

  public AsyncWaitEngineImpl(WaitNotifyEngine waitNotifyEngine, String publisherName) {
    this.waitNotifyEngine = waitNotifyEngine;
    this.publisherName = publisherName;
  }

  @Override
  public void waitForAllOn(
      NotifyCallback notifyCallback, ProgressCallback progressCallback, List<String> correlationIds, long timeout) {
    waitNotifyEngine.waitForAllOn(
        publisherName, notifyCallback, progressCallback, correlationIds, Duration.ofMillis(timeout));
  }

  @Override
  public void taskAcquired(String correlationId) {
    // Sends progress update to update node status to Running
    waitNotifyEngine.progressOn(correlationId, AsyncProgressData.builder().status(Status.RUNNING).build());
  }
}
