/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.AWS_ASG_ERROR;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;
import java.util.Set;

public class AwsAutoScaleException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public AwsAutoScaleException(String message) {
    this(message, null);
  }

  public AwsAutoScaleException(String message, Throwable th) {
    super(message, th, AWS_ASG_ERROR, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public AwsAutoScaleException(String message, ErrorCode errorCode, Set<ReportTarget> reportTargets) {
    super(message, null, errorCode, Level.ERROR, (EnumSet) reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }
}
