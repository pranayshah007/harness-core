/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.text.resolver;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.text.StringReplacer;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * TrackingExpressionResolver keeps track of all the expressions in the given input.
 *
 * The constructor takes 4 arguments:
 * - expressionPrefix: expression prefix (like <+)
 * - expressionSuffix: expression suffix (like >)
 * - wrapExpressions: If source is "abc <+def>" -> should the expression be "def" or "<+def>". If wrapExpressions is
 *   true, latter is returned
 * - onlyVariables: If source is "abc <+<+def> + <+ghi + <+jkl>>>" -> should the expressions be
 *   ["<+def> + <+ghi + <+jkl>>"] or ["def", "jkl"]. If onlyVariables is true, latter is returned
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class TrackingExpressionResolver implements ExpressionResolver {
  String expressionPrefix;
  String expressionSuffix;
  boolean wrapExpressions;
  boolean onlyVariables;
  List<String> expressions = new ArrayList<>();

  @Override
  public String resolveInternal(String expression) {
    if (onlyVariables) {
      List<String> nested = findExpressions(expressionPrefix, expressionSuffix, false, false, expression);
      if (EmptyPredicate.isNotEmpty(nested)) {
        nested.forEach(this::resolveInternal);
        return "";
      }
    }

    if (wrapExpressions) {
      String expr = createExpression(expressionPrefix, expressionSuffix, expression);
      if (expr != null) {
        expressions.add(expr);
      }
    } else {
      expressions.add(expression);
    }
    return "";
  }

  /**
   * Find all the expressions in the given source string.
   *
   * @param expressionPrefix the expression prefix
   * @param expressionSuffix the expression suffix
   * @param wrapExpressions  should wrap returned expressions with delimiters. See
   *                         {@link TrackingExpressionResolver#wrapExpressions}
   * @param onlyVariables    should return only variables or whole expressions. See
   *                         {@link TrackingExpressionResolver#onlyVariables}
   * @param source           the source string we want to check
   * @return the list of expressions found
   */
  public static List<String> findExpressions(
      String expressionPrefix, String expressionSuffix, boolean wrapExpressions, boolean onlyVariables, String source) {
    if (EmptyPredicate.isEmpty(source)) {
      return new ArrayList<>();
    }

    TrackingExpressionResolver resolver =
        new TrackingExpressionResolver(expressionPrefix, expressionSuffix, wrapExpressions, onlyVariables);
    StringReplacer replacer = new StringReplacer(resolver, expressionPrefix, expressionSuffix);
    replacer.replace(source);
    return resolver.getExpressions();
  }

  /**
   * Returns true if the source string is of the form "<+expr>", ie. there is a single expression and nothing else.
   *
   * @param expressionPrefix the expression prefix
   * @param expressionSuffix the expression suffix
   * @param source           the source string we want to check
   * @return true is source is a single expression, false otherwise
   */
  public static boolean isSingleExpression(String expressionPrefix, String expressionSuffix, String source) {
    if (EmptyPredicate.isEmpty(source)) {
      return false;
    }

    TrackingExpressionResolver resolver =
        new TrackingExpressionResolver(expressionPrefix, expressionSuffix, true, false);
    StringReplacer replacer = new StringReplacer(resolver, expressionPrefix, expressionSuffix);
    String resp = replacer.replace(source);
    return resolver.getExpressions().size() == 1 && resp.equals("");
  }

  public static String createExpression(String expressionPrefix, String expressionSuffix, String expr) {
    return expr == null ? null : expressionPrefix + expr + expressionSuffix;
  }
}
