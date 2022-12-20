/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate;

import static io.harness.eraro.ErrorCode.DELEGATE_NOT_AVAILABLE;

import io.harness.delegate.beans.NoDelegatesException;

public class NoEligibleDelegatesInAccountException extends NoDelegatesException {
  private static final String MESSAGE_ARG = "message";

  public NoEligibleDelegatesInAccountException(String message) {
    super(message, DELEGATE_NOT_AVAILABLE);
    super.param(MESSAGE_ARG, message);
  }
}
