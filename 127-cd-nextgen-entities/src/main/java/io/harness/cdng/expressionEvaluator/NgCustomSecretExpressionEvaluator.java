/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressionEvaluator;

import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import software.wings.expression.NgSecretManagerFunctor;
import software.wings.expression.SecretManagerMode;

import lombok.Value;
@Value
public class NgCustomSecretExpressionEvaluator extends ExpressionEvaluator {
  ExpressionFunctor expressionFunctor;

  public NgCustomSecretExpressionEvaluator(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      int secretFunctor, DelegateMetricsService delegateMetricsService, SecretManagerClientService ngSecretService) {
    this.expressionFunctor = NgSecretManagerFunctor.builder()
                                 .mode(SecretManagerMode.APPLY)
                                 .accountId(accountIdentifier)
                                 .expressionFunctorToken(secretFunctor)
                                 .delegateMetricsService(delegateMetricsService)
                                 .ngSecretService(ngSecretService)
                                 .evaluateSync(true)
                                 .orgId(orgIdentifier)
                                 .projectId(projectIdentifier)
                                 .build();
    addFunctor("ngSecretManager", expressionFunctor);
  }
}
