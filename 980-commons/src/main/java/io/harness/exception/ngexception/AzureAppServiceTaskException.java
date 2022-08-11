/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.AZURE_APP_SERVICES_TASK_EXCEPTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

@OwnedBy(CDP)
public class AzureAppServiceTaskException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public AzureAppServiceTaskException(String message) {
    super(
        message, null, AZURE_APP_SERVICES_TASK_EXCEPTION, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }

  public AzureAppServiceTaskException(String message, EnumSet<FailureType> failureTypes) {
    super(message, null, AZURE_APP_SERVICES_TASK_EXCEPTION, Level.ERROR, null, failureTypes);
    param(MESSAGE_KEY, message);
  }

  public AzureAppServiceTaskException(String message, Throwable cause) {
    super(message, cause, AZURE_APP_SERVICES_TASK_EXCEPTION, Level.ERROR, null,
        EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }
}
