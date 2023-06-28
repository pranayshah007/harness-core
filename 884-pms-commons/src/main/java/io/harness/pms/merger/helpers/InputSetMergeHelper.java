/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetMergeHelper {
  // TODO(shalini): remove older methods with yaml string once all are moved to jsonNode
  public String mergeInputSetIntoPipeline(
      String pipelineYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    return MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(
        pipelineYaml, inputSetPipelineCompYaml, appendInputSetValidator);
  }

  public String mergeInputSetIntoPipelineForGivenStages(String pipelineYaml, String inputSetPipelineCompYaml,
      boolean appendInputSetValidator, List<String> stageIdentifiers) {
    return MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(
        pipelineYaml, removeNonRequiredStages(inputSetPipelineCompYaml, stageIdentifiers), appendInputSetValidator);
  }

  public JsonNode mergeInputSets(
      JsonNode template, List<JsonNode> inputSetJsonNodeList, boolean appendInputSetValidator) {
    return mergeInputSetsForGivenStages(template, inputSetJsonNodeList, appendInputSetValidator, null);
  }

  public JsonNode mergeInputSetsForGivenStages(JsonNode template, List<JsonNode> inputSetJsonNodeList,
      boolean appendInputSetValidator, List<String> stageIdentifiers) {
    List<JsonNode> inputSetPipelineCompJsonNodeList =
        getInputSetPipelineCompJsonNodeListWithJsonNode(inputSetJsonNodeList);
    JsonNode res = template;
    for (JsonNode jsonNode : inputSetPipelineCompJsonNodeList) {
      JsonNode jsonNodeWithRequiredStages = removeNonRequiredStages(jsonNode, stageIdentifiers);
      if (isEmpty(jsonNodeWithRequiredStages)) {
        continue;
      }
      res = MergeHelper.mergeRuntimeInputValuesIntoOriginalJsonNode(
          res, jsonNodeWithRequiredStages, appendInputSetValidator);
    }
    return res;
  }

  private List<JsonNode> getInputSetPipelineCompJsonNodeListWithJsonNode(List<JsonNode> inputSetJsonNodeList) {
    return inputSetJsonNodeList.stream()
        .map(jsonNode -> {
          try {
            return InputSetYamlHelper.getPipelineComponent(jsonNode);
          } catch (InvalidRequestException e) {
            return jsonNode;
          }
        })
        .collect(Collectors.toList());
  }

  public JsonNode mergeInputSetsV1(List<JsonNode> inputSetJsonNodeList) {
    if (EmptyPredicate.isEmpty(inputSetJsonNodeList)) {
      return null;
    }
    JsonNode mergedInputSetNode = getMergedInputSetNodeWithJsonNode(inputSetJsonNodeList);
    Map<String, Object> inputsMap = new HashMap<>();
    if (mergedInputSetNode.get(YAMLFieldNameConstants.INPUTS) != null) {
      inputsMap.put(YAMLFieldNameConstants.INPUTS, mergedInputSetNode.get(YAMLFieldNameConstants.INPUTS));
    }
    if (mergedInputSetNode.get(YAMLFieldNameConstants.OPTIONS) != null) {
      inputsMap.put(YAMLFieldNameConstants.OPTIONS, mergedInputSetNode.get(YAMLFieldNameConstants.OPTIONS));
    }
    return JsonUtils.asTree(inputsMap);
  }

  private JsonNode getMergedInputSetNodeWithJsonNode(List<JsonNode> inputSetJsonNodeList) {
    JsonNode mergedInputSetNode = null;
    for (JsonNode inputSetNode : inputSetJsonNodeList) {
      if (mergedInputSetNode == null) {
        mergedInputSetNode = inputSetNode;
      } else {
        JsonNodeUtils.merge(mergedInputSetNode, inputSetNode);
      }
    }
    return mergedInputSetNode;
  }

  public JsonNode removeNonRequiredStages(JsonNode inputSetPipelineCompJsonNode, List<String> stageIdentifiers) {
    Map<FQN, Object> inputSetFQNMap = FQNMapGenerator.generateFQNMap(inputSetPipelineCompJsonNode);
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      FQNHelper.removeNonRequiredStages(inputSetFQNMap, stageIdentifiers);
    }
    return YamlMapGenerator.generateYamlMap(inputSetFQNMap, inputSetPipelineCompJsonNode, false);
  }

  public String removeNonRequiredStages(String inputSetPipelineCompYaml, List<String> stageIdentifiers) {
    return YamlUtils.writeYamlString(
        removeNonRequiredStages(YamlUtils.readAsJsonNode(inputSetPipelineCompYaml), stageIdentifiers));
  }
}
