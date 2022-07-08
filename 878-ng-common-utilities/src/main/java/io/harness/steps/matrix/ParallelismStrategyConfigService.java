/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.plancreator.strategy.StageStrategyUtils;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ParallelismStrategyConfigService implements StrategyConfigService {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    Integer parallelism = 0;
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      parallelism = strategyConfig.getParallelism().getValue();
    }
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    for (int i = 0; i < parallelism; i++) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(
                           StrategyMetadata.newBuilder().setCurrentIteration(i).setTotalIterations(parallelism).build())
                       .build());
    }
    return children;
  }

  @Override
  public List<JsonNode> expandJsonNode(StrategyConfig strategyConfig, JsonNode jsonNode) {
    Integer parallelism = 0;
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      parallelism = strategyConfig.getParallelism().getValue();
    }
    List<JsonNode> jsonNodes = new ArrayList<>();
    for (int i = 0; i < parallelism; i++) {
      JsonNode clonedJsonNode = JsonPipelineUtils.asTree(JsonUtils.asMap(
          StageStrategyUtils.replaceExpressions(jsonNode.deepCopy().toString(), new HashMap<>(), i, parallelism)));
      StageStrategyUtils.modifyJsonNode(clonedJsonNode, Lists.newArrayList(String.valueOf(i)));
      jsonNodes.add(clonedJsonNode);
    }
    return jsonNodes;
  }
}
