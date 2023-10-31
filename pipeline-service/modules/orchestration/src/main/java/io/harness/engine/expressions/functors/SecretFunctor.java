/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.SecretObserverInfo;
import io.harness.engine.observers.SecretResolutionObserver;
import io.harness.engine.utils.FunctorUtils;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import lombok.Value;

@OwnedBy(CDC)
@Value
public class SecretFunctor implements ExpressionFunctor {
  Ambiance ambiance;
  Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject;

  public SecretFunctor(Ambiance ambiance, Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject) {
    this.ambiance = ambiance;
    this.secretsRuntimeUsagesSubject = secretsRuntimeUsagesSubject;
  }

  public Object getValue(String secretIdentifier) {
    if (EmptyPredicate.isNotEmpty(secretIdentifier) && ambiance != null
        && AmbianceUtils.shouldEnableSecretsObserver(ambiance)) {
      secretsRuntimeUsagesSubject.fireInform(SecretResolutionObserver::onSecretsRuntimeUsage,
          SecretObserverInfo.builder().secretIdentifier(secretIdentifier).ambiance(ambiance).build());
    }

    return FunctorUtils.getSecretExpression(ambiance.getExpressionFunctorToken(), secretIdentifier);
  }
}
