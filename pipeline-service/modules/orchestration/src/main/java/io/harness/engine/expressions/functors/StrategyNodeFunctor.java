/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.StatusUtils;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
public class StrategyNodeFunctor extends LateBindingMap {
  private Ambiance ambiance;
  private NodeExecutionsCache nodeExecutionsCache;

  @Override
  public synchronized Object get(Object key) {
    String status = getCurrentStatus(key.toString(), false);
    return Map.of(OrchestrationConstants.CURRENT_STATUS, status);
  }

  public String getCurrentStatus() {
    return getCurrentStatus("", true);
  }
  private String getCurrentStatus(String key, boolean useDeepestStrategyNodeIfIdentifierNotMatched) {
    Level level = getStrategyLevelForGivenIdentifier(key, useDeepestStrategyNodeIfIdentifierNotMatched);
    if (level == null) {
      return "null";
    }

    // This getCurrentStatus is similar to nodeExecutionMap.
    List<Status> childStatuses = nodeExecutionsCache.findAllTerminalChildrenStatusOnly(level.getRuntimeId(), true);
    String status = Status.RUNNING.name();
    if (EmptyPredicate.isNotEmpty(childStatuses)) {
      status = StatusUtils.calculateStatus(childStatuses, ambiance.getPlanExecutionId()).name();
    }
    return status;
  }
  private Level getStrategyLevelForGivenIdentifier(
      String identifier, boolean useDeepestStrategyNodeIfIdentifierNotMatched) {
    Level strategyLevel = null;
    for (int index = ambiance.getLevelsCount() - 1; index >= 0; index--) {
      Level level = ambiance.getLevels(index);
      if (level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        // If identifier matches then return that strategy level.
        if (level.getIdentifier().equals(identifier)) {
          return level;
        }
        // If identifier does not match then return the strategy level with max depth if flag
        // useAnyStrategyNodeIfIdentifierNotMatched is true.
        if (useDeepestStrategyNodeIfIdentifierNotMatched && strategyLevel == null) {
          strategyLevel = level;
        }
      }
    }
    return strategyLevel;
  }
}
