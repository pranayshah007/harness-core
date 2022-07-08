/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.exception;

import static io.harness.eraro.ErrorCode.PMS_INITIALIZE_SDK_EXCEPTION;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class InitializeSdkException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public InitializeSdkException(String message) {
    super(message, null, PMS_INITIALIZE_SDK_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
