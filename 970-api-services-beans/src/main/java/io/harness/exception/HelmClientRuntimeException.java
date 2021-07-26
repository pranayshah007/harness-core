package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HelmClientRuntimeException extends RuntimeException {
  @Getter final HelmClientException helmClientException;

  public HelmClientRuntimeException(@NotNull HelmClientException helmClientException) {
    this.helmClientException = helmClientException;
  }
}
