/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Value
@Builder
@Slf4j
public class CIInitDelegateTaskStatusNotifier implements OldNotifyCallback {
  @Inject WaitNotifyEngine waitNotifyEngine;
  String waitId;

  @Override
  public void notify(Map<String, ResponseData> response) {
    notifyWithError(response, false);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyWithError(response, true);
  }

  private void notifyWithError(Map<String, ResponseData> response, boolean asyncError) {
    log.info("ci waiting on del task: {} ", waitId);

    if (response.size() != 1) {
      log.error("received notify event is invalid. Check what happened {}", response.size());
      return;
    }
    ResponseData responseData = (ResponseData) response.values().toArray()[0];
    waitNotifyEngine.doneWith(waitId, responseData);
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    // this needs to be checked again.
    log.error("ci build notify event timed out. Check what happened {}", responseMap.size());
    notifyWithError(responseMap, false);
  }
}
