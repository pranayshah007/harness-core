/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputServiceImpl implements ExecutionInputService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject ExecutionInputRepository executionInputRepository;

  @Override
  // TODO(BRIJESH): Should return the status if merging was successful. Use lock so that only one input can be processed
  // and only one doneWith should be called.
  public void continueExecution(String nodeExecutionId) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isPresent()) {
      ExecutionInputInstance executionInputInstance = optional.get();
      // Write the merging logic here.
      waitNotifyEngine.doneWith(executionInputInstance.getInputInstanceId(),
          ExecutionInputData.builder().inputInstanceId(executionInputInstance.getInputInstanceId()).build());
    } else {
      throw new InvalidRequestException(
          String.format("Execution Input template does not exist for input execution id : %s", nodeExecutionId));
    }
  }

  @Override
  public ExecutionInputInstance getExecutionInputInstance(String nodeExecutionId) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isPresent()) {
      return optional.get();
    }
    throw new InvalidRequestException(
        String.format("Execution Input template does not exist for input execution id : %s", nodeExecutionId));
  }

  @Override
  public ExecutionInputInstance save(ExecutionInputInstance executionInputInstance) {
    return executionInputRepository.save(executionInputInstance);
  }
}
