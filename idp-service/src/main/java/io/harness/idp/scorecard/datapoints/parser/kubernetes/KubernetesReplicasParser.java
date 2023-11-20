/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesReplicasParser extends KubernetesExpressionParser implements DataPointParser {
  @Override
  Object parseValue(Object value) {
    if (value == null) {
      return null;
    }
    return ((Double) value).intValue();
  }

  @Override
  boolean compare(Object value, Object compareValue) {
    return compareValue == null || (int) compareValue >= (int) value;
  }
}
