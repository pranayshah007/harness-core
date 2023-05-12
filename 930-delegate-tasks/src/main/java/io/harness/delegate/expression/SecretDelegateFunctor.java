/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.expression;

import io.harness.data.encoding.EncodingUtils;
import io.harness.exception.FunctorException;
import io.harness.expression.functors.ExpressionFunctor;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class SecretDelegateFunctor implements ExpressionFunctor {
  private Map<String, char[]> secrets;
  private int expressionFunctorToken;

  public Object obtain(String secretDetailsUuid, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }
    if (secrets.containsKey(secretDetailsUuid)) {
      return new String(secrets.get(secretDetailsUuid));
    }
    log.error("Unable to find secret details for {} ", secretDetailsUuid);
    throw new FunctorException("Secret details not found");
  }

  public Object obtainBase64(String secretDetailsUuid, int token) {
    return EncodingUtils.encodeBase64((String) obtain(secretDetailsUuid, token));
  }
}
