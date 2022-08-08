/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception.runtime;

import static io.harness.eraro.ErrorCode.INVALID_REQUEST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.FailureType;
import io.harness.exception.WingsException;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.CDP)
public class SshCommandExecutionException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public SshCommandExecutionException(String message) {
    this(message, null);
  }

  public SshCommandExecutionException(String message, Throwable cause) {
    super(message, cause, INVALID_REQUEST, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    super.param(MESSAGE_ARG, message);
  }
}
