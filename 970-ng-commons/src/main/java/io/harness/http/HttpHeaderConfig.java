/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.http;

import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;

import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.yaml.core.VariableExpression;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HttpHeaderConfig implements NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) @VariableExpression(skipVariableExpression = true) String key;
  @Expression(ALLOW_SECRETS) @VariableExpression(policy = REGULAR_WITH_CUSTOM_FIELD) String value;
}
