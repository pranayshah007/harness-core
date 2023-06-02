/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionCheck;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.interrupts.InterruptType;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;

@OwnedBy(PIPELINE)
public interface InterruptService {
  List<Interrupt> fetchActiveInterrupts(String planExecutionId);

  List<Interrupt> fetchActiveInterruptsForNodeExecution(String planExecutionId, String nodeExecutionId);

  List<Interrupt> fetchActiveInterruptsForNodeExecutionByType(
      String planExecutionId, String nodeExecutionId, InterruptType interruptType);

  List<Interrupt> fetchAllInterrupts(String planExecutionId);

  Interrupt markProcessed(String interruptId, State interruptState);

  Interrupt markProcessedForceful(String interruptId, State interruptState, boolean forceful);

  Interrupt markProcessing(String interruptId);

  List<Interrupt> fetchActivePlanLevelInterrupts(String planExecutionId);

  ExecutionCheck checkInterruptsPreInvocation(String planExecutionId, String nodeExecutionId);

  Interrupt save(@Valid Interrupt interrupt);

  Interrupt get(String interruptId);

  long closeActiveInterrupts(String planExecutionId);

  /**
   * Delete all interrupts for given planExecutionIds
   * Uses - planExecutionId_nodeExecutionId_createdAt_idx
   * @param planExecutionIds
   */
  void deleteAllInterrupts(Set<String> planExecutionIds);

  List<Interrupt> fetchAbortAllPlanLevelInterrupt(String planExecutionId);
}
