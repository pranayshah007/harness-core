/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionInputInstance;
import io.harness.pms.plan.execution.beans.dto.ExecutionInputDTO;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExecutionInputDTOMapper {
  public ExecutionInputDTO toExecutionInputDTO(ExecutionInputInstance executionInputInstance) {
    return ExecutionInputDTO.builder()
        .nodeExecutionId(executionInputInstance.getNodeExecutionId())
        .inputTemplate(executionInputInstance.getTemplate())
        .inputInstanceId(executionInputInstance.getInputInstanceId())
        .build();
  }
}
