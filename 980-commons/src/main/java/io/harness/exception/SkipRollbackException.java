package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

@OwnedBy(HarnessTeam.CDP)
public class SkipRollbackException extends WingsException {
  public SkipRollbackException(String message) {
    super(message, null, ErrorCode.GENERAL_ERROR, Level.INFO, NOBODY, null);
  }
}
