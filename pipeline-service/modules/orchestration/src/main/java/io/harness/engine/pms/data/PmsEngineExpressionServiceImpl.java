/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsEngineExpressionServiceImpl implements PmsEngineExpressionService {
  @Inject private ExpressionEvaluatorProvider expressionEvaluatorProvider;
  @Inject private Injector injector;

  @Override
  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.renderExpression(expression, skipUnresolvedExpressionsCheck);
  }

  @Override
  public String renderExpression(Ambiance ambiance, String expression, ExpressionMode expressionMode) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.renderExpression(expression, expressionMode);
  }

  @Override
  public String evaluateExpression(Ambiance ambiance, String expression) {
    return evaluateExpression(ambiance, expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
  }

  @Override
  public String evaluateExpression(Ambiance ambiance, String expression, ExpressionMode expressionMode) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    Object value = evaluator.evaluateExpression(expression, expressionMode);
    return RecastOrchestrationUtils.toJson(value);
  }

  @Override
  @Deprecated
  public Object resolve(Ambiance ambiance, Object o, boolean skipUnresolvedExpressionsCheck) {
    return resolve(ambiance, o, EngineExpressionEvaluator.calculateExpressionMode(skipUnresolvedExpressionsCheck));
  }

  @Override
  public Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.resolve(o, expressionMode);
  }

  @Override
  public Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode, Map<String, String> contextMap) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap);
    return evaluator.resolve(o, expressionMode);
  }

  @Override
  public Object evaluateExpression(
      Ambiance ambiance, String expression, ExpressionMode expressionMode, Map<String, String> contextMap) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contextMap);
    return evaluator.evaluateExpression(expression, expressionMode);
  }

  @Override
  public EngineExpressionEvaluator prepareExpressionEvaluator(Ambiance ambiance) {
    return prepareExpressionEvaluator(ambiance, null);
  }
  public EngineExpressionEvaluator prepareExpressionEvaluator(Ambiance ambiance, Map<String, String> contextMap) {
    EngineExpressionEvaluator engineExpressionEvaluator =
        expressionEvaluatorProvider.get(null, ambiance, null, false, contextMap);
    injector.injectMembers(engineExpressionEvaluator);
    return engineExpressionEvaluator;
  }
}
