/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputExpressionFunctor implements LateBindingValue {
  private final Ambiance ambiance;
  private final ExecutionInputService executionInputService;

  public ExecutionInputExpressionFunctor(ExecutionInputService executionInputService, Ambiance ambiance) {
    this.executionInputService = executionInputService;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    Map<String, Object> expressionValuesMap = new HashMap<>();
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    // Extracting nodeExecutionIds of all parents.
    Set<String> nodeExecutionIds =
        ambiance.getLevelsList().stream().map(Level::getRuntimeId).collect(Collectors.toSet());

    // Fetch all ExecutionInputInstance of the current node and all parents.
    List<ExecutionInputInstance> inputInstances = executionInputService.getExecutionInputInstances(nodeExecutionIds);
    for (ExecutionInputInstance instance : inputInstances) {
      expressionValuesMap.putAll(instance.getMergedInputTemplate());
    }
    return expressionValuesMap;
  }
}
