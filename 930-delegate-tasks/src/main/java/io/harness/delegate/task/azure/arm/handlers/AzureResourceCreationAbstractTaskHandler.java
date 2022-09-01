/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.arm.AzureResourceCreationBaseHelper;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.TimeoutException;
import io.harness.logging.LogLevel;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public abstract class AzureResourceCreationAbstractTaskHandler {
  @Inject AzureResourceCreationBaseHelper azureARMBaseHelper;

  public abstract AzureResourceCreationTaskNGResponse executeTaskInternal(
      AzureResourceCreationTaskNGParameters taskNGParameters, String delegateId, String taskId,
      AzureLogCallbackProvider logCallback) throws IOException, TimeoutException, InterruptedException;

  public AzureResourceCreationTaskNGResponse executeTask(AzureResourceCreationTaskNGParameters azureTaskNGParameters,
      String delegateId, String taskId, AzureLogCallbackProvider logCallback) throws Exception {
    AzureResourceCreationTaskNGResponse response =
        executeTaskInternal(azureTaskNGParameters, delegateId, taskId, logCallback);
    if (SUCCESS.equals(response.getCommandExecutionStatus())) {
      logCallback.obtainLogCallback("Create").saveExecutionLog("Execution finished successfully.", LogLevel.INFO);
    } else {
      logCallback.obtainLogCallback("Create").saveExecutionLog("Execution has been failed.", LogLevel.ERROR);
    }
    return response;
  }
}
