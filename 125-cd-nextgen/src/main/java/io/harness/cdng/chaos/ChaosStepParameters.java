package io.harness.cdng.chaos;

import io.harness.plancreator.steps.common.SpecParameters;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChaosStepParameters implements SpecParameters {
  String experimentRef;
}
