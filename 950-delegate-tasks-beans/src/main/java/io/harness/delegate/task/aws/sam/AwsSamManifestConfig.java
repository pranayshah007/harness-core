/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.sam;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
public class AwsSamManifestConfig implements ExpressionReflectionUtils.NestedAnnotationResolver {
  @NonFinal @Expression(ALLOW_SECRETS) String manifestPath;
  @NonFinal @Expression(ALLOW_SECRETS) String samTemplateFilePath;
  @NonFinal @Expression(ALLOW_SECRETS) String samConfigFilePath;
  GitStoreDelegateConfig gitStoreDelegateConfig;
}
