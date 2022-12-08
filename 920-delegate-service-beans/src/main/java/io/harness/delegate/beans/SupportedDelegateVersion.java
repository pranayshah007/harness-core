package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.DEL)
@Data
@Builder
public class SupportedDelegateVersion {
  private final String latestSupportedVersion;
  private final String latestSupportedMinimalVersion;
}
