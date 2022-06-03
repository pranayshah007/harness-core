package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;

@OwnedBy(HarnessTeam.PL)
public class ScmResourceNotFoundException extends ScmException {
  public ScmResourceNotFoundException(String errorMessage) {
    super(errorMessage, ErrorCode.SCM_NOT_FOUND_ERROR);
  }
}
