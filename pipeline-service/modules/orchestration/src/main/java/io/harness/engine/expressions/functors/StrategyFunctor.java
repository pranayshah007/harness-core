/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.utils.FunctorUtils;
import io.harness.expression.LateBindingMap;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class StrategyFunctor extends LateBindingMap {
  Ambiance ambiance;
  NodeExecutionsCache nodeExecutionsCache;
  public static final String NODE_KEY = "node";

  public StrategyFunctor(Ambiance ambiance, NodeExecutionsCache nodeExecutionsCache) {
    this.ambiance = ambiance;
    this.nodeExecutionsCache = nodeExecutionsCache;
  }

  @Override
  public synchronized Object get(Object key) {
    return FunctorUtils.fetchFirst(
        Arrays.asList(this::getCurrentStatus, this::getStrategyNodeForCurrentStatus, this::getStrategyParams),
        (String) key);
  }

  public Optional<Object> getStrategyParams(String key) {
    List<Level> levelsWithStrategyMetadata =
        ambiance.getLevelsList().stream().filter(Level::hasStrategyMetadata).collect(Collectors.toList());
    Map<String, Object> map = StrategyUtils.fetchStrategyObjectMap(
        levelsWithStrategyMetadata, ambiance.getMetadata().getUseMatrixFieldName());
    return Optional.of(map.get(key));
  }

  private Optional<Object> getCurrentStatus(String key) {
    if (!OrchestrationConstants.CURRENT_STATUS.equals(key)) {
      return Optional.empty();
    }
    return Optional.of(StrategyNodeFunctor.builder()
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(ambiance)
                           .build()
                           .getCurrentStatus());
  }
  private Optional<Object> getStrategyNodeForCurrentStatus(String key) {
    if (!NODE_KEY.equals(key)) {
      return Optional.empty();
    }
    return Optional.of(
        StrategyNodeFunctor.builder().nodeExecutionsCache(nodeExecutionsCache).ambiance(ambiance).build());
  }
}
