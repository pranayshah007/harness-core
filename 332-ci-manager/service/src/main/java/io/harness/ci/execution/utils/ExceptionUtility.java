/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.ngexception.UnresolvedRunTimeInputSetException;

@OwnedBy(HarnessTeam.CI)
public class ExceptionUtility {
  public static void throwUnresolvedExpressionException(String expression, String field, String message) {
    if (NGExpressionUtils.matchesInputSetPattern(expression)) {
      throw new UnresolvedRunTimeInputSetException(String.format(
          "Failed to resolve runtime inputset: %s for mandatory field %s in " + message, expression, field));
    } else {
      throw new UnresolvedRunTimeInputSetException(
          String.format("Failed to resolve expression: %s for mandatory field %s in" + message, expression, field));
    }
  }
}
