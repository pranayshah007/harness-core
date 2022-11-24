/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class CIK8ServicePodParams implements NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) @NonNull private String serviceName;
  @Expression(ALLOW_SECRETS) @NonNull private Map<String, String> selectorMap;
  @NonNull private List<Integer> ports;
  @Expression(ALLOW_SECRETS) @NonNull private CIK8PodParams<CIK8ContainerParams> cik8PodParams;
}
