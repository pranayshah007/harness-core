/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.expression.field.dummy;

import io.harness.expression.ExpressionEvaluatorUtils;

import lombok.Getter;

@Getter
public class DummyOrchestrationField<T> {
  private boolean isExpression;
  private String expressionValue;
  private boolean isString;
  private T value;

  public DummyOrchestrationField(boolean isExpression, String expressionValue, T value) {
    this.isExpression = isExpression;
    this.expressionValue = expressionValue;
    this.value = value;
  }

  public static <T> DummyOrchestrationField<T> createExpressionField(String expressionValue) {
    return new DummyOrchestrationField<>(true, expressionValue, null);
  }

  public static <T> DummyOrchestrationField<T> createValueField(T value) {
    return new DummyOrchestrationField<>(false, null, value);
  }

  public Object get(String key) {
    return isExpression ? expressionValue : ExpressionEvaluatorUtils.fetchField(value, key).orElse(null);
  }

  public void updateWithExpression(String newExpression) {
    isExpression = true;
    expressionValue = newExpression;
  }

  public void updateWithValue(Object newValue) {
    isExpression = false;
    value = (T) newValue;
  }

  public Object fetchFinalValue() {
    return isExpression ? expressionValue : value;
  }
}
