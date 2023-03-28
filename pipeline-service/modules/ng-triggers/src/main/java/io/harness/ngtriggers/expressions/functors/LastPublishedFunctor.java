/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions.functors;

import static io.harness.ngtriggers.Constants.TAG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class LastPublishedFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final String ACCEPT_ALL_REGEX = ".*?";

  public LastPublishedFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    Map<String, String> jsonObject = new HashMap<>();
    jsonObject.put(TAG, ACCEPT_ALL_REGEX);
    return jsonObject;
  }
}
