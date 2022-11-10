/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.WaitStepRepository;
import io.harness.wait.WaitStepInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Optional;

public class WaitStepServiceImpl implements WaitStepService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject WaitStepRepository waitStepRepository;
  @Inject PlanExecutionService planExecutionService;
  public WaitStepInstance save(WaitStepInstance waitStepInstance) {
    return waitStepRepository.save(waitStepInstance);
  }
  public Optional<WaitStepInstance> findByNodeExecutionId(String nodeExecutionId) {
    return waitStepRepository.findByNodeExecutionId(nodeExecutionId);
  }
  public void markAsFailOrSuccess(String planExecutionId, String nodeExecutionId, WaitStepAction waitStepAction) {
    Optional<WaitStepInstance> waitStepInstance = findByNodeExecutionId(nodeExecutionId);
    String correlationId = waitStepInstance.get().getWaitStepInstanceId();
    waitNotifyEngine.doneWith(correlationId, WaitStepResponseData.builder().action(waitStepAction).build());
    updatePlanStatus(planExecutionId, nodeExecutionId);
  }

  public WaitStepInstance getWaitStepExecutionDetails(String nodeExecutionId) {
    Optional<WaitStepInstance> waitStepInstance = findByNodeExecutionId(nodeExecutionId);
    return waitStepInstance.get();
  }

  public void updatePlanStatus(String planExecutionId, String nodeExecutionId) {
    // Update plan status after the completion of the approval step.
    Status planStatus = planExecutionService.calculateStatusExcluding(planExecutionId, nodeExecutionId);
    if (!StatusUtils.isFinalStatus(planStatus)) {
      planExecutionService.updateStatus(planExecutionId, planStatus);
    }
  }
}
