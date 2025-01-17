/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.LateBindingValue;
import io.harness.pms.merger.helpers.FQNMapGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 This Functor is invoked when an expression starts with yaml.
 *
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class YamlEvaluatorFunctor implements LateBindingValue {
  private final String yaml;

  public YamlEvaluatorFunctor(String yaml) {
    this.yaml = yaml;
  }

  @Override
  public Object bind() {
    return FQNMapGenerator.generateYamlMapWithFqnExpression(yaml);
  }
}
