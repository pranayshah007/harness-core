package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ScmException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExceptionsHelper {
  public ScmException getScmException(Throwable ex) {
    while (ex != null) {
      if (ex instanceof ScmException) {
        return (ScmException) ex;
      }
      ex = ex.getCause();
    }
    return null;
  }
}
