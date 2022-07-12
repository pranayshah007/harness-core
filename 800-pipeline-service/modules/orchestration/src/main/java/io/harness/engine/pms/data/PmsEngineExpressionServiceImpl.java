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
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.PmsEngineExpressionService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Injector;

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
  public String evaluateExpression(Ambiance ambiance, String expression) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    Object value = evaluator.evaluateExpression(expression);
    return RecastOrchestrationUtils.toJson(value);
  }

  @Override
  public Object resolve(Ambiance ambiance, Object o, boolean skipUnresolvedExpressionsCheck) {
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance);
    return evaluator.resolve(o, skipUnresolvedExpressionsCheck);
  }

  @Override
  public EngineExpressionEvaluator prepareExpressionEvaluator(Ambiance ambiance) {
    EngineExpressionEvaluator engineExpressionEvaluator = expressionEvaluatorProvider.get(null, ambiance, null, false);
    injector.injectMembers(engineExpressionEvaluator);
    return engineExpressionEvaluator;
  }
}
