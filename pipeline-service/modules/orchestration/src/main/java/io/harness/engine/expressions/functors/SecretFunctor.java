/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.utils.FunctorUtils;
import io.harness.expression.functors.ExpressionFunctor;

import lombok.Value;

@OwnedBy(CDC)
@Value
public class SecretFunctor implements ExpressionFunctor {
  long expressionFunctorToken;

  public SecretFunctor(long expressionFunctorToken) {
    this.expressionFunctorToken = expressionFunctorToken;
  }

  public Object getValue(String secretIdentifier) {
    return FunctorUtils.getSecretExpression(expressionFunctorToken, secretIdentifier);
  }
}
