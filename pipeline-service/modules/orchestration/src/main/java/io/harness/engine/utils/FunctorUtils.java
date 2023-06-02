/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class FunctorUtils {
  public String getSecretExpression(long expressionFunctorToken, String secretIdentifier) {
    if (secretIdentifier.startsWith("${ngSecretManager.obtain(")) {
      throw new InvalidRequestException("Secret referencing inside a secret is not supported.");
    }
    return "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
  }

  public Object fetchFirst(List<Function<String, Optional<Object>>> fns, String key) {
    if (EmptyPredicate.isEmpty(fns)) {
      return null;
    }

    for (Function<String, Optional<Object>> fn : fns) {
      Optional<Object> optional = fn.apply(key);
      if (optional.isPresent()) {
        return optional.get();
      }
    }
    return null;
  }
}
