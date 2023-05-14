/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Singleton
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.PIPELINE)
public class MatrixConfigService implements StrategyConfigService {
  @Inject MatrixConfigServiceHelper matrixConfigServiceHelper;

  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    MatrixConfig matrixConfig = (MatrixConfig) strategyConfig.getMatrixConfig().getValue();
    List<String> keys = getKeys(matrixConfig);

    return matrixConfigServiceHelper.fetchChildren(
        keys, matrixConfig.getAxes(), matrixConfig.getExpressionAxes(), matrixConfig.getExclude(), childNodeId);
  }

  public StrategyInfo expandJsonNodeFromClass(StrategyConfig strategyConfig, JsonNode jsonNode,
      Optional<Integer> maxExpansionLimit, boolean isStepGroup, Class cls) {
    MatrixConfig matrixConfig = (MatrixConfig) strategyConfig.getMatrixConfig().getValue();
    List<String> keys = getKeys(matrixConfig);
    return matrixConfigServiceHelper.expandJsonNodeFromClass(keys, matrixConfig.getAxes(),
        matrixConfig.getExpressionAxes(), matrixConfig.getExclude(), matrixConfig.getMaxConcurrency(), jsonNode,
        maxExpansionLimit, isStepGroup, cls);
  }

  public StrategyInfo expandJsonNode(
      StrategyConfig strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    MatrixConfig matrixConfig = (MatrixConfig) strategyConfig.getMatrixConfig().getValue();
    List<String> keys = getKeys(matrixConfig);
    return matrixConfigServiceHelper.expandJsonNode(keys, matrixConfig.getAxes(), matrixConfig.getExpressionAxes(),
        matrixConfig.getExclude(), matrixConfig.getMaxConcurrency(), jsonNode, maxExpansionLimit);
  }

  private List<String> getKeys(MatrixConfig matrixConfig) {
    List<String> keys = new LinkedList<>(matrixConfig.getAxes().keySet());
    keys.addAll(matrixConfig.getExpressionAxes().keySet());
    return keys;
  }
}
