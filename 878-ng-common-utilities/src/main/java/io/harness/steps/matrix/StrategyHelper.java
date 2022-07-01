/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class StrategyHelper {
  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Inject ParallelismStrategyConfigService parallelismStrategyConfigService;

  public List<JsonNode> expandJsonNodes(JsonNode nodeWithStrategy) throws IOException {
    JsonNode node = nodeWithStrategy.get("strategy");
    if (node == null || node.isNull()) {
      return Collections.singletonList(nodeWithStrategy);
    }
    StrategyConfig strategyConfig = JsonPipelineUtils.read(node.toString(), StrategyConfig.class);
    if (strategyConfig.getMatrixConfig() != null) {
      return matrixConfigService.expandJsonNode(strategyConfig, nodeWithStrategy);
    }
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      return parallelismStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy);
    }
    return forLoopStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy);
  }

  public List<ExecutionWrapperConfig> expandExecutionWrapperConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      if (executionWrapperConfig.getStep() != null) {
        List<JsonNode> expandedJsonNodes = expandJsonNodes(executionWrapperConfig.getStep());
        return expandedJsonNodes.stream()
            .map(node -> ExecutionWrapperConfig.builder().step(node).uuid(executionWrapperConfig.getUuid()).build())
            .collect(Collectors.toList());
      }
      if (executionWrapperConfig.getParallel() != null) {
        ParallelStepElementConfig parallelStepElementConfig =
            YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : parallelStepElementConfig.getSections()) {
          executionWrapperConfigs.addAll(expandExecutionWrapperConfig(config));
        }
        parallelStepElementConfig.setSections(executionWrapperConfigs);
        ArrayNode arrayNode = JsonPipelineUtils.getMapper().createArrayNode();
        for (ExecutionWrapperConfig config : executionWrapperConfigs) {
          arrayNode.add(JsonPipelineUtils.asTree(config));
        }
        return Lists.newArrayList(ExecutionWrapperConfig.builder().parallel(arrayNode).build());
      }
      if (executionWrapperConfig.getStepGroup() != null) {
        StepGroupElementConfig stepGroupElementConfig =
            YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : stepGroupElementConfig.getSteps()) {
          executionWrapperConfigs.addAll(expandExecutionWrapperConfig(config));
        }
        stepGroupElementConfig.setSteps(executionWrapperConfigs);
        JsonNode stepGroupNode = JsonPipelineUtils.asTree(stepGroupElementConfig);
        List<JsonNode> expandedJsonNodes = expandJsonNodes(stepGroupNode);
        return expandedJsonNodes.stream()
            .map(
                node -> ExecutionWrapperConfig.builder().stepGroup(node).uuid(executionWrapperConfig.getUuid()).build())
            .collect(Collectors.toList());
      }
      return Lists.newArrayList(executionWrapperConfig);
    } catch (Exception ex) {
      throw new InvalidRequestException("Unable to expand yaml for a execution element with strategy");
    }
  }
}
