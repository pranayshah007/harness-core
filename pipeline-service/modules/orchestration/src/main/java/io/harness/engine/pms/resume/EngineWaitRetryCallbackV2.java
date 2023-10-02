/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.resume;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class EngineWaitRetryCallbackV2 implements OldNotifyCallback {
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private OrchestrationEngine engine;

  @NonNull @Getter Ambiance ambiance;

  @Builder
  public EngineWaitRetryCallbackV2(@NonNull Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    executorService.submit(() -> engine.startNodeExecution(ambiance));
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.info("Retry Error Callback Received");
  }
}
