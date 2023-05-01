/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expressions.InputsExpressionEvaluator;
import io.harness.pms.merger.helpers.MergeHelper;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetMergeHelperV1 {
  public String mergeInputSetIntoPipelineYaml(String inputSetYaml, String pipelineYaml) {
    if (EmptyPredicate.isEmpty(inputSetYaml)) {
      return pipelineYaml;
    }
    pipelineYaml = MergeHelper.mergeOptionsRuntimeInput(pipelineYaml, inputSetYaml);
    EngineExpressionEvaluator evaluator = new InputsExpressionEvaluator(inputSetYaml, pipelineYaml);
    return (String) evaluator.resolve(pipelineYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
  }
}
