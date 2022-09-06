/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

import static io.harness.eraro.ErrorCode.CONNECTOR_VALIDATION_EXCEPTION;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class ConnectorValidationException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public ConnectorValidationException(String message) {
    super(message, null, CONNECTOR_VALIDATION_EXCEPTION, Level.ERROR, null, null);
    param(MESSAGE_KEY, message);
  }

  public ConnectorValidationException(String message, Throwable cause) {
    super(message, cause, CONNECTOR_VALIDATION_EXCEPTION, Level.ERROR, null, null);
  }
}
