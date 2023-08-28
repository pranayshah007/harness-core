/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import java.util.HashMap;
import java.util.Map;
import javax.cache.Cache;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class HostedVmSecretEvaluator {
  private Cache<String, EncryptedDataDetails> secretsCache;
  private SecretManagerClientService ngSecretService;

  public void resolve(Object o, NGAccess ngAccess, long token) {
    HostedVmSecretManagerFunctor hostedVmSecretManagerFunctor = HostedVmSecretManagerFunctor.builder()
                                                                    .ngAccess(ngAccess)
                                                                    .expressionFunctorToken(token)
                                                                    .secretsCache(secretsCache)
                                                                    .ngSecretService(ngSecretService)
                                                                    .build();

    HostedVmSecretEvaluator.ResolveFunctorImpl resolveFunctor =
        new HostedVmSecretEvaluator.ResolveFunctorImpl(new ExpressionEvaluator(), hostedVmSecretManagerFunctor);

    ExpressionEvaluatorUtils.updateExpressions(o, resolveFunctor);
  }

  public HostedVmSecretEvaluator(
      Cache<String, EncryptedDataDetails> secretsCache, SecretManagerClientService ngSecretService) {
    this.secretsCache = secretsCache;
    this.ngSecretService = ngSecretService;
  }

  public class ResolveFunctorImpl implements ExpressionResolveFunctor {
    private final ExpressionEvaluator expressionEvaluator;
    final Map<String, Object> evaluatorResponseContext = new HashMap<>(1);

    public ResolveFunctorImpl(
        ExpressionEvaluator expressionEvaluator, HostedVmSecretManagerFunctor hostedVmSecretManagerFunctor) {
      this.expressionEvaluator = expressionEvaluator;
      evaluatorResponseContext.put("ngSecretManager", hostedVmSecretManagerFunctor);
    }

    @Override
    public String processString(String expression) {
      return expressionEvaluator.substitute(expression, evaluatorResponseContext);
    }
  }
}
