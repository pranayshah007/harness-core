/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

public class StrategyExpressionEvaluator extends EngineExpressionEvaluator {
  private Map<String, String> combinations;
  private int currentIteration;
  private int totalIteration;
  private String itemValue;
  public StrategyExpressionEvaluator(
      Map<String, String> combinations, int currentIteration, int totalIteration, String itemValue) {
    super(null);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIteration = totalIteration;
    this.itemValue = itemValue;
  }

  @Override
  protected void initialize() {
    super.initialize();
    // Adding aliases for matrix->strategy.matrix, step->strategy.step and repeat->strategy.repeat. So when the
    // expression starts with matrix or step or repeat, the StrategyUtilFunctor will be used to resolve the values.
    addStaticAlias(StrategyConstants.MATRIX, StrategyConstants.STRATEGY + "." + StrategyConstants.MATRIX);
    addStaticAlias(StrategyConstants.STEP, StrategyConstants.STRATEGY + "." + StrategyConstants.STEP);
    addStaticAlias(StrategyConstants.REPEAT, StrategyConstants.STRATEGY + "." + StrategyConstants.REPEAT);
    addToContext(
        StrategyConstants.STRATEGY, new StrategyUtilFunctor(combinations, currentIteration, totalIteration, itemValue));
  }
}
