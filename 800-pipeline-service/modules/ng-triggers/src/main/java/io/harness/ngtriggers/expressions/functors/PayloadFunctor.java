/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingValue;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.io.IOException;
import java.util.HashMap;

public class PayloadFunctor implements LateBindingValue {
  private String payload;

  public PayloadFunctor(String payload) {
    this.payload = payload;
  }

  @Override
  public Object bind() {
    try {
      return JsonPipelineUtils.read(payload, HashMap.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Event payload could not be converted to a hashmap");
    }
  }
}
