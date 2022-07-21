/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.docker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.docker.CIDockerCleanupTaskParams;
import io.harness.delegate.beans.ci.docker.ExecuteStepDockerResponse;
import io.harness.delegate.beans.ci.docker.DestroyDockerRequest;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.docker.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIDockerCleanupTaskHandler implements CICleanupTaskHandler {
  @NotNull private Type type = Type.DOCKER;
  @Inject private HttpHelper httpHelper;

  @Override
  public Type getType() {
    return type;
  }

  public ExecuteStepDockerResponse executeTaskInternal(CICleanupTaskParams ciCleanupTaskParams, String taskId) {
    CIDockerCleanupTaskParams params = (CIDockerCleanupTaskParams) ciCleanupTaskParams;
    log.info("Received request to clean docker with stage runtime ID {}", params.getStageRuntimeId());
    return callRunnerForCleanup(params, taskId);
  }

  private ExecuteStepDockerResponse callRunnerForCleanup(CIDockerCleanupTaskParams params, String taskId) {
    CommandExecutionStatus executionStatus = CommandExecutionStatus.FAILURE;
    String errMessage = "";
    try {
      //TODO:xun need to move the common lib to upper folder
      Response<Void> response = httpHelper.cleanupStageWithRetries(convert(params, taskId));
      if (response.isSuccessful()) {
        executionStatus = CommandExecutionStatus.SUCCESS;
      }
    } catch (Exception e) {
      log.error("Failed to destory docker in runner", e);
      errMessage = e.toString();
    }

    return ExecuteStepDockerResponse.builder().errorMessage(errMessage).commandExecutionStatus(executionStatus).build();
  }

  private DestroyDockerRequest convert(CIDockerCleanupTaskParams params, String taskId) {
    return DestroyDockerRequest.builder()
        .poolID(params.getPoolId())
        .id(params.getStageRuntimeId())
        .correlationID(taskId)
        .build();
  }
}
