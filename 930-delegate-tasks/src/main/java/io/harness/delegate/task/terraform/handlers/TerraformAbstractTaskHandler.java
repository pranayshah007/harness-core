/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.LogLevel.ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.exception.InterruptedRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public abstract class TerraformAbstractTaskHandler {
  public abstract TerraformTaskNGResponse executeTaskInternal(
      TerraformTaskNGParameters taskParameters, String delegateId, String taskId, LogCallback logCallback)
      throws IOException, TimeoutException, InterruptedException;
  @Inject TerraformBaseHelper terraformBaseHelper;

  public TerraformTaskNGResponse executeTask(TerraformTaskNGParameters taskParameters, String delegateId, String taskId,
      LogCallback logCallback) throws Exception {
    try {
      return executeTaskInternal(taskParameters, delegateId, taskId, logCallback);
    } catch (InterruptedRuntimeException | InterruptedException ex) {
      log.error("Interrupted Exception received: {}", ex.getMessage());
      logCallback.saveExecutionLog("Interrupt received.", ERROR, CommandExecutionStatus.RUNNING);
      throw ex;
    } finally {
      terraformBaseHelper.performCleanupOfTfDirs(taskParameters, logCallback);
    }
  }
}
