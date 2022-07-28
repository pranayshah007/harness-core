package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitYamlException extends InvalidRequestException {
  public GitYamlException(String message) {
    super(message);
  }
}
