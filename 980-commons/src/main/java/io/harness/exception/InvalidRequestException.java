/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import java.util.EnumSet;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_FIRST_GEN, HarnessModuleComponent.CDS_PIPELINE})
public class InvalidRequestException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public InvalidRequestException(String message, ErrorMetadataDTO metadata) {
    super(message, null, INVALID_REQUEST, Level.ERROR, null, null, metadata);
    super.param(MESSAGE_ARG, message);
  }

  // This method does not create the intended message, needs to be fixed @George
  public InvalidRequestException(String message) {
    super(message, null, INVALID_REQUEST, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidRequestException(String message, Throwable cause) {
    super(message, cause, INVALID_REQUEST, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidRequestException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, INVALID_REQUEST, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidRequestException(String message, Throwable cause, EnumSet<ReportTarget> reportTargets) {
    super(message, cause, INVALID_REQUEST, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidRequestException(String message, ErrorCode errorCode, EnumSet<ReportTarget> reportTargets) {
    super(message, null, errorCode, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }

  public InvalidRequestException(
      String message, Throwable cause, ErrorCode errorCode, EnumSet<ReportTarget> reportTargets) {
    super(message, cause, errorCode, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }
}
