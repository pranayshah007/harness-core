/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.security.SimpleEncryption;

import software.wings.expression.NgSecretManagerFunctorInterface;
import software.wings.expression.SecretManagerMode;
import software.wings.expression.SecretManagerPreviewFunctor;
import software.wings.expression.SweepingOutputSecretFunctor;

import lombok.Value;

@OwnedBy(CDC)
@Value
public class MaskingExpressionEvaluator extends ExpressionEvaluator {
  private ExpressionFunctor secretManagerFunctor;
  private ExpressionFunctor sweepingOutputSecretFunctor;

  public MaskingExpressionEvaluator() {
    secretManagerFunctor = new SecretManagerPreviewFunctor();
    sweepingOutputSecretFunctor = SweepingOutputSecretFunctor.builder()
                                      .mode(SecretManagerMode.CHECK_FOR_SECRETS)
                                      .simpleEncryption(new SimpleEncryption())
                                      .build();
    addFunctor("sweepingOutputSecrets", sweepingOutputSecretFunctor);
    addFunctor(NgSecretManagerFunctorInterface.FUNCTOR_NAME, secretManagerFunctor);
  }
}
