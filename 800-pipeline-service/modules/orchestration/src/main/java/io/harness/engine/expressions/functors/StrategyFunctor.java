/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.plancreator.strategy.StrategyConstants.ITEM;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.ITERATIONS;
import static io.harness.plancreator.strategy.StrategyConstants.MATRIX;
import static io.harness.plancreator.strategy.StrategyConstants.REPEAT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.LateBindingValue;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class StrategyFunctor implements LateBindingValue {
  Ambiance ambiance;

  public StrategyFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    List<Level> levelsWithStrategyMetadata =
        ambiance.getLevelsList().stream().filter(Level::hasStrategyMetadata).collect(Collectors.toList());
    Map<String, String> matrixValuesMap = new HashMap<>();
    Map<String, String> repeatValuesMap = new HashMap<>();

    for (Level level : levelsWithStrategyMetadata) {
      if (level.getStrategyMetadata().hasMatrixMetadata()) {
        matrixValuesMap.putAll(level.getStrategyMetadata().getMatrixMetadata().getMatrixValuesMap());
      }
      if (level.getStrategyMetadata().hasForMetadata()) {
        repeatValuesMap.put(ITEM, level.getStrategyMetadata().getForMetadata().getValue());
      }
      strategyObjectMap.put(ITERATION, level.getStrategyMetadata().getCurrentIteration());
      strategyObjectMap.put(ITERATIONS, level.getStrategyMetadata().getTotalIterations());
      strategyObjectMap.put("identifierPostFix", AmbianceUtils.getStrategyPostfix(level));
    }
    strategyObjectMap.put(MATRIX, matrixValuesMap);
    strategyObjectMap.put(REPEAT, repeatValuesMap);

    return strategyObjectMap;
  }
}
