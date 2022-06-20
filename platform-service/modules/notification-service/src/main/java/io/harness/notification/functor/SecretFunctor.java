package io.harness.notification.functor;

import io.harness.expression.ExpressionFunctor;

import lombok.Value;

@Value
public class SecretFunctor implements ExpressionFunctor {
  long expressionFunctorToken;

  public SecretFunctor(long expressionFunctorToken) {
    this.expressionFunctorToken = expressionFunctorToken;
  }

  public Object getValue(String secretIdentifier) {
    return "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
  }
}