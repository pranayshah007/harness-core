/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformCloudCleanupTaskNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Supplier<ResponseData> responseSupplier = response.values().stream().findFirst().get();
    try {
      TerraformCloudCleanupTaskResponse responseData = (TerraformCloudCleanupTaskResponse) responseSupplier.get();
      if (CommandExecutionStatus.SUCCESS.equals(responseData.getCommandExecutionStatus())) {
        log.info("Received success response terraform cloud cleanup for runId: {}", responseData.getRunId());
      } else {
        log.error(String.format("Failed to discard run with runId: %s because of: %s", responseData.getRunId(),
            responseData.getErrorMessage()));
      }
    } catch (Exception exception) {
      log.error("Something went wrong for terraform cloud cleanup: {}", exception.getMessage());
    }
  }
}
