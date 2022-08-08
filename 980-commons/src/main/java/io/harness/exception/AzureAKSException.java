/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.CDP)
public class AzureAKSException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public AzureAKSException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, ErrorCode.INVALID_AZURE_AKS_REQUEST, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_KEY, message);
  }

  public AzureAKSException(String message, EnumSet<ReportTarget> reportTargets, Throwable throwable) {
    super(message, throwable, ErrorCode.INVALID_AZURE_AKS_REQUEST, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_KEY, message);
  }

  public AzureAKSException(String message) {
    super(message, null, ErrorCode.INVALID_AZURE_AKS_REQUEST, Level.ERROR, null, null);
    super.param(MESSAGE_KEY, message);
  }
}
