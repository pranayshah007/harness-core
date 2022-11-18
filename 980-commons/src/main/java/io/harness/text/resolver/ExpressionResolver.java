/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.text.resolver;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionConstants;
import io.harness.expression.common.ExpressionMode;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ExpressionResolver {
  String nullStringValue = "null";
  String resolveInternal(String expression);
  default String resolve(String expression) {
    String resolvedExpression = resolveInternal(expression);
    // If ExpressionMode is ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED and expression is resolved to
    // null value(means unresolved), do not replace with null value, instead keep the original expression as is.
    if (getExpressionMode() == ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
        && (resolvedExpression == null || resolvedExpression.equals(nullStringValue))) {
      return ExpressionConstants.EXPR_START + expression + ExpressionConstants.EXPR_END;
    }
    return resolvedExpression;
  }
  default ExpressionMode getExpressionMode() {
    return ExpressionMode.RETURN_NULL_IF_UNRESOLVED;
  }
}
