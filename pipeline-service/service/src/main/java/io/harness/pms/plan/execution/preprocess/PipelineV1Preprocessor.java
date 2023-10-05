/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.preprocess;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.merger.helpers.FQNHelper;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineV1Preprocessor implements PipelinePreprocessor {
  protected PipelineV1Preprocessor() {}
  @Override
  public String preProcess(String pipelineYaml) {
    JsonNode pipelineNode = YamlUtils.readAsJsonNode(pipelineYaml);
    // Set to keep track of what all ids are present in the YAML.
    Set<String> idsValuesSet = new HashSet<>();
    collectIdsFromYamlRecursively(pipelineNode, idsValuesSet);

    // Map of id->Integer. Here the Integer denote that this index has been used as suffix for a given key. So next time
    // use the next index suffix.
    Map<String, Integer> idsSufixMap = new HashMap<>();
    // Adding ids wherever required.
    addGeneratedIdInJsonNodeRecursively(pipelineNode, idsSufixMap, idsValuesSet);

    return YamlPipelineUtils.writeYamlString(pipelineNode);
  }

  private void collectIdsFromYamlRecursively(JsonNode jsonNode, Set<String> idsValuesSet) {
    if (jsonNode == null) {
      return;
    }
    if (jsonNode.isArray()) {
      collectIdsFromArrayNode((ArrayNode) jsonNode, idsValuesSet);
    } else if (jsonNode.isObject()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> entryIterator = it.next();
        JsonNode childNode = entryIterator.getValue();
        collectIdsFromYamlRecursively(childNode, idsValuesSet);
      }
    }
  }

  private void collectIdsFromArrayNode(ArrayNode arrayNode, Set<String> idsValuesSet) {
    Set<String> idsInList = new HashSet<>();
    for (JsonNode arrayElement : arrayNode) {
      if (arrayElement.get(YAMLFieldNameConstants.ID) != null
          && arrayElement.get(YAMLFieldNameConstants.ID).isTextual()) {
        idsInList.add(arrayElement.get(YAMLFieldNameConstants.ID).asText());
      }
      idsValuesSet.addAll(idsInList);
      collectIdsFromYamlRecursively(arrayElement, idsValuesSet);
    }
  }

  private void addGeneratedIdInJsonNodeRecursively(
      JsonNode jsonNode, Map<String, Integer> idsSufixMap, Set<String> idsValuesSet) {
    if (jsonNode == null) {
      return;
    }
    if (jsonNode.isArray()) {
      addGeneratedIdInArrayNodeElements((ArrayNode) jsonNode, idsSufixMap, idsValuesSet);
    } else if (jsonNode.isObject()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> entryIterator = it.next();
        JsonNode childNode = entryIterator.getValue();
        addGeneratedIdInJsonNodeRecursively(childNode, idsSufixMap, idsValuesSet);
      }
    }
  }

  private void addGeneratedIdInArrayNodeElements(
      ArrayNode arrayNode, Map<String, Integer> idsSufixMap, Set<String> idsValuesSet) {
    for (JsonNode arrayElement : arrayNode) {
      if (arrayElement.get(YAMLFieldNameConstants.ID) == null) {
        String id = getIdFromNameOrType(arrayElement);
        if (id == null) {
          String wrapperKey = FQNHelper.getWrapperKeyForArrayElement(arrayElement);
          if (EmptyPredicate.isNotEmpty(wrapperKey)) {
            arrayElement = arrayElement.get(wrapperKey);
            id = getIdFromNameOrType(arrayElement);
          } else {
            continue;
          }
        }
        // get the last index that was used as suffix with this id previously.
        Integer previousSuffixValue = idsSufixMap.getOrDefault(id, 0);
        previousSuffixValue++;
        String idWithSuffix = id + "_" + previousSuffixValue;
        // Keep incrementing the suffix until the idWithSuffix is present in the idsValuesSet because if its present in
        // the set then user has used that id in the YAML so we can't use it.
        while (idsValuesSet.contains(idWithSuffix)) {
          previousSuffixValue++;
          idWithSuffix = id + "_" + previousSuffixValue;
        }
        idsSufixMap.put(id, previousSuffixValue);

        // Set the generated unique id in the arrayElement.
        ((ObjectNode) arrayElement).set(YAMLFieldNameConstants.ID, TextNode.valueOf(idWithSuffix));
      }
      addGeneratedIdInJsonNodeRecursively(arrayElement, idsSufixMap, idsValuesSet);
    }
  }

  // Get the id from the name or type if present. And convert the special characters(" ",".","-") into "_".
  private String getIdFromNameOrType(JsonNode jsonNode) {
    if (jsonNode == null) {
      return null;
    }
    String id = null;
    if (jsonNode.get(YAMLFieldNameConstants.NAME) != null && jsonNode.get(YAMLFieldNameConstants.NAME).isTextual()) {
      id = jsonNode.get(YAMLFieldNameConstants.NAME).asText();
    } else if (jsonNode.get(YAMLFieldNameConstants.TYPE) != null
        && jsonNode.get(YAMLFieldNameConstants.TYPE).isTextual()) {
      id = jsonNode.get(YAMLFieldNameConstants.TYPE).asText();
    }
    if (id == null) {
      return null;
    }
    return IdentifierGeneratorUtils.getId(id);
  }
}
