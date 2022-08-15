/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata.exceptions;

import static io.harness.exception.FailureType.APPLICATION_ERROR;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

import java.util.EnumSet;

public class WingsTransactionFailureException extends WingsException {
  private static final String MESSAGE_ARG = "exception_message";

  protected WingsTransactionFailureException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, null, Level.ERROR, reportTargets, EnumSet.of(APPLICATION_ERROR));
    super.param(MESSAGE_ARG, message);
  }
}
