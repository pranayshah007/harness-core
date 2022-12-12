package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class MaxStepTimeoutInHours {
  long free;
  long team;
  long enterprise;
}
