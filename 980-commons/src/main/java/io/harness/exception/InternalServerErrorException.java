/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

// This exception can be used whenever there's an error in communication between internal services, Error code 500 will
// be thrown with this exception.
@OwnedBy(HarnessTeam.PIPELINE)
public class InternalServerErrorException extends WingsException {
  private static final String MESSAGE_ARG = "message";
  public InternalServerErrorException(String message, Throwable cause) {
    super(message, cause, ErrorCode.INTERNAL_SERVER_ERROR, Level.ERROR, USER, null);
    super.param(MESSAGE_ARG, message);
  }
}
