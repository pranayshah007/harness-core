/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.preprocess;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.merger.helpers.FQNHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
// Adds ids in all the stages and steps where it doesn't already exists
// id is calculated by name if present else type is used
// suffix is added to id to make sure two ids are never same within a scope
public class YamlV1PreProcessor implements YamlPreProcessor {
  public YamlV1PreProcessor() {}

  public YamlPreprocessorResponseDTO preProcess(JsonNode jsonNode) {
    // Set to keep track of what all ids are present in the YAML.
    Set<String> idsValuesSet = new HashSet<>();
    collectExistingIdsFromYamlRecursively(jsonNode, idsValuesSet);

    // Map of id->Integer. Here the Integer denote that this index has been used as suffix for a given key. So next time
    // use the next index suffix.
    Map<String, Integer> idsSufixMap = new HashMap<>();
    // Adding ids wherever required.
    addGeneratedIdInJsonNodeRecursively(jsonNode, idsSufixMap, idsValuesSet);

    return YamlPreprocessorResponseDTO.builder()
        .idsSuffixMap(idsSufixMap)
        .idsValuesSet(idsValuesSet)
        .preprocessedJsonNode(jsonNode)
        .build();
  }
  @Override
  public YamlPreprocessorResponseDTO preProcess(String yaml) {
    JsonNode jsonNode = YamlUtils.readAsJsonNode(yaml);
    return preProcess(jsonNode);
  }

  public void collectExistingIdsFromYamlRecursively(JsonNode jsonNode, Set<String> idsValuesSet) {
    if (jsonNode == null) {
      return;
    }
    if (jsonNode.isArray()) {
      collectIdsFromArrayNode((ArrayNode) jsonNode, idsValuesSet);
    } else if (jsonNode.isObject()) {
      for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> entryIterator = it.next();
        JsonNode childNode = entryIterator.getValue();
        collectExistingIdsFromYamlRecursively(childNode, idsValuesSet);
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
      collectExistingIdsFromYamlRecursively(arrayElement, idsValuesSet);
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
        String idWithSuffix = getMaxSuffixForAnId(idsValuesSet, idsSufixMap, id);
        // Set the generated unique id in the arrayElement.
        ((ObjectNode) arrayElement).set(YAMLFieldNameConstants.ID, TextNode.valueOf(idWithSuffix));
      }
      addGeneratedIdInJsonNodeRecursively(arrayElement, idsSufixMap, idsValuesSet);
    }
  }
}
