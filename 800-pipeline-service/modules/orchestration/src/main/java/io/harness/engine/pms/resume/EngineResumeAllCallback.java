/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(CDC)
public class EngineResumeAllCallback implements OldNotifyCallback {
  @Inject OrchestrationEngine orchestrationEngine;

  @Getter Ambiance ambiance;

  @Builder
  public EngineResumeAllCallback(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    orchestrationEngine.startNodeExecution(ambiance);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    // Do Nothing
  }
}
