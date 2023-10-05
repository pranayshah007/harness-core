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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigratorResolveFunctor implements ExpressionResolveFunctor {
  private final Map<String, Object> context;

  private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
  private static final String REPLACE_EXPRESSION = "\\.replace\\(.*?\\)";
  private static final String SPLIT_EXPRESSION = "\\.split\\(.*?\\)";
  private static final String LOWER_EXPRESSION = ".toLowerCase()";
  private static final String UPPER_EXPRESSION = ".toUpperCase()";

  private static final Pattern REPLACE_PATTERN = Pattern.compile(REPLACE_EXPRESSION);
  private static final Pattern SPLIT_PATTERN = Pattern.compile(SPLIT_EXPRESSION);

  public MigratorResolveFunctor(Map<String, Object> context) {
    this.context = context;
  }

  @Override
  public String processString(String expression) {
    Matcher replaceMatcher = REPLACE_PATTERN.matcher(expression);
    Matcher splitMatcher = SPLIT_PATTERN.matcher(expression);
    if (expression.contains(LOWER_EXPRESSION)) {
      String treatedExpression = expression.replace(LOWER_EXPRESSION, "");
      return expressionEvaluator.substitute(treatedExpression, context) + LOWER_EXPRESSION;
    }
    if (expression.contains(UPPER_EXPRESSION)) {
      String treatedExpression = expression.replace(UPPER_EXPRESSION, "");
      return expressionEvaluator.substitute(treatedExpression, context) + UPPER_EXPRESSION;
    }
    if (replaceMatcher.find()) {
      String replaceContent = replaceMatcher.group();
      String treatedExpression = expression.replaceAll(REPLACE_EXPRESSION, "");
      return expressionEvaluator.substitute(treatedExpression, context) + replaceContent;
    }
    if (splitMatcher.find()) {
      String splitContent = splitMatcher.group();
      String treatedExpression = expression.replaceAll(SPLIT_EXPRESSION, "");
      return expressionEvaluator.substitute(treatedExpression, context) + splitContent;
    }
    return expressionEvaluator.substitute(expression, context);
  }
}
