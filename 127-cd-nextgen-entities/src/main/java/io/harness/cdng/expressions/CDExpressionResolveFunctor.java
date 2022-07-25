/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.expressions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.yaml.ParameterField;

@OwnedBy(CDP)
public class CDExpressionResolveFunctor implements ExpressionResolveFunctor {
  private final EngineExpressionService engineExpressionService;
  private final Ambiance ambiance;

  public CDExpressionResolveFunctor(EngineExpressionService engineExpressionService, Ambiance ambiance) {
    this.engineExpressionService = engineExpressionService;
    this.ambiance = ambiance;
  }

  @Override
  public String processString(String expression) {
    if (EngineExpressionEvaluator.hasExpressions(expression)) {
      return engineExpressionService.renderExpression(ambiance, expression);
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
