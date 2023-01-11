/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.DELEGATE_INSTALLATION_COMMAND_NOT_SUPPORTED_EXCEPTION;

import io.harness.eraro.Level;

import java.util.EnumSet;

public class DelegateInstallationCommandNotSupportedException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public DelegateInstallationCommandNotSupportedException(String message) {
    super(message, null, DELEGATE_INSTALLATION_COMMAND_NOT_SUPPORTED_EXCEPTION, Level.ERROR, null,
        EnumSet.of(FailureType.APPLICATION_ERROR));
    super.param(MESSAGE_KEY, message);
  }
}
