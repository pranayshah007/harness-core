package io.harness.cdng.chaos;

import io.harness.annotation.RecasterAlias;
import io.harness.plancreator.steps.common.SpecParameters;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.cdng.chaos.ChaosStepParameters")
public class ChaosStepParameters implements SpecParameters {
  String experimentRef;
}
