/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;

public class GitOperationException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public GitOperationException(String message) {
    this(message, null);
  }

  public GitOperationException(String message, Throwable cause) {
    super(null, cause, ErrorCode.GIT_OPERATION_ERROR, Level.ERROR, USER_SRE, EnumSet.of(FailureType.APPLICATION_ERROR));
    super.param(MESSAGE_ARG, message);
  }
}
