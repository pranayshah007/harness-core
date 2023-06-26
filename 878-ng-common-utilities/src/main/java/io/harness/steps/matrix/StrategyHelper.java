/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.strategy.StrategyValidationUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class StrategyHelper {
  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Inject ParallelismStrategyConfigService parallelismStrategyConfigService;

  public StrategyInfo expandJsonNodesFromClass(JsonNode nodeWithStrategy, Optional<Integer> maxExpansionLimit,
      boolean isStepGroup, Class cls) throws IOException {
    JsonNode node = nodeWithStrategy.get("strategy");
    if (node == null || node.isNull()) {
      JsonNode clonedNode =
          StrategyUtils.replaceExpressions(JsonPipelineUtils.asTree(nodeWithStrategy), new HashMap<>(), 0, 1, null);
      return StrategyInfo.builder().expandedJsonNodes(Collections.singletonList(clonedNode)).maxConcurrency(1).build();
    }
    StrategyConfig strategyConfig = JsonPipelineUtils.read(node.toString(), StrategyConfig.class);
    StrategyValidationUtils.validateStrategyNode(strategyConfig);
    if (ParameterField.isNotNull(strategyConfig.getMatrixConfig())) {
      if (strategyConfig.getMatrixConfig().isExpression()) {
        throw new InvalidRequestException("Expression for matrix at runtime could not be resolved!");
      } else {
        return matrixConfigService.expandJsonNodeFromClass(
            strategyConfig, nodeWithStrategy, maxExpansionLimit, isStepGroup, cls);
      }
    }
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      return parallelismStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy, maxExpansionLimit);
    }
    return forLoopStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy, maxExpansionLimit);
  }

  public StrategyInfo expandJsonNodes(JsonNode nodeWithStrategy, Optional<Integer> maxExpansionLimit)
      throws IOException {
    JsonNode node = nodeWithStrategy.get("strategy");
    if (node == null || node.isNull()) {
      JsonNode clonedNode =
          StrategyUtils.replaceExpressions(JsonPipelineUtils.asTree(nodeWithStrategy), new HashMap<>(), 0, 1, null);
      return StrategyInfo.builder().expandedJsonNodes(Collections.singletonList(clonedNode)).maxConcurrency(1).build();
    }
    StrategyConfig strategyConfig = JsonPipelineUtils.read(node.toString(), StrategyConfig.class);
    StrategyValidationUtils.validateStrategyNode(strategyConfig);
    if (ParameterField.isNotNull(strategyConfig.getMatrixConfig())) {
      if (strategyConfig.getMatrixConfig().isExpression()) {
        throw new InvalidRequestException("Expression for matrix at runtime could not be resolved!");
      } else {
        return matrixConfigService.expandJsonNode(strategyConfig, nodeWithStrategy, maxExpansionLimit);
      }
    }
    if (!ParameterField.isBlank(strategyConfig.getParallelism())) {
      return parallelismStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy, maxExpansionLimit);
    }
    return forLoopStrategyConfigService.expandJsonNode(strategyConfig, nodeWithStrategy, maxExpansionLimit);
  }

  public ExpandedExecutionWrapperInfo expandExecutionWrapperConfig(
      ExecutionWrapperConfig executionWrapperConfig, Optional<Integer> maxExpansionLimit) {
    try {
      if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StrategyInfo strategyInfo = expandJsonNodes(executionWrapperConfig.getStep(), maxExpansionLimit);
        List<JsonNode> expandedJsonNodes = strategyInfo.getExpandedJsonNodes();
        Map<String, StrategyExpansionData> uuidToStrategyExpansionData = new HashMap<>();
        String uuid = EmptyPredicate.isEmpty(executionWrapperConfig.getUuid()) ? UUIDGenerator.generateUuid()
                                                                               : executionWrapperConfig.getUuid();
        uuidToStrategyExpansionData.put(
            uuid, StrategyExpansionData.builder().maxConcurrency(strategyInfo.getMaxConcurrency()).build());
        List<ExecutionWrapperConfig> executionWrapperConfigs =
            expandedJsonNodes.stream()
                .map(node -> ExecutionWrapperConfig.builder().step(node).uuid(uuid).build())
                .collect(Collectors.toList());
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(executionWrapperConfigs)
            .uuidToStrategyExpansionData(uuidToStrategyExpansionData)
            .build();
      }
      if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        Map<String, StrategyExpansionData> uuidToStrategyMetadata = new HashMap<>();
        ParallelStepElementConfig parallelStepElementConfig =
            YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : parallelStepElementConfig.getSections()) {
          ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
              expandExecutionWrapperConfig(config, maxExpansionLimit);
          executionWrapperConfigs.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
          uuidToStrategyMetadata.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
        }
        parallelStepElementConfig.setSections(executionWrapperConfigs);
        ArrayNode arrayNode = JsonPipelineUtils.getMapper().createArrayNode();
        for (ExecutionWrapperConfig config : executionWrapperConfigs) {
          arrayNode.add(JsonPipelineUtils.asTree(config));
        }
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(Lists.newArrayList(ExecutionWrapperConfig.builder().parallel(arrayNode).build()))
            .uuidToStrategyExpansionData(uuidToStrategyMetadata)
            .build();
      }
      if (executionWrapperConfig.getStepGroup() != null && !executionWrapperConfig.getStepGroup().isNull()) {
        Map<String, StrategyExpansionData> uuidToMaxConcurrency = new HashMap<>();
        StepGroupElementConfig stepGroupElementConfig =
            YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : stepGroupElementConfig.getSteps()) {
          ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
              expandExecutionWrapperConfig(config, maxExpansionLimit);
          executionWrapperConfigs.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
          uuidToMaxConcurrency.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
        }
        stepGroupElementConfig.setSteps(executionWrapperConfigs);
        JsonNode stepGroupNode = JsonPipelineUtils.asTree(stepGroupElementConfig);
        StrategyInfo strategyInfo = expandJsonNodes(stepGroupNode, maxExpansionLimit);
        List<JsonNode> expandedJsonNodes = strategyInfo.getExpandedJsonNodes();
        String uuid = EmptyPredicate.isEmpty(executionWrapperConfig.getUuid()) ? UUIDGenerator.generateUuid()
                                                                               : executionWrapperConfig.getUuid();
        uuidToMaxConcurrency.put(
            uuid, StrategyExpansionData.builder().maxConcurrency(strategyInfo.getMaxConcurrency()).build());
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(
                expandedJsonNodes.stream()
                    .map(node -> ExecutionWrapperConfig.builder().stepGroup(node).uuid(uuid).build())
                    .collect(Collectors.toList()))
            .uuidToStrategyExpansionData(uuidToMaxConcurrency)
            .build();
      }
      return ExpandedExecutionWrapperInfo.builder()
          .expandedExecutionConfigs(Lists.newArrayList(executionWrapperConfig))
          .uuidToStrategyExpansionData(new HashMap<>())
          .build();
    } catch (InvalidYamlException ex) {
      throw ex;
    } catch (JsonMappingException ex) {
      throw new InvalidYamlException(ex.getOriginalMessage());
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Unable to expand yaml for a execution element with strategy: %s", ex.getMessage()));
    }
  }

  public ExpandedExecutionWrapperInfo expandExecutionWrapperConfigFromClass(
      ExecutionWrapperConfig executionWrapperConfig, Optional<Integer> maxExpansionLimit, Class cls) {
    try {
      if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
        StrategyInfo strategyInfo =
            expandJsonNodesFromClass(executionWrapperConfig.getStep(), maxExpansionLimit, false, cls);
        List<JsonNode> expandedJsonNodes = strategyInfo.getExpandedJsonNodes();
        Map<String, StrategyExpansionData> uuidToStrategyExpansionData = new HashMap<>();
        String uuid = EmptyPredicate.isEmpty(executionWrapperConfig.getUuid()) ? UUIDGenerator.generateUuid()
                                                                               : executionWrapperConfig.getUuid();
        uuidToStrategyExpansionData.put(
            uuid, StrategyExpansionData.builder().maxConcurrency(strategyInfo.getMaxConcurrency()).build());
        List<ExecutionWrapperConfig> executionWrapperConfigs =
            expandedJsonNodes.stream()
                .map(node -> ExecutionWrapperConfig.builder().step(node).uuid(uuid).build())
                .collect(Collectors.toList());
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(executionWrapperConfigs)
            .uuidToStrategyExpansionData(uuidToStrategyExpansionData)
            .build();
      }
      if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
        Map<String, StrategyExpansionData> uuidToStrategyMetadata = new HashMap<>();
        ParallelStepElementConfig parallelStepElementConfig =
            YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : parallelStepElementConfig.getSections()) {
          ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
              expandExecutionWrapperConfigFromClass(config, maxExpansionLimit, cls);
          executionWrapperConfigs.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
          uuidToStrategyMetadata.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
        }
        parallelStepElementConfig.setSections(executionWrapperConfigs);
        ArrayNode arrayNode = JsonPipelineUtils.getMapper().createArrayNode();
        for (ExecutionWrapperConfig config : executionWrapperConfigs) {
          arrayNode.add(JsonPipelineUtils.asTree(config));
        }
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(Lists.newArrayList(ExecutionWrapperConfig.builder().parallel(arrayNode).build()))
            .uuidToStrategyExpansionData(uuidToStrategyMetadata)
            .build();
      }
      if (executionWrapperConfig.getStepGroup() != null && !executionWrapperConfig.getStepGroup().isNull()) {
        Map<String, StrategyExpansionData> uuidToMaxConcurrency = new HashMap<>();
        StepGroupElementConfig stepGroupElementConfig =
            YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
        List<ExecutionWrapperConfig> executionWrapperConfigs = new ArrayList<>();
        for (ExecutionWrapperConfig config : stepGroupElementConfig.getSteps()) {
          ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
              expandExecutionWrapperConfigFromClass(config, maxExpansionLimit, cls);
          executionWrapperConfigs.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
          uuidToMaxConcurrency.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
        }
        stepGroupElementConfig.setSteps(executionWrapperConfigs);
        JsonNode stepGroupNode = JsonPipelineUtils.asTree(stepGroupElementConfig);
        StrategyInfo strategyInfo = expandJsonNodesFromClass(stepGroupNode, maxExpansionLimit, true, cls);
        List<JsonNode> expandedJsonNodes = strategyInfo.getExpandedJsonNodes();
        String uuid = EmptyPredicate.isEmpty(executionWrapperConfig.getUuid()) ? UUIDGenerator.generateUuid()
                                                                               : executionWrapperConfig.getUuid();
        uuidToMaxConcurrency.put(
            uuid, StrategyExpansionData.builder().maxConcurrency(strategyInfo.getMaxConcurrency()).build());
        return ExpandedExecutionWrapperInfo.builder()
            .expandedExecutionConfigs(
                expandedJsonNodes.stream()
                    .map(node -> ExecutionWrapperConfig.builder().stepGroup(node).uuid(uuid).build())
                    .collect(Collectors.toList()))
            .uuidToStrategyExpansionData(uuidToMaxConcurrency)
            .build();
      }
      return ExpandedExecutionWrapperInfo.builder()
          .expandedExecutionConfigs(Lists.newArrayList(executionWrapperConfig))
          .uuidToStrategyExpansionData(new HashMap<>())
          .build();
    } catch (InvalidYamlException ex) {
      throw ex;
    } catch (JsonMappingException ex) {
      throw new InvalidYamlException(ex.getOriginalMessage());
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Unable to expand yaml for a execution element with strategy: %s", ex.getMessage()));
    }
  }
}
