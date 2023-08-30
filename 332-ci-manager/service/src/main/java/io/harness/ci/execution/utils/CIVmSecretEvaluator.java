/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.ng.core.NGAccess;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmSecretEvaluator extends ExpressionEvaluator {
  public Set<String> resolve(Object o, NGAccess ngAccess, long token) {
    CIVmSecretManagerFunctor ciVmSecretManagerFunctor =
        CIVmSecretManagerFunctor.builder().expressionFunctorToken(token).ngAccess(ngAccess).build();

    ResolveFunctorImpl resolveFunctor = new ResolveFunctorImpl(new ExpressionEvaluator(), ciVmSecretManagerFunctor);

    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);

    return ciVmSecretManagerFunctor.getSecrets();
  }

  public CIVmSecretEvaluator() {}

  public class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final ExpressionEvaluator expressionEvaluator;
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    public ResolveFunctorImpl(
        ExpressionEvaluator expressionEvaluator, CIVmSecretManagerFunctor ciVmSecretManagerFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("ngSecretManager", ciVmSecretManagerFunctor);
    }

    @Override
    public String processString(String expression) {
      return expressionEvaluator.substitute(expression, evaluatorResponseContext);
    }
  }
}
