/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.strategy.HarnessForConfig;
import io.harness.plancreator.strategy.RepeatUnit;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.yaml.ParameterField;
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
    HarnessForConfig harnessForConfig = strategyConfig.getRepeat();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    if (!ParameterField.isBlank(harnessForConfig.getTimes())) {
      for (int i = 0; i < harnessForConfig.getTimes().getValue(); i++) {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(childNodeId)
                         .setStrategyMetadata(StrategyMetadata.newBuilder()
                                                  .setCurrentIteration(i)
                                                  .setTotalIterations(harnessForConfig.getTimes().getValue())
                                                  .build())
                         .build());
      }
    } else if (!ParameterField.isBlank(harnessForConfig.getPartitions())) {
      int currentIteration = 0;
      List<List<String>> partitions = partitionItems(harnessForConfig);

      for (List<String> partition : partitions) {
        children.add(
            ChildrenExecutableResponse.Child.newBuilder()
                .setChildNodeId(childNodeId)
                .setStrategyMetadata(StrategyMetadata.newBuilder()
                                         .setForMetadata(ForMetadata.newBuilder().addAllPartition(partition).build())
                                         .setCurrentIteration(currentIteration)
                                         .setTotalIterations(partition.size())
                                         .build())
                .build());
        currentIteration++;
      }
    } else {
      int currentIteration = 0;
      List<String> params = splitParamsIfNeeded(harnessForConfig);

      for (String value : params) {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(childNodeId)
                         .setStrategyMetadata(StrategyMetadata.newBuilder()
                                                  .setForMetadata(ForMetadata.newBuilder().setValue(value).build())
                                                  .setCurrentIteration(currentIteration)
                                                  .setTotalIterations(params.size())
                                                  .build())
                         .build());
        currentIteration++;
      }
    }

    return children;
  }

  private List<List<String>> partitionItems(HarnessForConfig harnessForConfig) {
    if (harnessForConfig.getUnit() == RepeatUnit.PERCENTAGE) {
      return partitionItemsByPercentage(harnessForConfig);
    } else {
      return partitionItemsByCount(harnessForConfig);
    }
  }

  private List<List<String>> partitionItemsByPercentage(HarnessForConfig harnessForConfig) {
    List<String> items = harnessForConfig.getItems().getValue();
    int itemsSize = items.size();
    ParameterField<Integer> partitionSizeInPercentage = harnessForConfig.getPartitions();
    float partitionSizeInPercentageValue = partitionSizeInPercentage.getValue().floatValue();

    int partitionSize = Math.round((partitionSizeInPercentageValue / 100) * itemsSize);
    // fix partitionSize if provided percentage value is greater than 0
    if (partitionSize == 0 && partitionSizeInPercentageValue > 0) {
      partitionSize = 1;
    }

    // fix partitionSize if partitionSize is greater than number of items
    if (partitionSize > itemsSize) {
      partitionSize = itemsSize;
    }
    validatePartition(partitionSize, itemsSize);
    return com.google.common.collect.Lists.partition(items, partitionSize);
  }

  private List<List<String>> partitionItemsByCount(HarnessForConfig harnessForConfig) {
    List<String> items = harnessForConfig.getItems().getValue();
    int itemsSize = items.size();
    int partitionSize = harnessForConfig.getPartitions().getValue();

    // fix partitionSize if partitionSize is greater than number of items
    if (partitionSize > itemsSize) {
      partitionSize = itemsSize;
    }
    validatePartition(partitionSize, itemsSize);
    return com.google.common.collect.Lists.partition(items, partitionSize);
  }

  private List<String> splitParamsIfNeeded(HarnessForConfig harnessForConfig) {
    if (harnessForConfig.getUnit() == RepeatUnit.PERCENTAGE) {
      return handleSplitByPercentage(harnessForConfig);
    }
    return handleSplitByCount(harnessForConfig);
  }

  private List<String> handleSplitByPercentage(HarnessForConfig harnessForConfig) {
    int start = 0;
    List<String> params = harnessForConfig.getItems().getValue();
    int end = params.size() - 1;
    if (!ParameterField.isBlank(harnessForConfig.getStart())) {
      start = (int) (((harnessForConfig.getStart().getValue().floatValue()) / 100) * params.size());
    }
    if (!ParameterField.isBlank(harnessForConfig.getEnd())) {
      end = (int) (((harnessForConfig.getEnd().getValue().floatValue()) / 100) * params.size());
    }
    validateStartEnd(start, end, params.size());
    return params.subList(start, end);
  }

  private List<String> handleSplitByCount(HarnessForConfig harnessForConfig) {
    List<String> params = harnessForConfig.getItems().getValue();
    if (!ParameterField.isBlank(harnessForConfig.getStart())) {
      int start = harnessForConfig.getStart().getValue();
      if (!ParameterField.isBlank(harnessForConfig.getEnd())) {
        int end = harnessForConfig.getEnd().getValue();
        validateStartEnd(start, end, params.size());
        params = params.subList(start, end);
      } else {
        validateStartEnd(start, params.size() - 1, params.size());
        params = params.subList(start, params.size() - 1);
      }
    } else if (!ParameterField.isBlank(harnessForConfig.getEnd())) {
      int end = harnessForConfig.getEnd().getValue();
      validateStartEnd(0, end, params.size());
      params = params.subList(0, end);
    }
    return params;
  }

  private void validateStartEnd(int start, int end, int size) {
    if (start < 0 || end < 0) {
      throw new InvalidRequestException("start or end cannot be less that 0");
    }
    if (start >= size || end > size) {
      throw new InvalidRequestException("start or end cannot be greater than the size of list");
    }
    if (start > end) {
      throw new InvalidRequestException("start cannot be greater than end");
    }
  }

  private void validatePartition(int partitionSize, int itemsSize) {
    if (itemsSize == 0) {
      throw new InvalidArgumentsException("items list cannot be empty");
    }
    if (partitionSize <= 0) {
      throw new InvalidArgumentsException("partition size cannot be equal or less that 0");
    }
  }

  @Override
  public StrategyInfo expandJsonNode(StrategyConfig strategyConfig, JsonNode jsonNode) {
    HarnessForConfig harnessForConfig = strategyConfig.getRepeat();
    List<JsonNode> jsonNodes = new ArrayList<>();
    if (!ParameterField.isBlank(harnessForConfig.getTimes())) {
      for (int i = 0; i < harnessForConfig.getTimes().getValue(); i++) {
        JsonNode clonedNode = JsonPipelineUtils.asTree(JsonUtils.asMap(StrategyUtils.replaceExpressions(
            jsonNode.deepCopy().toString(), new HashMap<>(), i, harnessForConfig.getTimes().getValue(), null)));
        StrategyUtils.modifyJsonNode(clonedNode, Lists.newArrayList(String.valueOf(i)));
        jsonNodes.add(clonedNode);
      }
    } else {
      int currentIteration = 0;
      List<String> params = splitParamsIfNeeded(harnessForConfig);
      for (String value : params) {
        JsonNode clonedNode = JsonPipelineUtils.asTree(JsonUtils.asMap(StrategyUtils.replaceExpressions(
            jsonNode.deepCopy().toString(), new HashMap<>(), currentIteration, params.size(), value)));
        StrategyUtils.modifyJsonNode(clonedNode, Lists.newArrayList(String.valueOf(currentIteration)));
        jsonNodes.add(clonedNode);
        currentIteration++;
      }
    }
    int maxConcurrency = jsonNodes.size();
    if (!ParameterField.isBlank(harnessForConfig.getMaxConcurrency())) {
      maxConcurrency = harnessForConfig.getMaxConcurrency().getValue();
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(maxConcurrency).build();
  }
}
