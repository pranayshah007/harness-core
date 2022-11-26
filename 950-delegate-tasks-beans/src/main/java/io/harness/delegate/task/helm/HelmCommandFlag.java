/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.helm.HelmSubCommandType;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HelmCommandFlag implements NestedAnnotationResolver {
  @NotNull @Expression(ALLOW_SECRETS) private Map<HelmSubCommandType, String> valueMap;
}
