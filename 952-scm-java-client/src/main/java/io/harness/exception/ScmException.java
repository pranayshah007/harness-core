/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

@OwnedBy(DX)
public class ScmException extends WingsException {
  public ScmException(ErrorCode errorCode) {
    super("", null, errorCode, Level.ERROR, USER, null);
  }

  public ScmException(String message, Throwable cause, ErrorCode errorCode) {
    super(message, cause, errorCode, Level.ERROR, USER, null);
  }

  public ScmException(String errorMessage, ErrorCode errorCode) {
    super(errorMessage, null, errorCode, Level.ERROR, USER, null);
  }
}
