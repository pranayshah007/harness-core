/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupTaskResponse;
import io.harness.exception.TerraformSecretCleanupFailureException;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class TerraformSecretCleanupTaskNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    Supplier<ResponseData> responseSupplier = response.values().stream().findFirst().get();
    try {
      TerraformSecretCleanupTaskResponse responseData = (TerraformSecretCleanupTaskResponse) responseSupplier.get();
      if (responseData.getSecretCleanupFailureDetailsList().isEmpty()) {
        log.info("Received success response terraform secret cleanup for cleanup Uuid: {}",
            responseData.getResponseDataUuid());
      } else {
        responseData.getSecretCleanupFailureDetailsList().forEach(failureDetail -> {
          log.error(
              String.format("Failed to cleanup terraform plan secret with uuid: %s because of exception message: %s",
                  failureDetail.getEncryptedRecordData().getUuid(), failureDetail.getExceptionMessage()));
        });
      }
    } catch (Exception exception) {
      if (exception instanceof TerraformSecretCleanupFailureException) {
        log.error("Failure Message: {} and exception {}", exception.getMessage(), exception);
      } else {
        log.error("Something went wrong for terraform vault secret cleanup ", exception);
      }
    }
  }
}
