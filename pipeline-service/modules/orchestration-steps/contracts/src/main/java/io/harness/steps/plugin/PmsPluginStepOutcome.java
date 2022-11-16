package io.harness.steps.plugin;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDC)
@RecasterAlias("io.harness.steps.plugin.PmsPluginStepOutcome")
public class PmsPluginStepOutcome implements Outcome {
  // todo(abhinav): implement
}
