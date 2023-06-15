/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.beans;

import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.metrics.RecordContext;

import com.google.inject.Inject;

public class OrchestratorContext extends RecordContext {
  @Inject
  public OrchestratorContext(AnalysisOrchestrator orchestrator) {
    put("accountId", orchestrator.getAccountId());
    put("verificationTaskId", orchestrator.getVerificationTaskId());
  }
}
