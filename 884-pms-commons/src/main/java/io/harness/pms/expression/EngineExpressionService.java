/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;

@OwnedBy(HarnessTeam.PIPELINE)
public interface EngineExpressionService {
  default String renderExpression(Ambiance ambiance, String expression) {
    return renderExpression(ambiance, expression, true);
  }
  String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck);
  String renderExpression(Ambiance ambiance, String expression, ExpressionMode mode);

  Object evaluateExpression(Ambiance ambiance, String expression);
}
