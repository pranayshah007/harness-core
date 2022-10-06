/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;

import java.time.Instant;
import java.util.Set;

public interface OrchestrationService {
  void queueAnalysis(String verificationTaskId, Instant startTime, Instant endTime);
  void orchestrate(AnalysisOrchestrator orchestrator);
  void markTerminated(String verificationTaskId);
  void markStateMachineTerminated(String verificationTaskId);
  void markCompleted(String verificationTaskId);
  void markCompleted(Set<String> verificationTaskIds);
  AnalysisOrchestrator getAnalysisOrchestrator(String verificationTaskId);
}
