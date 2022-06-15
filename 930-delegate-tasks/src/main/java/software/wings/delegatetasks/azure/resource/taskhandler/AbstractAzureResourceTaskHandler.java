/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.resource.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.resource.AzureResourceTaskParameters;
import io.harness.delegate.task.azure.resource.AzureResourceTaskResponse;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperation;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperationResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public abstract class AbstractAzureResourceTaskHandler {
  public AzureTaskExecutionResponse executeTask(
      AzureResourceTaskParameters resourceTaskParameters, AzureConfig azureConfig) {
    try {
      AzureResourceOperationResponse operationResponse =
          executeTask(resourceTaskParameters.getResourceOperation(), azureConfig);
      return AzureTaskExecutionResponse.builder()
          .azureTaskResponse(AzureResourceTaskResponse.builder().operationResponse(operationResponse).build())
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .build();
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMsg = String.format("Failed to execute operation, operationName: %s, errorMessage: %s",
          resourceTaskParameters.getResourceOperation().getOperationName(), sanitizedException.getMessage());
      log.error(errorMsg, sanitizedException);
      return AzureTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(errorMsg)
          .build();
    }
  }

  protected abstract AzureResourceOperationResponse executeTask(
      AzureResourceOperation operationRequest, AzureConfig azureConfig);
}
