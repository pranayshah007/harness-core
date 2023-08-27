/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.NGAccess;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmSecretManagerFunctor implements ExpressionFunctor {
  private long expressionFunctorToken;
  private NGAccess ngAccess;

  @Builder.Default private Set<String> secrets = new HashSet<>();

  public Set<String> getSecrets() {
    return secrets;
  }

  public Object obtain(String secretIdentifier, int token) {
    String expr = "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
    secrets.add(expr);
    return expr;
  }
}
