/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.wait;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.steps.wait.WaitStepService;
import io.harness.wait.WaitStepInstance;

import com.google.inject.Inject;

public class WaitStepResourceImpl implements WaitStepResource {
  @Inject WaitStepService waitStepService;

  @Override
  public ResponseDTO<WaitStepResponseDto> markAsFailOrSuccess(
      String accountId, String orgId, String projectId, String nodeExecutionId, WaitStepRequestDto waitStepRequestDto) {
    waitStepService.markAsFailOrSuccess(
        nodeExecutionId, WaitStepActionMapper.mapWaitStepAction(waitStepRequestDto.getAction()));
    return ResponseDTO.newResponse(WaitStepResponseDto.builder().status(true).build());
  }

  @Override
  public ResponseDTO<WaitStepExecutionDetailsDto> getWaitStepExecutionDetails(
      String accountId, String orgId, String projectId, String nodeExecutionId) {
    WaitStepInstance waitStepInstance = waitStepService.getWaitStepExecutionDetails(nodeExecutionId);
    return ResponseDTO.newResponse(WaitStepExecutionDetailsDto.builder()
                                       .createdAt(waitStepInstance.getCreatedAt())
                                       .duration(waitStepInstance.getDuration())
                                       .nodeExecutionId(waitStepInstance.getNodeExecutionId())
                                       .build());
  }
}
