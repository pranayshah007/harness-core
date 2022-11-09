/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.DestroyVmRequest;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmCleanupTaskHandler implements CICleanupTaskHandler {
  @NotNull private Type type = CICleanupTaskHandler.Type.VM;
  @Inject private HttpHelper httpHelper;

  @Override
  public Type getType() {
    return type;
  }

  public VmTaskExecutionResponse executeTaskInternal(CICleanupTaskParams ciCleanupTaskParams, String taskId) {
    CIVmCleanupTaskParams params = (CIVmCleanupTaskParams) ciCleanupTaskParams;
    log.info("Received request to clean VM with stage runtime ID {}", params.getStageRuntimeId());
    return callRunnerForCleanup(params, taskId);
  }

  private VmTaskExecutionResponse callRunnerForCleanup(CIVmCleanupTaskParams params, String taskId) {
    CommandExecutionStatus executionStatus = CommandExecutionStatus.FAILURE;
    String errMessage = "";
    try {
      Response<Void> response = httpHelper.cleanupStageWithRetries(convert(params, taskId));
      if (response.isSuccessful()) {
        executionStatus = CommandExecutionStatus.SUCCESS;
      }
    } catch (Exception e) {
      log.error("Failed to destory VM in runner", e);
      errMessage = e.toString();
    }

    return VmTaskExecutionResponse.builder().errorMessage(errMessage).commandExecutionStatus(executionStatus).build();
  }

  private DestroyVmRequest convert(CIVmCleanupTaskParams params, String taskId) {
    return DestroyVmRequest.builder()
        .poolID(params.getPoolId())
        .id(params.getStageRuntimeId())
        .correlationID(taskId)
        .infraType(params.getInfraInfo().toString())
        .build();
  }
}
