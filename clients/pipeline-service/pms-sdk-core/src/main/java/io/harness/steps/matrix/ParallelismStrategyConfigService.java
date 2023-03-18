/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.plancreator.strategy.StrategyUtils;
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
import java.util.Optional;

public class ParallelismStrategyConfigService implements StrategyConfigService {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    Integer parallelism = 0;
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      parallelism = Double.valueOf(String.valueOf(strategyConfig.getParallelism().getValue())).intValue();
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
  public StrategyInfo expandJsonNode(
      StrategyConfig strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    Integer parallelism = 0;
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      parallelism = Double.valueOf(String.valueOf(strategyConfig.getParallelism().getValue())).intValue();
      if (maxExpansionLimit.isPresent()) {
        if (parallelism > maxExpansionLimit.get()) {
          throw new InvalidYamlException(
              "Parallelism count is beyond the supported limit of " + maxExpansionLimit.get());
        }
      }
    }
    List<JsonNode> jsonNodes = new ArrayList<>();
    for (int i = 0; i < parallelism; i++) {
      JsonNode clonedJsonNode = JsonPipelineUtils.asTree(JsonUtils.asMap(
          StrategyUtils.replaceExpressions(jsonNode.deepCopy().toString(), new HashMap<>(), i, parallelism, null)));
      StrategyUtils.modifyJsonNode(clonedJsonNode, Lists.newArrayList(String.valueOf(i)));
      jsonNodes.add(clonedJsonNode);
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(jsonNodes.size()).build();
  }
}
