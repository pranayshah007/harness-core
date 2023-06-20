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

import com.fasterxml.jackson.databind.JsonNode;

/**
  This ExpressionEvaluator is used for resolving the inputs in the pipeline yaml V1.
  It will be used before starting the execution and will replace the input references with values in the pipeline yaml.
 *
 */

@OwnedBy(HarnessTeam.PIPELINE)
public class InputsExpressionEvaluator extends EngineExpressionEvaluator {
  // inputs in json format.
  private final JsonNode inputSetJsonNode;
  private final JsonNode pipelineJsonNodeV1;
  public InputsExpressionEvaluator(JsonNode inputSetJsonNode, JsonNode pipelineJsonNodeV1) {
    super(null);
    this.inputSetJsonNode = inputSetJsonNode;
    this.pipelineJsonNodeV1 = pipelineJsonNodeV1;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("inputs", new InputsFunctor(inputSetJsonNode, pipelineJsonNodeV1));
  }
}
