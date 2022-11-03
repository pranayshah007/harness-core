/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.eraro.ErrorCode.INVALID_REQUEST;

import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class DelegateTaskInvalidRequestException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public DelegateTaskInvalidRequestException(String taskId) {
    super(taskId, null, INVALID_REQUEST, Level.ERROR, null, EnumSet.of(FailureType.EXPIRED));
    param(MESSAGE_KEY, taskId);
  }
}
