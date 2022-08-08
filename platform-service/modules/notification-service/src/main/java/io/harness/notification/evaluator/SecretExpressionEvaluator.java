/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.evaluator;

import io.harness.expression.EngineExpressionEvaluator;
import io.harness.notification.functor.SecretFunctor;

public class SecretExpressionEvaluator extends EngineExpressionEvaluator {
  private final long expressionFunctorToken;

  public SecretExpressionEvaluator(long expressionFunctorToken) {
    super(null);
    this.expressionFunctorToken = expressionFunctorToken;
  }

  @Override
  protected void initialize() {
    super.initialize();
    this.addToContext("secrets", new SecretFunctor(expressionFunctorToken));
  }
}
