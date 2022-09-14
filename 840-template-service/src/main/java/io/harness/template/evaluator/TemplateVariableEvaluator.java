/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.evaluator;

import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.EngineExpressionEvaluator;

import java.util.Map;

public class TemplateVariableEvaluator extends EngineExpressionEvaluator {
  private final Map<String, String> templateVariableValues;
  public TemplateVariableEvaluator(Map<String, String> templateVariableValues) {
    super(null);
    this.templateVariableValues = templateVariableValues;
  }

  @Override
  protected void initialize() {
    super.initialize();
    if (EmptyPredicate.isNotEmpty(templateVariableValues)) {
      templateVariableValues.forEach(this::addToContext);
    }
  }
}
