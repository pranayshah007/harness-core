/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.plancreator.strategy.v1.ForConfigV1;
import io.harness.plancreator.strategy.v1.StrategyConfigV1;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.matrix.StrategyInfo;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@NoArgsConstructor
public class ForConfigServiceV1 implements StrategyConfigServiceV1 {
  @Override
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfigV1 strategyConfig, String childNodeId) {
    try {
      ForConfigV1 forConfig = (ForConfigV1) strategyConfig.getStrategyInfoConfig().getValue();
      List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
      if (!ParameterField.isBlank(forConfig.getIterations())) {
        for (int i = 0; i < forConfig.getIterations().getValue(); i++) {
          children.add(ChildrenExecutableResponse.Child.newBuilder()
                           .setChildNodeId(childNodeId)
                           .setStrategyMetadata(StrategyMetadata.newBuilder()
                                                    .setCurrentIteration(i)
                                                    .setTotalIterations(forConfig.getIterations().getValue())
                                                    .build())
                           .build());
        }
      }
      return children;
    } catch (ClassCastException classCastException) {
      throw new InvalidRequestException(
          "Could not parse the for strategy. Please ensure you are using correct looping strategy.");
    }
  }

  @Override
  public StrategyInfo expandJsonNode(
      StrategyConfigV1 strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    ForConfigV1 forConfig = (ForConfigV1) strategyConfig.getStrategyInfoConfig().getValue();
    List<JsonNode> jsonNodes = new ArrayList<>();
    if (!ParameterField.isBlank(forConfig.getIterations())) {
      if (maxExpansionLimit.isPresent()) {
        Integer iterationCount = forConfig.getIterations().getValue();
        if (iterationCount > maxExpansionLimit.get()) {
          throw new InvalidYamlException("Iteration count is beyond the supported limit of " + maxExpansionLimit.get());
        }
      }
      for (int i = 0; i < forConfig.getIterations().getValue(); i++) {
        JsonNode clonedNode = StrategyUtils.replaceExpressions(
            JsonPipelineUtils.asTree(jsonNode), new HashMap<>(), i, forConfig.getIterations().getValue(), null);
        StrategyUtils.modifyJsonNode(clonedNode, Lists.newArrayList(String.valueOf(i)));
        jsonNodes.add(clonedNode);
      }
    }
    int maxConcurrency = jsonNodes.size();
    if (!ParameterField.isBlank(forConfig.getMaxConcurrency())) {
      maxConcurrency = forConfig.getMaxConcurrency().getValue();
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(maxConcurrency).build();
  }
}
