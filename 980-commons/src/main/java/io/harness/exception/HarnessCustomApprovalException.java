/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.CUSTOM_APPROVAL_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(CDC)
public class HarnessCustomApprovalException extends WingsException {
  public HarnessCustomApprovalException(String message, Throwable cause, EnumSet<ReportTarget> reportTargets) {
    super(message, cause, CUSTOM_APPROVAL_ERROR, Level.ERROR, reportTargets, null);
    super.param("message", message);
  }

  public HarnessCustomApprovalException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, CUSTOM_APPROVAL_ERROR, Level.ERROR, reportTargets, null);
    super.param("message", message);
  }

  public HarnessCustomApprovalException(String message) {
    super(message, null, CUSTOM_APPROVAL_ERROR, Level.ERROR, null, null);
    super.param("message", message);
  }
}
