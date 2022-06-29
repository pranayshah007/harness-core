package io.harness.steps.matrix;

import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;

import io.harness.expression.LateBindingValue;

import java.util.HashMap;
import java.util.Map;

public class StrategyJsonNodeFunctor implements LateBindingValue {
  Map<String, String> matrixCombinations;
  int currentIteration;
  int totalIterations;

  StrategyJsonNodeFunctor(Map<String, String> matrixCombinations, int currentIteration, int totalIterations) {
    this.matrixCombinations = matrixCombinations;
    this.currentIteration = currentIteration;
    this.totalIterations = totalIterations;
  }

  @Override
  public Object bind() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    strategyObjectMap.put(MATRIX, matrixCombinations);
    strategyObjectMap.put("currentIteration", currentIteration);
    strategyObjectMap.put("totalIterations", totalIterations);
    return strategyObjectMap;
  }
}
