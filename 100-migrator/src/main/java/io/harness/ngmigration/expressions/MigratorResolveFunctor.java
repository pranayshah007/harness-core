/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions;

import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionResolveFunctor;

import java.util.Map;

public class MigratorResolveFunctor implements ExpressionResolveFunctor {
  private final Map<String, Object> context;

  private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

  public MigratorResolveFunctor(Map<String, Object> context) {
    this.context = context;
  }

  @Override
  public String processString(String expression) {
    if (expression.contains(".toLowerCase()")) {
      String treatedExpression = expression.replace(".toLowerCase()", "");
      return expressionEvaluator.substitute(treatedExpression, context) + ".toLowerCase()";
    }
    return expressionEvaluator.substitute(expression, context);
  }
}
