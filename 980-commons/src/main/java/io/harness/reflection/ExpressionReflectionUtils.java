/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reflection;

import io.harness.expression.Expression;
import io.harness.expression.Expression.SecretsMode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpressionReflectionUtils {
  public interface Functor {
    String update(SecretsMode mode, String value);
  }

  public interface NestedAnnotationResolver {}

  public static void applyExpression(Object object, Functor functor) {
    ReflectionUtils.<Expression>updateAnnotatedField(Expression.class, object,
        (expression, value) -> functor.update(SecretsMode.valueOf(expression.value()), value));
  }
}
