package io.harness.steps.matrix;

import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyStepParameters implements StepParameters {
  StrategyConfig strategyConfig;
  String childNodeId;
}
