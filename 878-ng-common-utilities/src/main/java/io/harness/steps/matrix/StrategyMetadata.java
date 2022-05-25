package io.harness.steps.matrix;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StrategyMetadata {
  String childNodeId;
  String strategyNodeId;
}
