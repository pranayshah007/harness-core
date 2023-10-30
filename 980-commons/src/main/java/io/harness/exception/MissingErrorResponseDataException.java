/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.MISSING_EXCEPTION;

import io.harness.eraro.Level;

/**
 * Ideally, is expected to have an exception when working with {@code ErrorNotifyResponseData} and in case of one is
 * missing, the {@link io.harness.waiter.NotifyEventListenerHelper} throws that exception.
 */
public class MissingErrorResponseDataException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public MissingErrorResponseDataException(String message) {
    super(message, null, MISSING_EXCEPTION, Level.ERROR, NOBODY, null);
    param(MESSAGE_KEY, message);
  }
}
