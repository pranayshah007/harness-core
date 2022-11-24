package io.harness.ci.enforcement;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CI)
public interface CIBuildEnforcer {
  boolean checkBuildEnforcement(String accountID);
}
