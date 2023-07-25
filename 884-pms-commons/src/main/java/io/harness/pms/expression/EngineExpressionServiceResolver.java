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
import io.harness.pms.contracts.ambiance.Ambiance;

/**
 * This impl is taking EngineExpressionService to resolve expressions
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExpressionServiceResolver implements EngineExpressionResolver {
  private final EngineExpressionService engineExpressionService;
  private final Ambiance ambiance;

  public EngineExpressionServiceResolver(EngineExpressionService engineExpressionService, Ambiance ambiance) {
    this.engineExpressionService = engineExpressionService;
    this.ambiance = ambiance;
  }

  @Override
  public String renderExpression(String expression, ExpressionMode expressionMode) {
    return engineExpressionService.renderExpression(
        ambiance, expression, ExpressionModeMapper.toExpressionModeProto(expressionMode));
  }

  @Override
  public Object evaluateExpression(String expression, ExpressionMode expressionMode) {
    if (EngineExpressionEvaluator.hasExpressions(expression)) {
      return engineExpressionService.evaluateExpression(
          ambiance, expression, ExpressionModeMapper.toExpressionModeProto(expressionMode));
    }
    return expression;
  }

  @Override
  public Object resolve(Object o, ExpressionMode expressionMode) {
    // Currently resolve on grpc service is not supported
    return o;
  }
}
