/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;

/**
 * This is the base interface created to select expression resolution method between EngineExpressionEvaluator(Local
 * evaluator) or EngineExpressionService(Grpc call)
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface EngineExpressionResolver {
  String renderExpression(String expression, ExpressionMode expressionMode);

  Object evaluateExpression(String expression, ExpressionMode expressionMode);

  Object resolve(Object o, ExpressionMode expressionMode);
}
