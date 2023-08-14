/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.service;

import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

public class IdpExpressionEvaluator extends EngineExpressionEvaluator {
  private final Map<String, Map<String, Object>> data;

  public IdpExpressionEvaluator(Map<String, Map<String, Object>> data) {
    super(null);
    this.data = data;
  }

  @Override
  protected void initialize() {
    super.initialize();
    data.forEach(this::addToContext);
  }
}
