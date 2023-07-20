/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;

/**
 * This impl is taking EngineExpressionEvaluator to resolve expressions
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExpressionEvaluatorResolver implements EngineExpressionResolver {
  private final EngineExpressionEvaluator engineExpressionEvaluator;

  public EngineExpressionEvaluatorResolver(EngineExpressionEvaluator engineExpressionEvaluator) {
    this.engineExpressionEvaluator = engineExpressionEvaluator;
  }

  @Override
  public String renderExpression(String expression, ExpressionMode expressionMode) {
    return engineExpressionEvaluator.renderExpression(expression, expressionMode);
  }

  @Override
  public Object evaluateExpression(String expression, ExpressionMode expressionMode) {
    return engineExpressionEvaluator.evaluateExpression(expression, expressionMode);
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    return engineExpressionEvaluator.resolve(o, expressionMode);
  }
}
