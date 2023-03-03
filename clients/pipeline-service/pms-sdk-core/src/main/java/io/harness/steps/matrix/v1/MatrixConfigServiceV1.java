/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.strategy.v1.MatrixConfigV1;
import io.harness.plancreator.strategy.v1.StrategyConfigV1;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.steps.matrix.MatrixConfigServiceHelper;
import io.harness.steps.matrix.StrategyInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@NoArgsConstructor
@AllArgsConstructor
public class MatrixConfigServiceV1 implements StrategyConfigServiceV1 {
  @Inject MatrixConfigServiceHelper matrixConfigServiceHelper;
  // Fetching all the combinations for the matrix.
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfigV1 strategyConfig, String childNodeId) {
    MatrixConfigV1 matrixConfig = (MatrixConfigV1) strategyConfig.getStrategyInfoConfig().getValue();
    List<String> keys = getKeys(matrixConfig);
    return matrixConfigServiceHelper.fetchChildren(keys, matrixConfig.getAxis().getAxes(),
        matrixConfig.getAxis().getExpressionAxes(), matrixConfig.getExclude(), childNodeId);
  }

  // Expanding the YAML for matrix so that the expanded YAML can directly be executed. This is used by CI.
  public StrategyInfo expandJsonNode(
      StrategyConfigV1 strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    MatrixConfigV1 matrixConfig = (MatrixConfigV1) strategyConfig.getStrategyInfoConfig().getValue();
    List<String> keys = getKeys(matrixConfig);
    return matrixConfigServiceHelper.expandJsonNode(keys, matrixConfig.getAxis().getAxes(),
        matrixConfig.getAxis().getExpressionAxes(), matrixConfig.getExclude(), matrixConfig.getMaxConcurrency(),
        jsonNode, maxExpansionLimit);
  }

  private List<String> getKeys(MatrixConfigV1 matrixConfig) {
    List<String> keys = new LinkedList<>(matrixConfig.getAxis().getAxes().keySet());
    keys.addAll(matrixConfig.getAxis().getExpressionAxes().keySet());
    return keys;
  }
}
