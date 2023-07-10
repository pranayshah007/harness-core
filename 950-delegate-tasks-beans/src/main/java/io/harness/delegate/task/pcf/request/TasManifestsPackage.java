/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotation.RecasterAlias;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.delegate.task.pcf.request.TasManifestsPackage")
public class TasManifestsPackage implements NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) private String manifestYml;
  @Expression(ALLOW_SECRETS) private String autoscalarManifestYml;
  @Expression(ALLOW_SECRETS) private List<String> variableYmls;
}
