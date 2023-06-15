/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stages;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class StagesExpressionEvaluator extends EngineExpressionEvaluator {
  private final Map<String, String> expressionValues;

  public StagesExpressionEvaluator(Map<String, String> expressionValues) {
    super(null);
    this.expressionValues = expressionValues;
  }

  @Override
  protected void initialize() {
    super.initialize();
    for (Map.Entry<String, String> entry : expressionValues.entrySet()) {
      String key = entry.getKey();
      // trimming the key 'cause it can be "<+abc> "
      key = key.trim();
      // key starts with <+ and ends with >
      key = key.substring(2, key.length() - 1);
      // trimming the key again 'cause it can be "<+abc >"
      key = key.trim();
      this.addToContext(key, entry.getValue());
    }
  }
}
