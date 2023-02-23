/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.WaitStepRepository;
import io.harness.wait.WaitStepInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;
import net.jodah.failsafe.Failsafe;

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
  }

  public WaitStepInstance getWaitStepExecutionDetails(String nodeExecutionId) {
    Optional<WaitStepInstance> waitStepInstance = findByNodeExecutionId(nodeExecutionId);
    return waitStepInstance.get();
  }

  @Override
  public void deleteWaitStepInstancesForGivenNodeExecutionIds(Set<String> nodeExecutionIds) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      waitStepRepository.deleteAllByNodeExecutionIdIn(nodeExecutionIds);
      return true;
    });
  }
}
