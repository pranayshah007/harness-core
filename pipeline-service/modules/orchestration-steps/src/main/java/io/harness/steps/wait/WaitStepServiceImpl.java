/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import io.harness.repositories.WaitStepRepository;
import io.harness.wait.WaitStepInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Optional;

public class WaitStepServiceImpl implements WaitStepService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject WaitStepRepository waitStepRepository;
  public WaitStepInstance save(WaitStepInstance waitStepInstance) {
    return waitStepRepository.save(waitStepInstance);
  }
  public Optional<WaitStepInstance> findByNodeExecutionId(String nodeExecutionId) {
    return waitStepRepository.findByNodeExecutionId(nodeExecutionId);
  }
  public void markAsFailOrSuccess(String nodeExecutionId, WaitStepAction waitStepAction) {
    Optional<WaitStepInstance> waitStepInstance = findByNodeExecutionId(nodeExecutionId);
    String correlationId = waitStepInstance.get().getWaitStepInstanceId();
    waitNotifyEngine.doneWith(correlationId, WaitStepResponseData.builder().action(waitStepAction).build());
  }

  public WaitStepInstance getWaitStepExecutionDetails(String nodeExecutionId) {
    Optional<WaitStepInstance> waitStepInstance = findByNodeExecutionId(nodeExecutionId);
    return waitStepInstance.get();
  }
}
