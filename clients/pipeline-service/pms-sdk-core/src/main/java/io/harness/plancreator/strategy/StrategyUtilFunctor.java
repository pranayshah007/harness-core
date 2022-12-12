/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import io.harness.expression.LateBindingValue;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StrategyUtilFunctor implements LateBindingValue {
  private Map<String, String> combinations;
  private int currentIteration;
  private int totalIteration;
  private String itemValue;
  @Override
  public Object bind() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    Map<String, Object> matrixObjectMap = StrategyUtils.getMatrixMapFromCombinations(combinations);
    strategyObjectMap.put(StrategyConstants.MATRIX, matrixObjectMap);

    Map<String, Object> stepObjectMap = new HashMap<>();
    stepObjectMap.put(StrategyConstants.ITERATION, String.valueOf(currentIteration));
    stepObjectMap.put(StrategyConstants.ITERATIONS, String.valueOf(totalIteration));
    stepObjectMap.put(StrategyConstants.TOTAL_ITERATIONS, String.valueOf(totalIteration));
    strategyObjectMap.put(StrategyConstants.STEP, stepObjectMap);

    strategyObjectMap.put(StrategyConstants.ITERATION, String.valueOf(currentIteration));
    strategyObjectMap.put(StrategyConstants.ITERATIONS, String.valueOf(totalIteration));

    strategyObjectMap.put(StrategyConstants.REPEAT, Map.of(StrategyConstants.ITEM, itemValue == null ? "" : itemValue));
    return strategyObjectMap;
  }
}
