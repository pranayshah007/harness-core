/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceDriverException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public DelegateServiceDriverException(String message, Throwable cause) {
    super(message, cause, ErrorCode.DELEGATE_SERVICE_DRIVER_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public DelegateServiceDriverException(String message) {
    super(message, (Throwable) null, ErrorCode.DELEGATE_SERVICE_DRIVER_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
