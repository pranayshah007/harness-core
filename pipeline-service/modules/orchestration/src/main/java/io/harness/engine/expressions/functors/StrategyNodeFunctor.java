/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.expression.LateBindingMap;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
@OwnedBy(PIPELINE)
public class StrategyNodeFunctor extends LateBindingMap {
  private Ambiance ambiance;
  private NodeExecutionsCache nodeExecutionsCache;

  @Override
  public synchronized Object get(Object key) {
    Map<String, Object> strategyDataMap = new HashMap<>();
    String status = getCurrentStatus(key.toString(), false);
    strategyDataMap.put(OrchestrationConstants.CURRENT_STATUS, status);
    strategyDataMap.putAll(getStrategyChildDataForGivenIdentifier((String) key));
    return strategyDataMap;
  }

  public String getCurrentStatus() {
    return getCurrentStatus("", true);
  }
  private String getCurrentStatus(String key, boolean useDeepestStrategyNodeIfIdentifierNotMatched) {
    // CurrentStatus is calculated for the strategy node. So we are calculating the Strategy level(Not the child of
    // strategy)
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

  // Strategy expressions(Except currentStatus) are evaluated on the child of strategy node. So we are getting the
  // level that is just below the strategy node.
  private Map<String, Object> getStrategyChildDataForGivenIdentifier(String identifier) {
    Level childLevel = null;
    for (int index = ambiance.getLevelsCount() - 2; index >= 0; index--) {
      Level level = ambiance.getLevels(index);
      if (level.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        // If identifier matches then its the level is the correct strategy level. So the child will be
        // getLevel(index+1).
        if (level.getIdentifier().equals(identifier)) {
          Level tempLevel = ambiance.getLevels(index + 1);
          childLevel = tempLevel.hasStrategyMetadata() ? tempLevel : null;
          break;
        }
      }
    }

    if (childLevel == null) {
      return Collections.emptyMap();
    }
    return StrategyUtils.fetchStrategyObjectMap(
        Collections.singletonList(childLevel), AmbianceUtils.shouldUseMatrixFieldName(ambiance));
  }
}
