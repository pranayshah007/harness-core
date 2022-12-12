/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.expressions.functors.InputsFunctor;

/**
  This ExpressionEvaluator is used for resolving the inputs in the pipeline yaml V1.
  It will be used before starting the execution and will replace the input references with values in the pipeline yaml.
 *
 */

@OwnedBy(HarnessTeam.PIPELINE)
public class InputsExpressionEvaluator extends EngineExpressionEvaluator {
  // inputs in json format.
  private final String inputs;
  private final String pipelineYamlV1;
  public InputsExpressionEvaluator(String inputs, String pipelineYamlV1) {
    super(null);
    this.inputs = inputs;
    this.pipelineYamlV1 = pipelineYamlV1;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("inputs", new InputsFunctor(inputs, pipelineYamlV1));
  }
}
