/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.app.resources;

import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.ci.pipeline.executions.beans.ExecutionQueueLimitDTO;
import io.harness.ci.pipeline.executions.beans.ExecutionQueueLimitResource;
import io.harness.exception.EntityNotFoundException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.repositories.ExecutionQueueLimitRepository;

import com.google.inject.Inject;
import java.util.Optional;

public class ExecutionQueueLimitResourceImpl implements ExecutionQueueLimitResource {
  @Inject ExecutionQueueLimitRepository executionQueueLimitRepository;

  @Override
  public ResponseDTO<Boolean> updateExecutionLimits(
      String accountIdentifier, ExecutionQueueLimitDTO executionQueueLimitDTO) {
    Optional<ExecutionQueueLimit> firstByAccountIdentifier =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountIdentifier);
    if (firstByAccountIdentifier.isPresent()) {
      executionQueueLimitRepository.deleteById(firstByAccountIdentifier.get().getUuid());
    }
    ExecutionQueueLimit build = ExecutionQueueLimit.builder()
                                    .macExecLimit(executionQueueLimitDTO.getMacExecutionLimits())
                                    .totalExecLimit(executionQueueLimitDTO.getTotalExecutionLimits())
                                    .accountIdentifier(accountIdentifier)
                                    .build();
    ExecutionQueueLimit save = executionQueueLimitRepository.save(build);
    return ResponseDTO.newResponse(true);
  }

  @Override
  public ResponseDTO<ExecutionQueueLimitDTO> getExecutionLimits(String accountIdentifier) {
    Optional<ExecutionQueueLimit> firstByAccountIdentifier =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountIdentifier);
    if (firstByAccountIdentifier.isPresent()) {
      ExecutionQueueLimit executionQueueLimit = firstByAccountIdentifier.get();
      return ResponseDTO.newResponse(ExecutionQueueLimitDTO.builder()
                                         .macExecutionLimits(executionQueueLimit.getMacExecLimit())
                                         .totalExecutionLimits(executionQueueLimit.getTotalExecLimit())
                                         .build());
    } else {
      throw new EntityNotFoundException(
          String.format("no execution config found for accountId: %s", accountIdentifier));
    }
  }
}
