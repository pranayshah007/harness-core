/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.eraro.ErrorCode;

import java.util.EnumSet;

public class ServiceNowOIDCException extends ServiceNowException {
  public ServiceNowOIDCException(String message, ErrorCode code, EnumSet<ReportTarget> reportTargets) {
    super(message, code, reportTargets, EnumSet.of(FailureType.AUTHORIZATION_ERROR));
  }

  public ServiceNowOIDCException(String message, ErrorCode code, EnumSet<ReportTarget> reportTargets, Throwable cause) {
    super(message, code, reportTargets, cause, EnumSet.of(FailureType.AUTHORIZATION_ERROR));
  }
}
