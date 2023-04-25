/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsEngineExpressionService {
  default String renderExpression(Ambiance ambiance, String expression) {
    return renderExpression(ambiance, expression, false);
  }
  String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck);
  String renderExpression(Ambiance ambiance, String expression, ExpressionMode expressionMode);

  String evaluateExpression(Ambiance ambiance, String expression);
  String evaluateExpression(Ambiance ambiance, String expression, ExpressionMode expressionMode);
  @Deprecated Object resolve(Ambiance ambiance, Object o, boolean skipUnresolvedExpressionsCheck);
  Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode);
  Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode, Map<String, String> contextMap);
  Object evaluateExpression(
      Ambiance ambiance, String expression, ExpressionMode expressionMode, Map<String, String> contextMap);

  default EngineExpressionEvaluator prepareExpressionEvaluator(Ambiance ambiance) {
    return null;
  }
}
