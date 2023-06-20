/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.yaml.ParameterField;

public class PluginExpressionResolveFunctor implements ExpressionResolveFunctor {
  private final EngineExpressionService engineExpressionService;
  private final Ambiance ambiance;
  private final io.harness.pms.contracts.plan.ExpressionMode expressionMode;

  public PluginExpressionResolveFunctor(EngineExpressionService engineExpressionService, Ambiance ambiance) {
    this(engineExpressionService, ambiance,
        io.harness.pms.contracts.plan.ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }

  public PluginExpressionResolveFunctor(
      EngineExpressionService engineExpressionService, Ambiance ambiance, ExpressionMode expressionMode) {
    this.engineExpressionService = engineExpressionService;
    this.ambiance = ambiance;
    this.expressionMode = expressionMode;
  }

  @Override
  public String processString(String expression) {
    if (EngineExpressionEvaluator.hasExpressions(expression)) {
      return engineExpressionService.renderExpression(ambiance, expression, expressionMode);
    }

    return expression;
  }

  @Override
  public ResolveObjectResponse processObject(Object o) {
    if (!(o instanceof ParameterField)) {
      return new ResolveObjectResponse(false, null);
    }

    ParameterField<?> parameterField = (ParameterField<?>) o;

    if (!parameterField.isExpression()) {
      return new ResolveObjectResponse(false, null);
    }

    String processedExpressionValue = processString(parameterField.getExpressionValue());
    parameterField.updateWithValue(processedExpressionValue);

    return new ResolveObjectResponse(true, parameterField);
  }
}
