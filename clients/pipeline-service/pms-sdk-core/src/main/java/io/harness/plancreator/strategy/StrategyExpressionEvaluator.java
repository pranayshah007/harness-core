/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionEvaluatorResolver;
import io.harness.pms.expression.ParameterFieldResolverFunctor;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;

import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class StrategyExpressionEvaluator extends EngineExpressionEvaluator {
  private Map<String, String> combinations;
  private int currentIteration;
  private int totalIteration;
  private String itemValue;
  private InputSetValidatorFactory inputSetValidatorFactory;
  public StrategyExpressionEvaluator(Map<String, String> combinations, int currentIteration, int totalIteration,
      String itemValue, InputSetValidatorFactory inputSetValidatorFactory) {
    super(null);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIteration = totalIteration;
    this.itemValue = itemValue;
    this.inputSetValidatorFactory = inputSetValidatorFactory;
  }

  public StrategyExpressionEvaluator(Map<String, String> combinations, int currentIteration, int totalIteration,
      String itemValue, Map<String, String> contextMap, InputSetValidatorFactory inputSetValidatorFactory) {
    super(null);
    contextMap.forEach(this::addToContext);
    this.combinations = combinations;
    this.currentIteration = currentIteration;
    this.totalIteration = totalIteration;
    this.itemValue = itemValue;
    this.inputSetValidatorFactory = inputSetValidatorFactory;
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    return ExpressionEvaluatorUtils.updateExpressions(o,
        new ParameterFieldResolverFunctor(
            new EngineExpressionEvaluatorResolver(this), inputSetValidatorFactory, expressionMode));
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
