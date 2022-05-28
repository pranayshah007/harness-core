package io.harness.steps.matrix;

import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;

import java.util.List;

public interface StrategyConfigService {
  List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId);
}
