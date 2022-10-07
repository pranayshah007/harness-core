/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody.StatusEnum;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionsApiUtils {
  public static PipelineExecuteResponseBody getExecuteResponseBody(PlanExecutionResponseDto oldExecutionResponseDto) {
    if (oldExecutionResponseDto == null) {
      return null;
    }
    PipelineExecuteResponseBody responseBody = new PipelineExecuteResponseBody();
    responseBody.setSlug(oldExecutionResponseDto.getPlanExecution().getPlanId());
    responseBody.setStarted(oldExecutionResponseDto.getPlanExecution().getStartTs());
    responseBody.setGitDetails(PipelinesApiUtils.getGitDetails(oldExecutionResponseDto.getGitDetails()));
    responseBody.setStatus(ExecutionsApiUtils.getStatus(oldExecutionResponseDto.getPlanExecution().getStatus()));
    return responseBody;
  }

  public static StatusEnum getStatus(Status statusOld) {
    if (statusOld == null) {
      return null;
    }
    return StatusEnum.fromValue(statusOld.name());
  }
}
