package io.harness.steps.matrix;

import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;

import io.harness.expression.EngineExpressionEvaluator;

import java.util.HashMap;
import java.util.Map;

public class StrategyExpressionEvaluator extends EngineExpressionEvaluator {
  private final Map<String, String> combinations;
  private final int currentIteration;
  private final int totalIterations;

  public StrategyExpressionEvaluator(Map<String, String> combinations, int currentIteration, int totalIterations) {
    super(null);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIterations = totalIterations;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext("strategy", new StrategyJsonNodeFunctor(combinations, currentIteration, totalIterations));
    this.addStaticAlias(MATRIX, "strategy.matrix");
  }

  @Override
  public Map<String, String> getStaticAliases() {
    Map<String, String> aliasMap = new HashMap<>();
    return aliasMap;
  }
}
