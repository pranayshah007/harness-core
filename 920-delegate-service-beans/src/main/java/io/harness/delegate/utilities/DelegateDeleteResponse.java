package io.harness.delegate.utilities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Data;

@Data
@OwnedBy(HarnessTeam.DEL)
public class DelegateDeleteResponse {
  private final String responseMsg;
}
