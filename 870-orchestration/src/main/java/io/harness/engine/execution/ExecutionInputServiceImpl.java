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
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputServiceImpl implements ExecutionInputService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject ExecutionInputRepository executionInputRepository;
  @Override
  // TODO(BRIJESH): Use lock so that only one input can be processed and only one doneWith should be called.
  public boolean continueExecution(String nodeExecutionId, String executionInputYaml) {
    ExecutionInputInstance executionInputInstance;
    try {
      executionInputInstance = mergeUserInputInTemplate(nodeExecutionId, executionInputYaml);
    } catch (NoSuchElementException ex) {
      log.error("User input could not be processed for nodeExecutionId {}", nodeExecutionId, ex);
      return false;
    }
    waitNotifyEngine.doneWith(executionInputInstance.getInputInstanceId(),
        ExecutionInputData.builder().inputInstanceId(executionInputInstance.getInputInstanceId()).build());
    return true;
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

  @Override
  public List<ExecutionInputInstance> getExecutionInputInstances(Set<String> nodeExecutionIds) {
    return executionInputRepository.findByNodeExecutionIds(nodeExecutionIds);
  }

  private ExecutionInputInstance mergeUserInputInTemplate(String nodeExecutionId, String executionInputYaml) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isPresent()) {
      ExecutionInputInstance executionInputInstance = optional.get();
      executionInputInstance.setUserInput(executionInputYaml);

      JsonNode mergedJsonNode = MergeHelper.mergeRuntimeInputIntoOriginalYamlJsonNode(
          executionInputInstance.getTemplate(), executionInputInstance.getUserInput(), false);
      executionInputInstance.setMergedInputTemplate(RecastOrchestrationUtils.fromJson(mergedJsonNode.toString()));
      return executionInputRepository.save(executionInputInstance);
    } else {
      throw new InvalidRequestException(
          String.format("Execution Input template does not exist for input execution id : %s", nodeExecutionId));
    }
  }
}
