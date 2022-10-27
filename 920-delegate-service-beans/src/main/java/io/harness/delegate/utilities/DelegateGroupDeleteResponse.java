package io.harness.delegate.utilities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Data;

// this class is used to propagate the error msg from cg manager to ng manager
@Data
@OwnedBy(HarnessTeam.DEL)
public class DelegateGroupDeleteResponse {
  private final String errorMsg;
  private final boolean statusSuccess;
}
