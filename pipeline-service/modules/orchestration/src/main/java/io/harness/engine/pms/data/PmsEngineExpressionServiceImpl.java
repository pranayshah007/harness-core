/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.ExpressionEvaluatorProvider;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
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
  public Object resolve(Ambiance ambiance, Object o, ExpressionMode expressionMode, List<String> enabledFeatureFlags) {
    Map<String, String> contxtMap = new HashMap<>();
    if (isNotEmpty(enabledFeatureFlags)) {
      contxtMap.put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, String.join(",", enabledFeatureFlags));
    }
    EngineExpressionEvaluator evaluator = prepareExpressionEvaluator(ambiance, contxtMap);
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
    List<String> enabledFeatureFlags = AmbianceUtils.getEnabledFeatureFlags(ambiance);
    if (contextMap != null) {
      String enabledFFsString = contextMap.get(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY);
      if (isNotEmpty(enabledFFsString)) {
        enabledFeatureFlags.addAll(List.of(enabledFFsString.split(",")));
      }
    }
    if (isNotEmpty(enabledFeatureFlags)) {
      if (contextMap == null) {
        contextMap = new HashMap<>();
      }
      contextMap.put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, String.join(",", enabledFeatureFlags));
    }
    EngineExpressionEvaluator engineExpressionEvaluator =
        expressionEvaluatorProvider.get(null, ambiance, null, false, contextMap);
    injector.injectMembers(engineExpressionEvaluator);
    return engineExpressionEvaluator;
  }
}
