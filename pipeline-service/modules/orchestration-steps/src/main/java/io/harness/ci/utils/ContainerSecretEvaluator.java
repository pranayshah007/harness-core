/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.ng.core.NGAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerSecretEvaluator extends ExpressionEvaluator {
  private SecretUtils secretUtils;
  public List<SecretVariableDetails> resolve(Object o, NGAccess ngAccess, long token) {
    ContainerStepSecretFunctor ciNgSecretManagerFunctor = ContainerStepSecretFunctor.builder()
                                                              .expressionFunctorToken(token)
                                                              .secretUtils(secretUtils)
                                                              .ngAccess(ngAccess)
                                                              .build();

    ResolveFunctorImpl resolveFunctor = new ResolveFunctorImpl(new ExpressionEvaluator(), ciNgSecretManagerFunctor);

    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);

    return ciNgSecretManagerFunctor.getSecretVariableDetails();
  }

  public ContainerSecretEvaluator(SecretUtils secretUtils) {
    this.secretUtils = secretUtils;
  }

  public class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final ExpressionEvaluator expressionEvaluator;
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    public ResolveFunctorImpl(
        ExpressionEvaluator expressionEvaluator, ContainerStepSecretFunctor ciNgSecretManagerFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("ngSecretManager", ciNgSecretManagerFunctor);
    }

    @Override
    public String processString(String expression) {
      return expressionEvaluator.substitute(expression, evaluatorResponseContext);
    }
  }
}
