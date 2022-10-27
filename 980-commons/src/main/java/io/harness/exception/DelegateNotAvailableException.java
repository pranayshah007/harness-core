/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.DELEGATE_NOT_AVAILABLE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.PIPELINE)
public class DelegateNotAvailableException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public DelegateNotAvailableException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, DELEGATE_NOT_AVAILABLE, Level.ERROR, reportTargets, EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }

  public DelegateNotAvailableException(String message, Throwable cause, EnumSet<ReportTarget> reportTargets) {
    super(
        message, cause, DELEGATE_NOT_AVAILABLE, Level.ERROR, reportTargets, EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }

  public DelegateNotAvailableException(String message, Level level, EnumSet<ReportTarget> reportTargets) {
    super(message, null, DELEGATE_NOT_AVAILABLE, level, reportTargets, EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }

  public DelegateNotAvailableException(String message) {
    super(message, null, DELEGATE_NOT_AVAILABLE, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }
}
