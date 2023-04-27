/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.HashMap;

@OwnedBy(PIPELINE)
public class InputSetFunctor implements LateBindingValue {
  private final PmsExecutionSummaryService pmsExecutionService;

  private final Ambiance ambiance;

  public InputSetFunctor(PmsExecutionSummaryService pmsExecutionService, Ambiance ambiance) {
    this.pmsExecutionService = pmsExecutionService;
    this.ambiance = ambiance;
  }
  @Override
  public Object bind() {
    try {
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
          pmsExecutionService.getPipelineExecutionSummaryWithProjections(ambiance.getPlanExecutionId(),
              Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.inputSetYaml));

      return YamlUtils.read(pipelineExecutionSummaryEntity.getInputSetYaml(), HashMap.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Input Set Yaml could not be converted to a hashmap");
    }
  }
}
