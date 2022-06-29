/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.plancreator.strategy.HarnessForConfig;
import io.harness.plancreator.strategy.StageStrategyUtils;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ForLoopStrategyConfigService implements StrategyConfigService {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    HarnessForConfig harnessForConfig = strategyConfig.getForConfig();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    for (int i = 0; i < harnessForConfig.getIteration().getValue(); i++) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(StrategyMetadata.newBuilder()
                                                .setCurrentIteration(i)
                                                .setTotalIterations(harnessForConfig.getIteration().getValue())
                                                .build())
                       .build());
    }
    return children;
  }

  @Override
  public List<JsonNode> expandJsonNode(StrategyConfig strategyConfig, JsonNode jsonNode) {
    HarnessForConfig harnessForConfig = strategyConfig.getForConfig();
    List<JsonNode> jsonNodes = new ArrayList<>();
    for (int i = 0; i < harnessForConfig.getIteration().getValue(); i++) {
      JsonNode clonedNode = JsonPipelineUtils.asTree(JsonUtils.asMap(StageStrategyUtils.replaceExpressions(
          jsonNode.deepCopy().toString(), new HashMap<>(), i, harnessForConfig.getIteration().getValue())));
      StageStrategyUtils.modifyJsonNode(clonedNode, Lists.newArrayList(String.valueOf(i)));
      jsonNodes.add(clonedNode);
    }
    return jsonNodes;
  }
}
